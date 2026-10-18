package com.sesliplanlayici.app.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// Light only for the alpha; the recording screen uses inverseSurface for its dark background.
private val AppColorScheme = lightColorScheme(
    primary = Accent,
    onPrimary = Surface,
    primaryContainer = MissingContainer,
    onPrimaryContainer = OnMissingContainer,
    tertiary = Success,
    onTertiary = Surface,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Chip,
    onSurfaceVariant = TextSecondary,
    inverseSurface = TextPrimary,
    inverseOnSurface = Background,
    inversePrimary = RecordingAccent,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun SesliPlanlayiciTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
