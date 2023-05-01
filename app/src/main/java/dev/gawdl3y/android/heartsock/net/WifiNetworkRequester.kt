package dev.gawdl3y.android.heartsock.net

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiNetworkRequester(private val connectivityManager: ConnectivityManager) :
	ConnectivityManager.NetworkCallback() {
	private var _network: MutableStateFlow<Network?>? = null

	/**
	 * Requests a WiFi network
	 */
	fun request(): StateFlow<Network?> {
		if (_network != null) throw IllegalStateException("Attempting to request on already-running requester")
		Log.d(TAG, "Requesting WiFi connectivity")

		_network = MutableStateFlow(null)
		connectivityManager.requestNetwork(
			NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
			this
		)

		return _network?.asStateFlow() ?: MutableStateFlow(null).asStateFlow()
	}

	/**
	 * Releases the request
	 */
	fun release() {
		if (_network == null) return
		Log.d(TAG, "Stopping WiFi request")

		connectivityManager.unregisterNetworkCallback(this)
		connectivityManager.bindProcessToNetwork(null)

		_network?.value = null
		_network = null
	}

	/**
	 * Checks whether the device is currently connected to WiFi
	 */
	fun isDeviceOnWifi(): Boolean {
		val network = connectivityManager.activeNetwork ?: return false
		val capabilities = connectivityManager.getNetworkCapabilities(network)
		return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
	}

	override fun onAvailable(network: Network) {
		super.onAvailable(network)
		Log.d(TAG, "WiFi network available: $network")
		connectivityManager.bindProcessToNetwork(network)
		_network?.value = network
	}

	override fun onLost(network: Network) {
		super.onLost(network)
		Log.d(TAG, "WiFi network lost: $network")
		_network?.value = null
	}

	companion object {
		private const val TAG = "WifiNetworkRequester"
	}
}