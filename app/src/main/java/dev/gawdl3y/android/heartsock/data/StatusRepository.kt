package dev.gawdl3y.android.heartsock.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class StatusRepository {
	private val _status = MutableStateFlow(ServiceStatus.DISCONNECTED)
	val status = _status.asStateFlow()

	private val _progress = MutableStateFlow(0F)
	val progress = _progress.asStateFlow()

	private val _bpm = MutableStateFlow(0.0)
	val bpm = _bpm.asStateFlow()

	fun setStatus(status: ServiceStatus) {
		_status.value = status
	}

	fun setProgress(progress: Float) {
		_progress.value = progress
	}

	fun setBpm(bpm: Double) {
		_bpm.value = bpm
	}

	companion object {
		@Volatile
		private var INSTANCE: StatusRepository? = null

		fun getInstance(): StatusRepository {
			return INSTANCE ?: synchronized(this) {
				INSTANCE ?: StatusRepository().also { INSTANCE = it }
			}
		}
	}
}