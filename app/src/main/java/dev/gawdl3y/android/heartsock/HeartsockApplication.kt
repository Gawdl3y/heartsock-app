package dev.gawdl3y.android.heartsock

import android.app.Application
import dev.gawdl3y.android.heartsock.data.SettingsRepository
import dev.gawdl3y.android.heartsock.data.StatusRepository

class HeartsockApplication : Application() {
	val settingsRepository by lazy {
		SettingsRepository.getInstance(applicationContext)
	}

	val statusRepository by lazy {
		StatusRepository.getInstance()
	}
}