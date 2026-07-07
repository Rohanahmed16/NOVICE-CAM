package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CameraColorScheme = darkColorScheme(
    primary = ChampagneGold,
    onPrimary = CharcoalBlack,
    secondary = LightChampagne,
    onSecondary = CharcoalBlack,
    tertiary = AmberIndicator,
    background = CharcoalBlack,
    onBackground = SoftCream,
    surface = DarkWarmStone,
    onSurface = SoftCream,
    surfaceVariant = MediumWarmStone,
    onSurfaceVariant = LightChampagne,
    error = RedRecording
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit,
) {
    // We always use our custom luxury camera color scheme to preserve the DSLR-grade interface
    MaterialTheme(
        colorScheme = CameraColorScheme,
        typography = Typography,
        content = content
    )
}
