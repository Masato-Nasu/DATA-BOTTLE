package jp.masatolab.databottle.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BottleBackground = Color(0xFF000000)
val BottlePrimary = Color(0xFFB8FFF1)
val BottleOverflow = Color(0xFFFF6B6B)
val BottleText = Color(0xFFF2F7F5)
val BottleMuted = Color(0xFFB2C0BC)

private val DataBottleColors = darkColorScheme(
    primary = BottlePrimary,
    onPrimary = BottleBackground,
    background = BottleBackground,
    onBackground = BottleText,
    surface = BottleBackground,
    onSurface = BottleText,
    surfaceVariant = Color(0xFF101515),
    onSurfaceVariant = BottleMuted,
    secondary = BottleOverflow,
    onSecondary = BottleBackground,
    outline = BottleMuted
)

@Composable
fun DataBottleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DataBottleColors,
        content = content
    )
}
