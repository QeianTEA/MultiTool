package com.example.multitool.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF90CAF9),
    secondary = androidx.compose.ui.graphics.Color(0xFFCE93D8),
    tertiary = androidx.compose.ui.graphics.Color(0xFFFFAB91)
)

@Composable
fun MultiToolAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
