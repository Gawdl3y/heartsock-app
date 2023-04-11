package dev.gawdl3y.android.heartsock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.os.BatteryManager
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.health.services.client.HealthServices
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import dev.gawdl3y.android.heartsock.data.ServiceStatus
import dev.gawdl3y.android.heartsock.data.SettingsRepository
import dev.gawdl3y.android.heartsock.data.StatusRepository
import dev.gawdl3y.android.heartsock.net.DiscoveryManager
import dev.gawdl3y.android.heartsock.net.WebSocketClient
import dev.gawdl3y.android.heartsock.net.WifiNetworkRequester
import dev.gawdl3y.android.heartsock.ui.theme.wearColorPalette
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient

class HeartsockService : LifecycleService() {
	private lateinit var notificationManager: NotificationManager
	private lateinit var batteryManager: BatteryManager

//	private lateinit var powerManager: PowerManager
//	private lateinit var alarmManager: AlarmManager
	private lateinit var healthManager: HealthServicesManager
	private lateinit var discoveryManager: DiscoveryManager
//	private lateinit var alarmIntent: PendingIntent

	private val settingsRepository: SettingsRepository by lazy { (application as HeartsockApplication).settingsRepository }
	private val statusRepository: StatusRepository by lazy { (application as HeartsockApplication).statusRepository }
	private val okHttpClient: OkHttpClient by lazy { OkHttpClient() }
	private val websocketClient: WebSocketClient by lazy { WebSocketClient(okHttpClient) }

	private val localBinder = LocalBinder()
	private var configurationChange = false
	private var runningInForeground = false
	private var websocketActive = false

//	private var wakeLock: PowerManager.WakeLock? = null
	private var monitorJob: Job? = null
	private var scanJob: Deferred<ScanResult>? = null
	private var preScanStatus: ServiceStatus? = null

	/**
	 * Connects to the WebSocket and begins monitoring the WebSocketClient's state and heart rate data.
	 * This officially starts the service.
	 */
	suspend fun connect() {
		Log.d(TAG, "connect()")

		// Get the address and port to use
		var address = settingsRepository.serverAddress.first()
		var port = settingsRepository.serverPort.first()

		// Perform a scan if we don't have a server address
		if (address.isBlank()) {
			Log.i(TAG, "No server address specified, scanning for one to use...")
			val server = when (val result = scan(fromConnect = true)) {
				is ScanResult.Server -> result.server
				is ScanResult.NoServer -> throw IllegalStateException("No server to connect to")
				is ScanResult.NoWifi -> throw IllegalStateException("Unable to get WiFi connection")
			}
			address = server.host.hostAddress ?: ""
			port = server.port
		}

		// Start connection
		startSelf()
		websocketActive = true
		updateStatus(ServiceStatus.CONNECTING)
		websocketClient.connect(address, port)

		// Wait for the websocket to enter a final state
		val state = websocketClient.state.first {
			it == WebSocketClient.State.VERIFIED || it == WebSocketClient.State.INACTIVE
		}

		// Bail if the connection failed
		if (state == WebSocketClient.State.INACTIVE) {
			updateStatus(ServiceStatus.DISCONNECTED)
			websocketActive = false
			throw websocketClient.failureCause ?: IllegalStateException("Failed to connect with no failure cause")
		}

		// Set up the monitoring job
		monitorJob?.cancel()
		monitorJob = lifecycleScope.launch {
			launch { monitorWebSocketState() }
			launch { monitorHeartRate() }
			launch { monitorBattery() }
		}
		monitorJob?.invokeOnCompletion {
			monitorJob = null
			stopSelfIfNothingRunning()
		}
	}

	/**
	 * Disconnects from the WebSocket and stop monitoring heart rate
	 */
	suspend fun disconnect() {
		Log.d(TAG, "disconnect()")

		// If we're scanning, cancel that
		if (scanJob != null) {
			scanJob?.cancel()
			return
		}

		// Cancel the WebSocket if it's connecting to interrupt it immediately; otherwise, just gracefully close
		when (websocketClient.state.first()) {
			WebSocketClient.State.CONNECTING -> websocketClient.cancel()
			else -> websocketClient.close(reason = "User initiated disconnect")
		}

//		wakeLock?.release()
	}

