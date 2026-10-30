package io.github.aldrinadira.jalanz.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF00658F),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF8A5100),
    onSecondary = Color(0xFFFFFFFF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF84CFFF),
    onPrimary = Color(0xFF00344C),
    secondary = Color(0xFFFFB86E),
    onSecondary = Color(0xFF4A2800),
)

/** App-wide Compose theme for the phone UI; follows the system light/dark setting. */
@Composable
fun JalanzTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
