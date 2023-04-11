package dev.gawdl3y.android.heartsock.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnState

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun ThemeScalingLazyColumn(
	modifier: Modifier = Modifier,
	state: ScalingLazyListState? = null,
	content: ScalingLazyListScope.() -> Unit
) {
	val columnState = remember {
		val columnState = ScalingLazyColumnState(rotaryMode = ScalingLazyColumnState.RotaryMode.Scroll)
		if (state != null) columnState.state = state
		columnState
	}

	ScalingLazyColumn(
		modifier = modifier,
		columnState = columnState,
		content = content
	)
}