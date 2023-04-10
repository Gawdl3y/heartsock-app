package dev.gawdl3y.android.heartsock.net

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import java.io.EOFException
import kotlin.math.roundToInt

class WebSocketClient(private var okHttpClient: OkHttpClient) : WebSocketListener() {
	private val coroutineScope = CoroutineScope(Dispatchers.IO)
	private var webSocket: WebSocket? = null
	private var reconnectJob: Job? = null
	private var lastAddress: String? = null
	private var lastPort: Int? = null

	private val _messages = MutableSharedFlow<String>()
	val messages = _messages.asSharedFlow()

	private val _state = MutableStateFlow(State.INACTIVE)
	val state = _state.asStateFlow()

	var failureCause: Throwable? = null
		private set

	fun connect(address: String, port: Int) {
		webSocket?.cancel()

		if (isReconnecting() && (address != lastAddress || port != lastPort)) cancelReconnect()
		lastAddress = address
		lastPort = port

		Log.i(TAG, "Connecting to WebSocket server at $address:$port...")
		buildWebSocket(address, port)
		_state.value = State.CONNECTING
	}

	private fun buildWebSocket(address: String, port: Int) {
		val url = HttpUrl.Builder()
			.scheme("http")
			.host(address)
			.port(port)
			.build()
		val request = Request.Builder()
			.url(url)
			.build()
		webSocket = okHttpClient.newWebSocket(request, this)
	}

	fun close(code: Int = 1000, reason: String? = null): Boolean {
		if (isReconnecting()) cancelReconnect()
		return webSocket?.close(code, reason) ?: false
	}

	fun cancel() {
		if (isReconnecting()) cancelReconnect()
		webSocket?.cancel()
	}

	private suspend fun reconnect() {
		for (retry in 1..5) {
			Log.i(TAG, "Attempting to reconnect to WebSocket server, attempt $retry of 5...")
			connect(lastAddress!!, lastPort!!)

			val state = state.first { it == State.VERIFIED || it == State.CLOSED }
			if (state == State.VERIFIED) break

			if (retry < 5) delay(5000L)
		}

		if (_state.value == State.CLOSED) {
			_state.value = State.INACTIVE
			webSocket = null
		}
	}

	private fun cancelReconnect() {
		reconnectJob?.cancel()
		reconnectJob = null
		_state.value = State.INACTIVE
	}

	fun isReconnecting(): Boolean = reconnectJob != null

	fun sendBpm(bpm: Double): Boolean {
		if (_state.value != State.VERIFIED) return false
		val rounded = bpm.roundToInt()
		Log.d(TAG, "Sending BPM: $rounded")
		return webSocket?.send("set bpm $rounded") ?: false
	}

	fun sendBattery(battery: Int): Boolean {
		if (_state.value != State.VERIFIED) return false
		Log.d(TAG, "Sending battery: $battery")
		return webSocket?.send("set battery $battery") ?: false
	}

	private suspend fun verifyServerIsHeartsock(): Boolean {
		val response = coroutineScope.async {
			withTimeoutOrNull(3000L) { messages.first() }
		}

		delay(100)
		val sent = webSocket?.send("ping")

		return if (sent == true) {
			val text = response.await()
			text == "pong" || text?.matches(VALUE_MESSAGE_REGEX) ?: false
		} else {
			Log.d(TAG, "Failed to send ping")
			response.cancel()
			false
		}
	}

	override fun onOpen(webSocket: WebSocket, response: Response) {
		super.onOpen(webSocket, response)
		_state.value = State.OPEN
		Log.i(TAG, "WebSocket opened, verifying server")

		coroutineScope.launch {
			if (verifyServerIsHeartsock()) {
				_state.value = State.VERIFIED
				Log.i(TAG, "Server verified")
			} else {
				Log.i(TAG, "Server failed verification, closing WebSocket...")
				close(reason = "Unknown server type")
			}
		}
	}

	override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
		super.onClosing(webSocket, code, reason)
		Log.i(TAG, "WebSocket connection closing with code $code and reason: $reason")
		_state.value = State.CLOSING
	}

	override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
		super.onClosed(webSocket, code, reason)
		Log.i(TAG, "WebSocket connection closed with code $code and reason: $reason")
		_state.value = if (!isReconnecting()) State.CLOSED else State.INACTIVE
		this.webSocket = null
	}

	override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
		super.onFailure(webSocket, t, response)

		Log.e(TAG, "WebSocket connection failure: $t")
		failureCause = t
		this.webSocket = null

		if (_state.value == State.VERIFIED && !isReconnecting() && t !is EOFException) {
			reconnectJob = coroutineScope.launch {
				reconnect()
				reconnectJob = null
			}
		}

		_state.value = if (isReconnecting()) State.CLOSED else State.INACTIVE
	}

	override fun onMessage(webSocket: WebSocket, text: String) {
		super.onMessage(webSocket, text)
		Log.d(TAG, "Message received: $text")
		coroutineScope.launch {
			_messages.emit(text)
		}
	}

	enum class State {
		INACTIVE,
		CONNECTING,
		OPEN,
		VERIFIED,
		CLOSING,
		CLOSED
	}

	companion object {
		private const val TAG = "WebSocketClient"
		private val VALUE_MESSAGE_REGEX = "([a-z\\d]+): (\\d+)".toRegex()
	}
}