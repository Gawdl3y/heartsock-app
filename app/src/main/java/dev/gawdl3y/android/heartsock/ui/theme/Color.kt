package dev.gawdl3y.android.heartsock.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors

val Purple200 = Color(0xFFBB86FC)
val Purple500 = Color(0xFF6200EE)
val Purple700 = Color(0xFF3700B3)
val Teal200 = Color(0xFF03DAC5)
val Red400 = Color(0xFFCF6679)

val PrussianBlue = Color(0xFF12355B)
val DarkPurple = Color(0xFF420039)
val Crimson = Color(0xFFD72638)
val OrangePantone = Color(0xFFFF570A)

internal val wearColorPalette: Colors = Colors(
//	primary = Purple200,
//	primaryVariant = Purple700,
//	secondary = Teal200,
//	secondaryVariant = Teal200,
//	error = Red400,
	primary = Crimson,
	primaryVariant = OrangePantone,
	secondary = DarkPurple,
	secondaryVariant = PrussianBlue,
	error = Crimson,
	onPrimary = Color.White,
	onSecondary = Color.White,
	onError = Color.White
)