package dev.gawdl3y.android.heartsock.net

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class DiscoveryManager(private val nsdManager: NsdManager) {
	/**
	 * Finds the first usable server that is discovered and resolved successfully
	 */
	suspend fun findServer(type: String = SERVICE_TYPE, name: String = INSTANCE_NAME): DiscoveredServer? {
		return discover(type, name).map {
			try {
				if (it.serviceName == name) {
					val service = resolve(it)
					if (service != null) DiscoveredServer(service.host, service.port) else null
				} else {
					null
				}
			} catch (err: Exception) {
				null
			}
		}.first {
			it != null
		}
	}

	/**
	 * Provides a flow of discovered services
	 */
	fun discover(type: String = SERVICE_TYPE, name: String = INSTANCE_NAME) = callbackFlow {
		// Create a discovery listener
		val listener = object : NsdManager.DiscoveryListener {
			private val logPrefix = "Discovery ($type, $name):"

			override fun onDiscoveryStarted(serviceType: String?) {
				Log.d(TAG, "$logPrefix Service discovery started")
			}

			override fun onDiscoveryStopped(serviceType: String?) {
				Log.d(TAG, "$logPrefix Service discovery stopped")
			}

			override fun onServiceFound(service: NsdServiceInfo?) {
				Log.i(TAG, "$logPrefix Service discovered: $service")
				if (service == null) return
				trySendBlocking(service)
			}

			override fun onServiceLost(service: NsdServiceInfo?) {
				Log.d(TAG, "$logPrefix Service lost: $service")
			}

			override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
				Log.e(TAG, "$logPrefix Discovery start failed, error code $errorCode")
			}

			override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
				Log.e(TAG, "$logPrefix Discovery stop failed, error code $errorCode")
			}
		}

		// Start the discovery
		Log.d(TAG, "Registering for service discovery: $type")
		nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)

		// Shut down when the flow is done
		awaitClose {
			Log.d(TAG, "Unregistering for service discovery: $type")
			runBlocking {
				nsdManager.stopServiceDiscovery(listener)
			}
		}
	}

	/**
	 * Attempts to resolve a service
	 */
	suspend fun resolve(service: NsdServiceInfo): NsdServiceInfo? {
		return suspendCoroutine { continuation ->
			val listener = object : NsdManager.ResolveListener {
				override fun onServiceResolved(service: NsdServiceInfo) {
					Log.i(TAG, "Service resolved: $service")
					continuation.resume(service)
				}

				override fun onResolveFailed(service: NsdServiceInfo, errorCode: Int) {
					Log.e(TAG, "Resolution failed: $errorCode")
					continuation.resume(null)
				}
			}

			Log.d(TAG, "Resolving service: $service")
			nsdManager.resolveService(service, listener)
		}
	}

	data class DiscoveredServer(val host: InetAddress, val port: Int)

	companion object {
		private const val TAG = "DiscoveryManager"
		private const val SERVICE_TYPE = "_heartsock._tcp."
		private const val INSTANCE_NAME = "❤️\uD83E\uDDE6"
	}
}