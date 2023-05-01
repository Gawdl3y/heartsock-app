package dev.gawdl3y.android.heartsock.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.paddingFromBaseline
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.dialog.Alert
import androidx.wear.compose.material.dialog.Dialog
import com.google.android.horologist.compose.navscaffold.scalingLazyColumnComposable
import dev.gawdl3y.android.heartsock.R
import dev.gawdl3y.android.heartsock.data.ServiceStatus
import dev.gawdl3y.android.heartsock.models.SettingsViewModel
import dev.gawdl3y.android.heartsock.ui.components.TextInput
import dev.gawdl3y.android.heartsock.ui.components.ThemeScalingLazyColumn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TAG = "SettingsScreen"

@Composable
fun SettingsScreen(
	listState: ScalingLazyListState,
	onActionScan: (cancel: Boolean) -> Unit,
	onToggleKeepScreenOn: (keepScreenOn: Boolean) -> Unit,
	onToggleAmbientMode: (ambientMode: Boolean) -> Unit,
	viewModel: SettingsViewModel = viewModel()
) {
	val status = viewModel.status.collectAsState(initial = ServiceStatus.DISCONNECTED)
	val progress = viewModel.progress.collectAsState(initial = 0F)
	val serverAddress = viewModel.serverAddress.collectAsState(initial = "")
	val serverPort = viewModel.serverPort.collectAsState(initial = 0)
	val keepScreenOn = viewModel.keepScreenOn.collectAsState(initial = false)
	val ambientMode = viewModel.ambientMode.collectAsState(initial = false)
	val useSensorManager = viewModel.useSensormanager.collectAsState(initial = false)

	val coroutineScope = rememberCoroutineScope()
	var showResetDialog by remember { mutableStateOf(false) }

	ThemeScalingLazyColumn(
		state = listState,
		modifier = Modifier.fillMaxSize(),
	) {
		// Server settings header
		item {
			ListHeader(R.string.settings_server)
		}

		// Server address
		item {
			TextInput(
				label = stringResource(R.string.label_address),
				placeholder = stringResource(R.string.placeholder_none),
				value = serverAddress.value,
				onChange = { value ->
					coroutineScope.launch {
						viewModel.onServerAddressChange(value)
					}
				}
			)
		}

		// Server port
		item {
			val context = LocalContext.current
			TextInput(
				label = stringResource(R.string.label_port),
				placeholder = stringResource(R.string.placeholder_none),
				value = serverPort.value.toString(),
				onChange = { value ->
					coroutineScope.launch {
						try {
							viewModel.onServerPortChange(value)
						} catch (ex: Exception) {
							Log.d(TAG, "onServerPortChange error: $ex")
							Toast.makeText(context, R.string.error_port, Toast.LENGTH_SHORT).show()
						}
					}
				}
			)
		}

		// Scan for server
		item {
			Chip(
				modifier = Modifier.fillMaxWidth(),
				colors = ChipDefaults.secondaryChipColors(),
				label = {
					AnimatedContent(
						targetState = when (status.value) {
							ServiceStatus.SCANNING, ServiceStatus.AWAITING_WIFI -> stringResource(R.string.action_cancel_scan)
							else -> stringResource(R.string.action_scan)
						},
						label = "Scan label animation"
					) { text ->
						Text(text)
					}
				},
				secondaryLabel = {
					AnimatedContent(
						targetState = when (status.value) {
							ServiceStatus.SCANNING, ServiceStatus.AWAITING_WIFI -> stringResource(status.value.stringResource)
							else -> null
						},
						label = "Scan secondary label animation"
					) { text ->
						if (text != null) Text(text)
					}
				},
				icon = {
					Crossfade(status.value, label = "Scan progress crossfade") { status ->
						val progressTransition =
							updateTransition((1F - progress.value).coerceAtLeast(0F), label = "Progress")
						val progressAnimated by progressTransition.animateFloat(
							label = "Progress",
							transitionSpec = {
								tween(durationMillis = 500)
							}
						) { it }

						when (status) {
							ServiceStatus.SCANNING -> CircularProgressIndicator(
								progress = progressAnimated,
								modifier = Modifier
									.size(ChipDefaults.IconSize)
									.align(Alignment.Center),
								strokeWidth = 2.dp
							)

							ServiceStatus.AWAITING_WIFI -> CircularProgressIndicator(
								modifier = Modifier
									.size(ChipDefaults.IconSize)
									.align(Alignment.Center),
								strokeWidth = 2.dp
							)

							else -> Icon(
								Icons.Default.Search,
								contentDescription = stringResource(R.string.icon_description_search),
								modifier = Modifier.align(Alignment.Center)
							)
						}
					}
				},
				onClick = {
					onActionScan(
						status.value == ServiceStatus.SCANNING || status.value == ServiceStatus.AWAITING_WIFI
					)
				}
			)
		}

		// Display settings header
		item {
			ListHeader(R.string.settings_display)
		}

		// Keep screen on
		item {
			ToggleSetting(
				label = R.string.label_keep_screen_on,
				checked = keepScreenOn.value,
				onToggle = {
					onToggleKeepScreenOn(it)
					coroutineScope.launch {
						viewModel.onKeepScreenOnChange(it)
					}
				}
			)
		}

		// Ambient mode
		item {
			ToggleSetting(
				label = R.string.label_ambient_mode,
				checked = ambientMode.value,
				enabled = !keepScreenOn.value,
				onToggle = {
					onToggleAmbientMode(it)
					coroutineScope.launch {
						viewModel.onAmbientModeChange(it)
					}
				}
			)
		}

		// Performance settings header
		item {
			ListHeader(R.string.settings_performance)
		}

		item {
			ToggleSetting(
				label = R.string.label_use_sensor_manager,
				secondaryLabel = if (useSensorManager.value) R.string.label_use_sensor_manager_true else R.string.label_use_sensor_manager_false,
				checked = useSensorManager.value,
				onToggle = {
					coroutineScope.launch {
						viewModel.onUseSensorManagerChange(it)
					}
				}
			)
		}

		// Super special spacer
		item {
			Spacer(Modifier.size(4.dp))
		}

		// Reset settings
		item {
			Chip(
				colors = ChipDefaults.childChipColors(),
				label = { Text(stringResource(R.string.action_reset)) },
				icon = {
					Icon(
						painterResource(R.drawable.ic_restart_alt_24),
						contentDescription = stringResource(R.string.icon_description_restart)
					)
				},
				onClick = { showResetDialog = true }
			)
		}
	}

	// Confirmation dialog for settings reset
	val resetDialogScrollState = rememberScalingLazyListState()
	Dialog(
		showDialog = showResetDialog,
		onDismissRequest = { showResetDialog = false },
		scrollState = resetDialogScrollState
	) {
		Alert(
			title = { Text(stringResource(R.string.confirm_title_reset), textAlign = TextAlign.Center) },
			icon = {
				Icon(
					painterResource(R.drawable.ic_restart_alt_24),
					contentDescription = stringResource(R.string.icon_description_restart),
					modifier = Modifier
						.size(24.dp)
						.wrapContentSize(align = Alignment.Center),
				)
			},
			positiveButton = {
				Button(
					colors = ButtonDefaults.primaryButtonColors(),
					onClick = {
						showResetDialog = false
						coroutineScope.launch {
							viewModel.onResetSettings()
							onToggleKeepScreenOn(viewModel.keepScreenOn.first())
							onToggleAmbientMode(viewModel.ambientMode.first())
						}
					}
				) {
					Icon(
						Icons.Default.Check,
						contentDescription = stringResource(R.string.icon_description_check)
					)
				}
			},
			negativeButton = {
				Button(
					colors = ButtonDefaults.secondaryButtonColors(),
					onClick = { showResetDialog = false }
				) {
					Icon(
						Icons.Default.Clear,
						contentDescription = stringResource(R.string.icon_description_clear)
					)
				}
			},
		) {
			Text(
				text = stringResource(R.string.confirm_body_generic),
				textAlign = TextAlign.Center,
				style = MaterialTheme.typography.body2
			)
		}
	}
}