	/**
	 * Scans for a valid WebSocket server to use
	 */
	suspend fun scan(updateSettings: Boolean = false, fromConnect: Boolean = false): ScanResult {
		Log.d(TAG, "scan(updateSettings = $updateSettings)")
		startSelf()

		// Find out whether we'll need to wait for WiFi
		val wifiRequester = WifiNetworkRequester(getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager)
		val alreadyOnWifi = wifiRequester.isDeviceOnWifi()

		// Store previous state and update the status
		preScanStatus = statusRepository.status.value
		updateStatus(if (alreadyOnWifi) ServiceStatus.SCANNING else ServiceStatus.AWAITING_WIFI, true)

		try {
			scanJob = lifecycleScope.async {
				// Wait for a WiFi connection
				if (!alreadyOnWifi) {
					Log.i(TAG, "Waiting for WiFi connection...")

					val wifiFlow = wifiRequester.request()
					val network = withTimeoutOrNull(60000L) { wifiFlow.filterNotNull().firstOrNull() }
					if (network == null) {
						Log.i(TAG, "Scan couldn't get a WiFi connection after 60s")
						return@async ScanResult.NoWifi
					}

					updateStatus(ServiceStatus.SCANNING, true)
				}

				// Tick the progress indicator
				val start = System.currentTimeMillis()
				val progressJob = launch {
					while (true) {
						delay(1000L)
						statusRepository.setProgress(((System.currentTimeMillis() - start).toDouble() / 5000F).toFloat())
					}
				}
				progressJob.invokeOnCompletion { statusRepository.setProgress(0F) }

				// Look for the server
				val server = try {
					withTimeoutOrNull(5000L) { discoveryManager.findServer() }
				} finally {
					progressJob.cancel()
				}

				// Make sure we discovered one
				if (server == null) {
					Log.i(TAG, "Scan found no servers")
					return@async ScanResult.NoServer
				}
				Log.i(TAG, "Scan found server: $server")

				// Update the settings to match the discovered server
				if (updateSettings) {
					settingsRepository.setServerAddress(server.host.hostAddress ?: "")
					settingsRepository.setServerPort(server.port.toUShort())
				}

				ScanResult.Server(server)
			}

			return scanJob?.await() ?: ScanResult.NoServer
		} finally {
			scanJob = null
			preScanStatus?.let { updateStatus(it, true) }
			preScanStatus = null
			wifiRequester.release()
			if (!fromConnect) stopSelfIfNothingRunning()
		}
	}

	/**
	 * Updates the running notification (if applicable) and the ServiceStatus in the repository
	 */
	private fun updateStatus(status: ServiceStatus, fromScan: Boolean = false) {
		if (!fromScan && scanJob != null) {
			Log.d(TAG, "Setting pre-scan ServiceStatus: $status")
			preScanStatus = status
		} else {
			Log.d(TAG, "Setting ServiceStatus: $status")
			statusRepository.setStatus(status)
		}

		if (runningInForeground) {
			notificationManager.notify(NOTIFICATION_ID, buildNotification(status))
		}
	}

	/**
	 * Monitors the WebSocketClient's state and updates the WebSocketStatus appropriately.
	 * When the WebSocketClient becomes inactive, also clears any active monitoring job and stops the service.
	 */
	private suspend fun monitorWebSocketState() {
		websocketClient.state.collect {
			Log.d(TAG, "WebSocketClient state changed: $it")

			when (it) {
				WebSocketClient.State.VERIFIED -> updateStatus(ServiceStatus.CONNECTED)
				WebSocketClient.State.INACTIVE -> {
					Log.i(TAG, "WebSocketClient returned to inactive, tearing down...")
					tearDown()
					return@collect
				}
				WebSocketClient.State.CONNECTING -> updateStatus(
					if (websocketClient.isReconnecting()) ServiceStatus.RECONNECTING else ServiceStatus.CONNECTING
				)
				WebSocketClient.State.CLOSED -> updateStatus(
					if (websocketClient.isReconnecting()) ServiceStatus.RECONNECTING else ServiceStatus.DISCONNECTED
				)
				else -> {}
			}
		}
	}

