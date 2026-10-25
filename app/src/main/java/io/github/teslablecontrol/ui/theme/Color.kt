package io.github.teslablecontrol.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Fixed grey palettes in the spirit of the official app, with a calm blue accent. Every text/background
 * pair is checked to WCAG AA (≥ 4.5:1) by ThemeContrastTest.
 */
object Palette {
    // Dark
    val DarkBackground = Color(0xFF121314)
    val DarkSurface = Color(0xFF1B1C1E)
    val DarkSurfaceLow = Color(0xFF202124)
    val DarkSurfaceContainer = Color(0xFF26282B)
    val DarkSurfaceHigh = Color(0xFF2E3034)
    val DarkSurfaceHighest = Color(0xFF383A3F)
    // The chosen option of a segmented slider: lighter than anything it sits on.
    val DarkSurfaceBright = Color(0xFF404348)
    val DarkOnSurface = Color(0xFFECEDEE)
    val DarkOnSurfaceVariant = Color(0xFFB5B8BD)
    val DarkOutline = Color(0xFF7D8188)
    val DarkOutlineVariant = Color(0xFF45484D)
    val DarkPrimary = Color(0xFF8AB4FF)
    val DarkOnPrimary = Color(0xFF0B1E45)
    val DarkPrimaryContainer = Color(0xFF3E6AE1)
    val DarkOnPrimaryContainer = Color(0xFFFFFFFF)
    val DarkSecondaryContainer = Color(0xFF3A3D42)
    val DarkOnSecondaryContainer = Color(0xFFECEDEE)
    val DarkError = Color(0xFFFF8A80)
    val DarkOnError = Color(0xFF3B0907)
    val DarkErrorContainer = Color(0xFF5C1A16)
    val DarkOnErrorContainer = Color(0xFFFFDAD5)
    val DarkSuccess = Color(0xFF5BD08F)
    val DarkWarning = Color(0xFFF5C065)

    // Light. The accent is darker than the button blue so it stays readable as text on grey cards.
    val LightBackground = Color(0xFFF2F3F5)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceLow = Color(0xFFF7F8F9)
    val LightSurfaceContainer = Color(0xFFE9EAEC)
    val LightSurfaceHigh = Color(0xFFE1E2E5)
    val LightSurfaceHighest = Color(0xFFD7D9DC)
    val LightSurfaceBright = Color(0xFFFFFFFF)
    val LightOnSurface = Color(0xFF17181A)
    val LightOnSurfaceVariant = Color(0xFF4D5157)
    val LightOutline = Color(0xFF6E7279)
    val LightOutlineVariant = Color(0xFFC6C9CE)
    val LightPrimary = Color(0xFF2A52C2)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFF3E6AE1)
    val LightOnPrimaryContainer = Color(0xFFFFFFFF)
    val LightSecondaryContainer = Color(0xFFDDE0E5)
    val LightOnSecondaryContainer = Color(0xFF17181A)
    val LightError = Color(0xFFB3261E)
    val LightOnError = Color(0xFFFFFFFF)
    val LightErrorContainer = Color(0xFFFFDAD5)
    val LightOnErrorContainer = Color(0xFF410E0B)
    val LightSuccess = Color(0xFF156B40)
    val LightWarning = Color(0xFF7A5000)
}
