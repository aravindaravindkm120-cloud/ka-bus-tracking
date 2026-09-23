package com.kabus.admin.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Brand = Color(0xFF0B6E51)
private val BrandDark = Color(0xFF084C39)

private val LightColors = lightColorScheme(
    primary = Brand,
    secondary = BrandDark,
    background = Color(0xFFF5F7F6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DD0A4),
    secondary = Color(0xFF84B79F),
    background = Color(0xFF111614)
)

@Composable
fun KaBusAdminTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}