	/**
	 * Collects heart rate data and sends it to the WebSocket if it is available
	 */
	private suspend fun monitorHeartRate() {
		healthManager.heartRateMeasureFlow().collect {
			when (it) {
				is MeasureMessage.MeasureAvailability -> {
					Log.d(TAG, "Heart rate availability changed: ${it.availability}")
				}
				is MeasureMessage.MeasureData -> {
					val bpm = it.data.last().value
					Log.d(TAG, "Heart rate update: $bpm")
					statusRepository.setBpm(bpm)
					websocketClient.sendBpm(bpm)
				}
			}
		}
	}

	/**
	 * Gets the current battery percentage and sends it to the WebSocket every minute
	 */
	private suspend fun monitorBattery() {
		while (true) {
			val percent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
			websocketClient.sendBattery(percent)
			delay(60000)
		}
	}

//	/**
//	 * Sets an exact alarm with the alarm manager to wake the CPU for a lil bit
//	 */
//	private fun setWakeAlarm() {
//		Log.d(TAG, "Scheduling wake alarm")
//		alarmManager.setExactAndAllowWhileIdle(
//			AlarmManager.ELAPSED_REALTIME_WAKEUP,
//			SystemClock.elapsedRealtime() + 5000,
//			alarmIntent
//		)
//	}

	/**
	 * Starts the service
	 */
	private fun startSelf() {
		Log.d(TAG, "Starting")
		startService(Intent(applicationContext, HeartsockService::class.java))
	}

	/**
	 * Stops the service if there isn't an active monitor or scan job and the websocket isn't connected
	 */
	private fun stopSelfIfNothingRunning() {
		if (websocketActive || monitorJob != null || scanJob != null) {
			Log.d(TAG, "Not stopping as there is an active monitor or scan job, or the websocket is active")
			return
		}

		Log.d(TAG, "Stopping")
		stopSelf()
	}

	/**
	 * Puts the service in the foreground
	 */
	private fun enterForeground() {
		Log.d(TAG, "Entering foreground")
		startForeground(NOTIFICATION_ID, buildNotification(statusRepository.status.value))
		runningInForeground = true
//		setWakeAlarm()
	}

	/**
	 * Removes the service from the foreground
	 */
	private fun leaveForeground() {
		Log.d(TAG, "Leaving foreground")
		stopForeground(STOP_FOREGROUND_REMOVE)
//		wakeLock?.release()
		runningInForeground = false
		configurationChange = false
	}

	/**
	 * Stops all the things
	 */
	private fun tearDown() {
		statusRepository.setStatus(ServiceStatus.DISCONNECTED)
//		wakeLock?.release()
//		alarmManager.cancel(alarmIntent)
		websocketActive = false
		monitorJob?.cancel()
		scanJob?.cancel()
		stopSelf()
	}

	/**
	 * Builds a notification and ongoing activity
	 */
	private fun buildNotification(status: ServiceStatus): Notification {
		Log.d(TAG, "buildNotification(status = $status)")
		val titleText = getString(R.string.app_name)
		val statusText = getString(status.stringResource)

		// Set up pending intents
		val launchIntent = Intent(this, MainActivity::class.java)
			.let { intent ->
				PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
			}
		val disconnectIntent = Intent(this, HeartsockService::class.java)
			.putExtra(EXTRA_DISCONNECT_FROM_NOTIFICATION, true)
			.let { intent ->
				PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
			}

		// Build the notification
		val notificationBuilder = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
			.setStyle(
				NotificationCompat.BigTextStyle()
					.bigText(statusText)
					.setBigContentTitle(titleText)
			)
			.setContentTitle(titleText)
			.setContentText(statusText)
			.setSmallIcon(R.drawable.ic_notification_mask)
			.setColor(wearColorPalette.primary.toArgb())
			.setDefaults(NotificationCompat.DEFAULT_ALL)
			.setOngoing(true)
			.setCategory(NotificationCompat.CATEGORY_LOCATION_SHARING)
			.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
			.addAction(
				R.drawable.ic_baseline_launch_24,
				getString(R.string.action_launch),
				launchIntent
			)

		// Add the disconnect/stop action
		when (status) {
			ServiceStatus.SCANNING, ServiceStatus.AWAITING_WIFI -> {
				notificationBuilder.addAction(
					R.drawable.ic_outline_cancel_24,
					getString(R.string.action_cancel_scan),
					disconnectIntent
				)
			}
			else -> {
				notificationBuilder.addAction(
					R.drawable.ic_baseline_link_off_24,
					getString(R.string.action_disconnect),
					disconnectIntent
				)
			}
		}

		// Build the ongoing activity
		val ongoingActivity = OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, notificationBuilder)
			.setStaticIcon(R.drawable.ic_notification_mask)
			.setTouchIntent(launchIntent)
			.setStatus(
				Status.Builder()
					.addTemplate(statusText)
					.build()
			)
			.build()

