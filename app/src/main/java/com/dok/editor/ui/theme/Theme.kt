package com.dok.editor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DokColorScheme = darkColorScheme(
    primary = DokAccent,
    onPrimary = DokBackground,
    primaryContainer = DokAccentVariant,
    onPrimaryContainer = DokPrimaryText,
    secondary = DokAccentVariant,
    onSecondary = DokPrimaryText,
    background = DokBackground,
    onBackground = DokPrimaryText,
    surface = DokSurface,
    onSurface = DokPrimaryText,
    surfaceVariant = DokSurfaceElevated,
    onSurfaceVariant = DokSecondaryText,
    outline = DokDivider
)

@Composable
fun DokEditorTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DokColorScheme,
        content = content
    )
}
