package dev.gawdl3y.android.heartsock.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.wear.compose.material.*
import androidx.wear.compose.navigation.composable
import dev.gawdl3y.android.heartsock.R
import dev.gawdl3y.android.heartsock.data.ServiceStatus
import dev.gawdl3y.android.heartsock.models.MainViewModel
import kotlin.math.roundToInt

private const val TAG = "MainScreen"

@Composable
fun MainScreen(
	onActionConnect: () -> Unit,
	onActionDisconnect: () -> Unit,
	onActionSettings: () -> Unit,
	viewModel: MainViewModel = viewModel()
) {
	val status = viewModel.status.collectAsState(initial = ServiceStatus.DISCONNECTED)
	val progress = viewModel.progress.collectAsState(initial = 0F)
	val bpm = viewModel.bpm.collectAsState(initial = 0.0)

	Box(modifier = Modifier.fillMaxSize()) {
		ServiceActionButton(
			modifier = Modifier.align(Alignment.Center),
			status = status,
			progress = progress,
			bpm = bpm,
			onActionConnect = onActionConnect,
			onActionDisconnect = onActionDisconnect
		)

		Chip(
			label = { Text(stringResource(R.string.action_settings)) },
			colors = ChipDefaults.childChipColors(),
			modifier = Modifier.align(Alignment.BottomCenter),
			icon = {
				Icon(
					Icons.Default.Settings,
					contentDescription = stringResource(R.string.icon_description_settings)
				)
			},
			onClick = onActionSettings
		)
	}
}

@Composable
fun ServiceActionButton(
	status: State<ServiceStatus>,
	progress: State<Float>,
	bpm: State<Double>,
	onActionConnect: () -> Unit,
	onActionDisconnect: () -> Unit,
	modifier: Modifier = Modifier,
) {
	Box(modifier = modifier) {
		Button(onClick = {
			when (status.value) {
				ServiceStatus.DISCONNECTED -> onActionConnect()
				else -> onActionDisconnect()
			}
		}) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Crossfade(status.value) {
					when (it) {
						ServiceStatus.DISCONNECTED -> Icon(
							Icons.Default.PlayArrow,
							contentDescription = stringResource(R.string.icon_description_play),
							modifier = Modifier.padding(horizontal = 14.dp)
						)
						else -> Icon(
							Icons.Default.Clear,
							contentDescription = stringResource(R.string.icon_description_clear),
							modifier = Modifier.padding(horizontal = 14.dp)
						)
					}
				}

				Row(modifier = Modifier.animateContentSize()) {
					AnimatedVisibility(
						visible = status.value == ServiceStatus.CONNECTED,
						enter = fadeIn(),
						exit = fadeOut()
					) {
						AnimatedContent(
							targetState = bpm.value.roundToInt(),
							transitionSpec = {
								if (targetState > initialState) {
									slideInVertically { height -> height } + fadeIn() with
											slideOutVertically { height -> -height } + fadeOut()
								} else {
									slideInVertically { height -> -height } + fadeIn() with
											slideOutVertically { height -> height } + fadeOut()
								}.using(
									SizeTransform(clip = false)
								)
							}
						) { targetBpm ->
							Text(
								text = targetBpm.toString(),
								modifier = Modifier.padding(end = 14.dp)
							)
						}
					}
				}
			}
		}

		ConnectionProgressIndicator(status)
		ScanProgressIndicator(status, progress)
	}
}

@Composable
fun ConnectionProgressIndicator(status: State<ServiceStatus>) {
	AnimatedVisibility(
		visible = status.value == ServiceStatus.CONNECTING || status.value == ServiceStatus.RECONNECTING,
		enter = scaleIn(initialScale = 0.6f) + fadeIn(),
		exit = scaleOut(targetScale = 0.6f) + fadeOut()
	) {
		CircularProgressIndicator(
			indicatorColor = MaterialTheme.colors.secondary,
			trackColor = Color.Transparent,
			strokeWidth = 3.dp,
			modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
		)
	}
}

@Composable
fun ScanProgressIndicator(status: State<ServiceStatus>, progress: State<Float>) {
	AnimatedVisibility(
		visible = status.value == ServiceStatus.SCANNING || status.value == ServiceStatus.AWAITING_WIFI,
		enter = scaleIn(initialScale = 0.6f) + fadeIn(),
		exit = scaleOut(targetScale = 0.6f) + fadeOut()
	) {
		Crossfade(status.value) { status ->
			when (status) {
				ServiceStatus.SCANNING -> {
					val progressTransition = updateTransition((1F - progress.value).coerceAtLeast(0F), label = "Progress")
					val progressAnimated by progressTransition.animateFloat(
						label = "Progress",
						transitionSpec = {
							tween(durationMillis = 500)
						}
					) { it }

					CircularProgressIndicator(
						progress = progressAnimated,
						indicatorColor = MaterialTheme.colors.primaryVariant,
						trackColor = Color.Transparent,
						strokeWidth = 3.dp,
						modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
					)
				}
				else -> CircularProgressIndicator(
					indicatorColor = MaterialTheme.colors.primaryVariant,
					trackColor = Color.Transparent,
					strokeWidth = 3.dp,
					modifier = Modifier.size(ButtonDefaults.DefaultButtonSize)
				)
			}
		}
	}
}

fun NavGraphBuilder.mainScreen(
	onActionConnect: () -> Unit,
	onActionDisconnect: () -> Unit,
	onActionSettings: () -> Unit
) {
	composable("main") {
		MainScreen(
			onActionConnect,
			onActionDisconnect,
			onActionSettings
		)
	}
}

fun NavController.navigateToMain() {
	navigate("main")
}