		ongoingActivity.apply(applicationContext)
		return notificationBuilder.build()
	}

	override fun onCreate() {
		super.onCreate()
		Log.d(TAG, "onCreate()")

		// Get system services
		notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
//		powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
//		alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
		healthManager = HealthServicesManager(HealthServices.getClient(application))
		discoveryManager = DiscoveryManager(getSystemService(Context.NSD_SERVICE) as NsdManager)

//		alarmIntent = Intent(this, WebSocketService::class.java)
//			.putExtra(EXTRA_WAKE_ALARM, true)
//			.let { intent ->
//				PendingIntent.getService(this, 1, intent, PendingIntent.FLAG_IMMUTABLE)
//			}

		// Set up notification channel
		notificationManager.createNotificationChannel(
			NotificationChannel(
				NOTIFICATION_CHANNEL_ID, getString(R.string.channel_active_name), NotificationManager.IMPORTANCE_DEFAULT
			)
		)
	}

	override fun onDestroy() {
		super.onDestroy()
		Log.d(TAG, "onDestroy()")
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		super.onStartCommand(intent, flags, startId)
		Log.d(TAG, "onStartCommand()")

//		// Schedule another wake alarm if that's what triggered us
//		if (intent?.getBooleanExtra(EXTRA_WAKE_ALARM, false) == true) {
//			Log.d(TAG, "Received wake alarm")
//			if (runningInForeground) setWakeAlarm()
//		}

		// Shut everything down when the notification's cancel button is pressed
		if (intent?.getBooleanExtra(EXTRA_DISCONNECT_FROM_NOTIFICATION, false) == true) {
			Log.d(TAG, "Received disconnect from notification")
			lifecycleScope.launch {
				disconnect()
			}
		}

		return START_NOT_STICKY
	}

	override fun onBind(intent: Intent): IBinder {
		super.onBind(intent)
		Log.d(TAG, "onBind()")
		leaveForeground()
		return localBinder
	}

	override fun onRebind(intent: Intent?) {
		super.onRebind(intent)
		Log.d(TAG, "onRebind()")
		leaveForeground()
	}

	override fun onUnbind(intent: Intent?): Boolean {
		Log.d(TAG, "onUnbind() configurationChange = $configurationChange")
		if (!configurationChange && (websocketActive || scanJob != null)) {
			enterForeground()
		}
		return true
	}

	override fun onConfigurationChanged(newConfig: Configuration) {
		super.onConfigurationChanged(newConfig)
		Log.d(TAG, "onConfigurationChanged()")
		configurationChange = true
	}

	sealed class ScanResult {
		object NoServer : ScanResult()
		object NoWifi : ScanResult()
		data class Server(val server: DiscoveryManager.DiscoveredServer) : ScanResult()
	}

	inner class LocalBinder : Binder() {
		internal val websocketService: HeartsockService
			get() = this@HeartsockService
	}

	companion object {
		private const val TAG = "HeartsockService"
		private const val PACKAGE_NAME = "dev.gawdl3y.android.heartsock"
		private const val EXTRA_DISCONNECT_FROM_NOTIFICATION = "$PACKAGE_NAME.extra.DISCONNECT_FROM_NOTIFICATION"
//		private const val EXTRA_WAKE_ALARM = "$PACKAGE_NAME.extra.WAKE_ALARM"
		private const val NOTIFICATION_ID = 1337
		private const val NOTIFICATION_CHANNEL_ID = "websocket_active"
	}
}