package dev.gawdl3y.android.heartsock.data

import android.content.Context

class SettingsRepository private constructor(
	private val settingsDataStore: SettingsDataStore
) {
	val serverAddress = settingsDataStore.serverAddress
	suspend fun setServerAddress(serverAddress: String?) = settingsDataStore.setServerAddress(serverAddress)

	val serverPort = settingsDataStore.serverPort
	suspend fun setServerPort(serverPort: UShort?) = settingsDataStore.setServerPort(serverPort)

	val keepScreenOn = settingsDataStore.keepScreenOn
	suspend fun setKeepScreenOn(keepScreenOn: Boolean?) = settingsDataStore.setKeepScreenOn(keepScreenOn)

	val ambientMode = settingsDataStore.ambientMode
	suspend fun setAmbientMode(ambientMode: Boolean?) = settingsDataStore.setAmbientMode(ambientMode)

//	val wakeFrequency = settingsDataStore.wakeFrequency
//	suspend fun setWakeFrequency(wakeFrequency: Int?) = settingsDataStore.setWakeFrequency(wakeFrequency)

	companion object {
		@Volatile
		private var INSTANCE: SettingsRepository? = null

		fun getInstance(context: Context): SettingsRepository {
			return INSTANCE ?: synchronized(this) {
				INSTANCE ?: SettingsRepository(SettingsDataStore(context)).also { INSTANCE = it }
			}
		}
	}
}