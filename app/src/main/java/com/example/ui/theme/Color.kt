package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val ObsidianVoid = Color(0xFF07090F)
val ObsidianBase = Color(0xFF0B0E17)
val ObsidianSurface = Color(0xFF121726)
val ObsidianElevated = Color(0xFF1A2035)

val ElectricViolet = Color(0xFF8B6EFF)
val AuroraCyan = Color(0xFF2EE6D6)
val PulseRose = Color(0xFFFF6B9A)
val WarmAmber = Color(0xFFFFB86C)

val TextPrimary = Color(0xFFF6F7FB)
val TextSecondary = Color(0xFFA9B2C9)
val TextMuted = Color(0xFF6D7690)

val GlassFillTop = Color(0x24FFFFFF)
val GlassFillBottom = Color(0x0AFFFFFF)
val GlassBorderTop = Color(0x38FFFFFF)
val GlassBorderBottom = Color(0x12FFFFFF)
val GlassScrimDark = Color(0xCC090B12)

@Immutable
data class AmbientPaletteColors(
    val primaryGlow: Color = ElectricViolet,
    val secondaryGlow: Color = AuroraCyan,
    val deepTint: Color = ObsidianSurface,
    val accent: Color = PulseRose,
    val vibrantColor: Color = ElectricViolet,
    val dominantColor: Color = PulseRose,
    val lightVibrantColor: Color = AuroraCyan
)

val LocalAmbientPalette = staticCompositionLocalOf { AmbientPaletteColors() }
