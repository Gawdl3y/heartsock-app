package dev.gawdl3y.android.heartsock.ui.components

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender

@Composable
fun TextInput(
	label: String,
	placeholder: String,
	value: String?,
	onChange: (value: String) -> Unit,
) {
	val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
		it.data?.let { data ->
			val results: Bundle? = RemoteInput.getResultsFromIntent(data)
			val newValue: CharSequence? = results?.getCharSequence(label)
			onChange((newValue ?: "").toString())
		}
	}

	Chip(
		label = { Text(label) },
		secondaryLabel = { Text(if (value == null || value.isEmpty()) placeholder else value) },
		colors = ChipDefaults.secondaryChipColors(),
		modifier = Modifier.fillMaxWidth(),
		onClick = {
			val intent: Intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
			val remoteInputs: List<RemoteInput> = listOf(
				RemoteInput.Builder(label)
					.setLabel(label)
					.wearableExtender {
						setEmojisAllowed(false)
						setInputActionType(EditorInfo.IME_ACTION_DONE)
					}.build()
			)
			RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
			launcher.launch(intent)
		}
	)
}