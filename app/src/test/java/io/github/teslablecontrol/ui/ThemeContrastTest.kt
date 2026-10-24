package io.github.teslablecontrol.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import io.github.teslablecontrol.ui.theme.DarkColors
import io.github.teslablecontrol.ui.theme.LightColors
import io.github.teslablecontrol.ui.theme.Palette
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** WCAG 2.1: 4.5:1 for text, 3:1 for icons and control outlines. */
class ThemeContrastTest {
    private fun luminance(c: Color): Double {
        fun channel(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    }

    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun textPairs(s: ColorScheme) = listOf(
        "onSurface/surface" to (s.onSurface to s.surface),
        "onSurface/background" to (s.onSurface to s.background),
        "onSurface/surfaceContainer" to (s.onSurface to s.surfaceContainer),
        "onSurface/surfaceContainerHigh" to (s.onSurface to s.surfaceContainerHigh),
        "onSurface/surfaceContainerHighest" to (s.onSurface to s.surfaceContainerHighest),
        "onSurfaceVariant/background" to (s.onSurfaceVariant to s.background),
        "onSurfaceVariant/surfaceContainer" to (s.onSurfaceVariant to s.surfaceContainer),
        "onSurfaceVariant/surfaceContainerHigh" to (s.onSurfaceVariant to s.surfaceContainerHigh),
        "onPrimary/primary" to (s.onPrimary to s.primary),
        "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
        "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
        "onErrorContainer/errorContainer" to (s.onErrorContainer to s.errorContainer),
        "error/surfaceContainer" to (s.error to s.surfaceContainer),
        "error/background" to (s.error to s.background),
        "primary/background" to (s.primary to s.background),
        "primary/surfaceContainer" to (s.primary to s.surfaceContainer),
        // Dialogs and menus sit on the higher containers.
        "primary/surfaceContainerHigh" to (s.primary to s.surfaceContainerHigh),
        "primary/surfaceContainerHighest" to (s.primary to s.surfaceContainerHighest),
        "onSurfaceVariant/surfaceContainerHighest" to (s.onSurfaceVariant to s.surfaceContainerHighest),
        "error/surfaceContainerHigh" to (s.error to s.surfaceContainerHigh),
        "tertiary/surfaceContainer" to (s.tertiary to s.surfaceContainer),
        "tertiary/background" to (s.tertiary to s.background),
    )

    private fun check(name: String, scheme: ColorScheme, success: Color, warning: Color) {
        val failures = textPairs(scheme).mapNotNull { (label, pair) ->
            val ratio = contrast(pair.first, pair.second)
            if (ratio < 4.5) "$name $label = ${"%.2f".format(ratio)}" else null
        } + listOf(
            "outline/surfaceContainer" to (scheme.outline to scheme.surfaceContainer),
            "success/surfaceContainer" to (success to scheme.surfaceContainer),
            "warning/surfaceContainer" to (warning to scheme.surfaceContainer),
        ).mapNotNull { (label, pair) ->
            val ratio = contrast(pair.first, pair.second)
            if (ratio < 3.0) "$name $label = ${"%.2f".format(ratio)}" else null
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun darkThemeMeetsWcagAa() = check("dark", DarkColors, Palette.DarkSuccess, Palette.DarkWarning)

    @Test
    fun lightThemeMeetsWcagAa() = check("light", LightColors, Palette.LightSuccess, Palette.LightWarning)
}
