package dev.gawdl3y.android.heartsock.models

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.gawdl3y.android.heartsock.HeartsockApplication

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
	private val settingsRepository = (app as HeartsockApplication).settingsRepository
	private val statusRepository = (app as HeartsockApplication).statusRepository

	val serverAddress = settingsRepository.serverAddress
	val serverPort = settingsRepository.serverPort
	val keepScreenOn = settingsRepository.keepScreenOn
	val ambientMode = settingsRepository.ambientMode
	val useSensormanager = settingsRepository.useSensorManager
	val status = statusRepository.status
	val progress = statusRepository.progress

	suspend fun onServerAddressChange(value: String) {
		val address = value.ifBlank { "" }
		settingsRepository.setServerAddress(address)
	}

	@Throws(IllegalArgumentException::class, NumberFormatException::class)
	suspend fun onServerPortChange(value: String) {
		val port = value.toUShort()
		if (port < 1U) throw IllegalArgumentException("Invalid port: $value")
		settingsRepository.setServerPort(port)
	}

	suspend fun onKeepScreenOnChange(value: Boolean) {
		settingsRepository.setKeepScreenOn(value)
	}

	suspend fun onAmbientModeChange(value: Boolean) {
		settingsRepository.setAmbientMode(value)
	}

	suspend fun onUseSensorManagerChange(value: Boolean) {
		settingsRepository.setUseSensorManager(value)
	}

	suspend fun onResetSettings() {
		settingsRepository.setServerAddress(null)
		settingsRepository.setServerPort(null)
		settingsRepository.setKeepScreenOn(null)
		settingsRepository.setAmbientMode(null)
		settingsRepository.setUseSensorManager(null)
	}
}