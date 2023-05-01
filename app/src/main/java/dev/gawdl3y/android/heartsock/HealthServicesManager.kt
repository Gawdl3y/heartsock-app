package dev.gawdl3y.android.heartsock

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventCallback
import android.hardware.SensorManager
import android.util.Log
import androidx.concurrent.futures.await
import androidx.health.services.client.HealthServicesClient
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.SampleDataPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking

class HealthServicesManager(healthServicesClient: HealthServicesClient, private val sensorManager: SensorManager) {
	private val measureClient = healthServicesClient.measureClient
	private val heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

	suspend fun hasHeartRateCapability(): Boolean {
		val capabilities = measureClient.getCapabilitiesAsync().await()
		return DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure
	}

	fun hasHeartRateSensor(): Boolean {
		return heartRateSensor != null
	}

	fun heartRateMeasureFlow() = callbackFlow {
		val callback = object : MeasureCallback {
			override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
				if (availability is DataTypeAvailability) {
					trySendBlocking(MeasureMessage.MeasureAvailability(availability))
				}
			}

			override fun onDataReceived(data: DataPointContainer) {
				val bpm = data.getData(DataType.HEART_RATE_BPM)
				trySendBlocking(MeasureMessage.MeasureData(bpm))
			}
		}

		Log.d(TAG, "Registering for data with measure client")
		measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)

		awaitClose {
			Log.d(TAG, "Unregistering for data with measure client")
			runBlocking {
				measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
			}
		}
	}

	fun heartRateSensorFlow() = callbackFlow {
		val callback = object : SensorEventCallback() {
			override fun onSensorChanged(event: SensorEvent?) {
				val bpm = event?.values?.get(0) ?: 0F
				trySendBlocking(bpm)
			}
		}

		Log.d(TAG, "Registering for data with sensor manager")
		sensorManager.registerListener(callback, heartRateSensor, SensorManager.SENSOR_DELAY_FASTEST)

		awaitClose {
			Log.d(TAG, "Unregistering for data with sensor manager")
			sensorManager.unregisterListener(callback)
		}
	}

	companion object {
		private const val TAG = "HealthServicesManager"
	}
}

sealed class MeasureMessage {
	class MeasureAvailability(val availability: DataTypeAvailability) : MeasureMessage()
	class MeasureData(val data: List<SampleDataPoint<Double>>) : MeasureMessage()
}