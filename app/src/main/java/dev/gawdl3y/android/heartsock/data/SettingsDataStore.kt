package dev.gawdl3y.android.heartsock.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsDataStore(private val context: Context) {
	private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(SETTINGS_DATASTORE_NAME)

	val serverAddress: Flow<String> = context.dataStore.data.map {
		it[SERVER_ADDRESS_KEY] ?: ""
	}

	suspend fun setServerAddress(serverAddress: String?) {
		context.dataStore.edit {
			it[SERVER_ADDRESS_KEY] = serverAddress ?: ""
		}
	}

	val serverPort: Flow<Int> = context.dataStore.data.map {
		it[SERVER_PORT_KEY] ?: 9001
	}

	suspend fun setServerPort(serverPort: UShort?) {
		context.dataStore.edit {
			it[SERVER_PORT_KEY] = serverPort?.toInt() ?: 9001
		}
	}

	val keepScreenOn: Flow<Boolean> = context.dataStore.data.map {
		it[KEEP_SCREEN_ON_KEY] ?: false
	}

	suspend fun setKeepScreenOn(keepScreenOn: Boolean?) {
		context.dataStore.edit {
			it[KEEP_SCREEN_ON_KEY] = keepScreenOn ?: false
		}
	}

	val ambientMode: Flow<Boolean> = context.dataStore.data.map {
		it[AMBIENT_MODE_KEY] ?: false
	}

	suspend fun setAmbientMode(ambientMode: Boolean?) {
		context.dataStore.edit {
			it[AMBIENT_MODE_KEY] = ambientMode ?: false
		}
	}

//	val wakeFrequency: Flow<Int> = context.dataStore.data.map {
//		it[WAKE_FREQUENCY_KEY] ?: 5
//	}
//
//	suspend fun setWakeFrequency(wakeFrequency: Int?) {
//		context.dataStore.edit {
//			it[WAKE_FREQUENCY_KEY] = wakeFrequency ?: 0
//		}
//	}

	companion object {
		private const val SETTINGS_DATASTORE_NAME = "settings_datastore"
		private val SERVER_ADDRESS_KEY = stringPreferencesKey("server_address")
		private val SERVER_PORT_KEY = intPreferencesKey("server_port")
		private val KEEP_SCREEN_ON_KEY = booleanPreferencesKey("keep_screen_on")
		private val AMBIENT_MODE_KEY = booleanPreferencesKey("ambient_mode")
//		private val WAKE_FREQUENCY_KEY = intPreferencesKey("force_wake_frequency")
	}
}