@Composable
fun ListHeader(string: Int, modifier: Modifier = Modifier) {
	Text(
		text = stringResource(string),
		modifier = modifier.paddingFromBaseline(top = 24.dp, bottom = 8.dp),
		style = MaterialTheme.typography.title3
	)
}

@Composable
fun ToggleSetting(
	label: Int,
	checked: Boolean,
	enabled: Boolean = true,
	onToggle: (value: Boolean) -> Unit,
	secondaryLabel: Int? = null,
) {
	ToggleChip(
		modifier = Modifier.fillMaxWidth(),
		label = { Text(stringResource(label)) },
		secondaryLabel = {
			if (secondaryLabel != null) {
				AnimatedContent(targetState = secondaryLabel, label = "Toggle secondary label animation") {
					Text(stringResource(it))
				}
			}
		},
		checked = checked,
		enabled = enabled,
		toggleControl = {
			val description = stringResource(if (checked) R.string.toggle_on else R.string.toggle_off)
			Switch(
				checked = checked,
				modifier = Modifier.semantics { this.contentDescription = description }
			)
		},
		onCheckedChange = { onToggle(it) }
	)
}

fun NavGraphBuilder.settingsScreen(
	onActionScan: (cancel: Boolean) -> Unit,
	onToggleKeepScreenOn: (keepScreenOn: Boolean) -> Unit,
	onToggleAmbientMode: (ambientMode: Boolean) -> Unit
) {
	scalingLazyColumnComposable(
		"settings",
		scrollStateBuilder = { ScalingLazyListState() }
	) {
		SettingsScreen(
			listState = it.scrollableState,
			onActionScan,
			onToggleKeepScreenOn,
			onToggleAmbientMode
		)
	}
}

fun NavController.navigateToSettings() {
	navigate("settings")
}