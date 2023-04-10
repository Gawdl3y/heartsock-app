package dev.gawdl3y.android.heartsock.models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.gawdl3y.android.heartsock.HeartsockApplication

class MainViewModel(app: Application) : AndroidViewModel(app) {
	private val statusRepository = (app as HeartsockApplication).statusRepository

	val status = statusRepository.status
	val progress = statusRepository.progress
	val bpm = statusRepository.bpm
}

