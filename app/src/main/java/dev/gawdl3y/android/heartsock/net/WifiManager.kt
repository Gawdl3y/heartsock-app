package dev.gawdl3y.android.heartsock.net

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow

class WifiManager(private val connectivityManager: ConnectivityManager) {
	fun requestWifi() = callbackFlow {
		val listener = object : ConnectivityManager.NetworkCallback() {
			override fun onAvailable(network: Network) {
				super.onAvailable(network)
				Log.d(TAG, "Network available: $network")
				connectivityManager.bindProcessToNetwork(network)
				trySendBlocking(network)
			}

			override fun onLost(network: Network) {
				super.onLost(network)
				Log.d(TAG, "Network lost: $network")
				trySendBlocking(null)
			}
		}

		Log.d(TAG, "Requesting WiFi connectivity - registering network listener")
		connectivityManager.requestNetwork(
			NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
			listener
		)

		awaitClose {
			Log.d(TAG, "Unregistering network listener")
			connectivityManager.unregisterNetworkCallback(listener)
		}
	}

	companion object {
		private const val TAG = "WifiManager"
	}
}