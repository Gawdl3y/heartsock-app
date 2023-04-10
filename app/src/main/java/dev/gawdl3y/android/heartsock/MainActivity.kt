package dev.gawdl3y.android.heartsock

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.wear.ambient.AmbientModeSupport
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.curvedText
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.google.android.horologist.compose.navscaffold.WearNavScaffold
import com.google.android.horologist.compose.snackbar.SnackbarData
import dev.gawdl3y.android.heartsock.data.ServiceStatus
import dev.gawdl3y.android.heartsock.data.SettingsRepository
import dev.gawdl3y.android.heartsock.net.DiscoveryManager
import dev.gawdl3y.android.heartsock.ui.mainScreen
import dev.gawdl3y.android.heartsock.ui.navigateToSettings
import dev.gawdl3y.android.heartsock.ui.settingsScreen
import dev.gawdl3y.android.heartsock.ui.theme.HeartsockTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.EOFException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {
	private lateinit var settingsRepository: SettingsRepository
	private lateinit var permissionLauncher: ActivityResultLauncher<String>
	private var websocketService: HeartsockService? = null
	private val ambientCallbackState = AmbientCallbackState()

	private val websocketServiceConnection = object : ServiceConnection {
		override fun onServiceConnected(name: ComponentName, service: IBinder) {
			val binder = service as HeartsockService.LocalBinder
			websocketService = binder.websocketService
		}

		override fun onServiceDisconnected(name: ComponentName) {
			websocketService = null
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Log.d(TAG, "onCreate()")

		AmbientModeSupport.attach(this)

		permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
			when (it) {
				true -> Log.i(TAG, "Body sensors permission granted")
				false -> Log.i(TAG, "Body sensors permission not granted")
			}
		}

		settingsRepository = (application as HeartsockApplication).settingsRepository
		lifecycleScope.launch {
			if (settingsRepository.keepScreenOn.first()) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		}

		setContent { AppContent() }
	}

	override fun onStart() {
		super.onStart()
		Log.d(TAG, "onStart()")

		val serviceIntent = Intent(this, HeartsockService::class.java)
		bindService(serviceIntent, websocketServiceConnection, Context.BIND_AUTO_CREATE)

		permissionLauncher.launch(android.Manifest.permission.BODY_SENSORS)
	}

	override fun onStop() {
		Log.d(TAG, "onStop()")
		if (websocketService != null) unbindService(websocketServiceConnection)
		super.onStop()
	}

	override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback = ambientCallbackState

	private fun showException(exception: Exception) {
		val (text, duration) = when (exception) {
			is SocketTimeoutException -> Pair(getString(R.string.error_connection_timeout), Toast.LENGTH_SHORT)
			is UnknownHostException -> Pair(getString(R.string.error_connection_dns), Toast.LENGTH_SHORT)
			is ConnectException -> Pair(getString(R.string.error_connection_refused), Toast.LENGTH_SHORT)
			is SocketException ->
				if (exception.message == "Socket closed") {
					Pair("", 0)
				} else {
					Pair(getString(R.string.error_connection_generic, exception.toString()), Toast.LENGTH_LONG)
				}
			is CancellationException -> Pair("", 0)
			is IllegalStateException -> when (exception.message) {
				"No server to connect to" -> Pair(getString(R.string.scan_failure), Toast.LENGTH_SHORT)
				"Unable to get WiFi connection" -> Pair(getString(R.string.scan_failure_wifi), Toast.LENGTH_SHORT)
				else -> Pair(getString(R.string.error_connection_generic, exception.toString()), Toast.LENGTH_LONG)
			}
			is EOFException -> Pair("", 0)
			else -> Pair(getString(R.string.error_connection_generic, exception.toString()), Toast.LENGTH_LONG)
		}
		if (text.isNotEmpty()) Toast.makeText(this, text, duration).show()
	}

	@Composable
	private fun AppContent() {
		HeartsockTheme {
			val navController = rememberSwipeDismissableNavController()
			val coroutineScope = rememberCoroutineScope()
			val status =
				(application as HeartsockApplication).statusRepository.status.collectAsState(initial = ServiceStatus.DISCONNECTED)

			WearNavScaffold(
				startDestination = "main",
				navController = navController,
				timeText = {
					WsStatusTimeText(status, it)
				}
			) {
				mainScreen(
					onActionConnect = {
						coroutineScope.launch {
							try {
								websocketService?.connect()
							} catch (ex: Exception) {
								showException(ex)
							}
						}
					},
					onActionDisconnect = {
						coroutineScope.launch {
							websocketService?.disconnect()
						}
					},
					onActionSettings = { navController.navigateToSettings() }
				)

				settingsScreen(
					onActionScan = { cancel ->
						coroutineScope.launch {
							if (cancel) return@launch websocketService?.disconnect() ?: Unit

							val server = websocketService?.scan(updateSettings = true)
							Toast.makeText(
								this@MainActivity,
								when (server) {
									is HeartsockService.ScanResult.Server -> R.string.scan_success
									is HeartsockService.ScanResult.NoWifi -> R.string.scan_failure_wifi
									else -> R.string.scan_failure
								},
								Toast.LENGTH_SHORT
							).show()
						}
					},
					onToggleKeepScreenOn = { keepScreenOn ->
						if (keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
						else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
					},
					onToggleAmbientMode = { ambientMode ->
						// TODO: implement ambient mode
					}
				)
			}
		}
	}

	@Composable
	private fun WsStatusTimeText(status: State<ServiceStatus>, modifier: Modifier = Modifier) {
		val statusText = stringResource(status.value.stringResource)
		TimeText(
			modifier = modifier,
			endLinearContent = { Text(statusText) },
			endCurvedContent = { curvedText(statusText) }
		)
	}

	companion object {
		private const val TAG = "MainActivity"
	}
}

private class AmbientCallbackState : AmbientModeSupport.AmbientCallback() {
	var ambientState by mutableStateOf<AmbientState>(AmbientState.Interactive)

	override fun onEnterAmbient(ambientDetails: Bundle) {
		super.onEnterAmbient(ambientDetails)
		val isLowBitAmbient = ambientDetails.getBoolean(
			AmbientModeSupport.EXTRA_LOWBIT_AMBIENT,
			false
		)
		val doBurnInProtection = ambientDetails.getBoolean(
			AmbientModeSupport.EXTRA_BURN_IN_PROTECTION,
			false
		)

		ambientState = AmbientState.Ambient(
			isLowBitAmbient = isLowBitAmbient,
			doBurnInProtection = doBurnInProtection
		)
	}
}

sealed interface AmbientState {
	object Interactive : AmbientState

	data class Ambient(
		val isLowBitAmbient: Boolean,
		val doBurnInProtection: Boolean
	) : AmbientState
}