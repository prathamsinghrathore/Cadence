package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val SonoraDarkColorScheme = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = TextPrimary,
    primaryContainer = ObsidianElevated,
    onPrimaryContainer = TextPrimary,
    secondary = AuroraCyan,
    onSecondary = ObsidianVoid,
    secondaryContainer = ObsidianSurface,
    onSecondaryContainer = AuroraCyan,
    tertiary = PulseRose,
    onTertiary = TextPrimary,
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = ObsidianBase,
    onSurface = TextPrimary,
    surfaceVariant = ObsidianSurface,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorderTop,
    outlineVariant = GlassBorderBottom
)

val SonoraShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun SonoraTheme(
    ambientPalette: AmbientPaletteColors = AmbientPaletteColors(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAmbientPalette provides ambientPalette) {
        MaterialTheme(
            colorScheme = SonoraDarkColorScheme,
            typography = Typography,
            shapes = SonoraShapes,
            content = content
        )
    }
}
