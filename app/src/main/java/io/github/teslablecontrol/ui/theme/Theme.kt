package io.github.teslablecontrol.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.teslablecontrol.R
import io.github.teslablecontrol.data.ThemeMode

val DarkColors: ColorScheme = darkColorScheme(
    primary = Palette.DarkPrimary,
    onPrimary = Palette.DarkOnPrimary,
    primaryContainer = Palette.DarkPrimaryContainer,
    onPrimaryContainer = Palette.DarkOnPrimaryContainer,
    secondary = Palette.DarkOnSurfaceVariant,
    onSecondary = Palette.DarkBackground,
    secondaryContainer = Palette.DarkSecondaryContainer,
    onSecondaryContainer = Palette.DarkOnSecondaryContainer,
    tertiary = Palette.DarkSuccess,
    background = Palette.DarkBackground,
    onBackground = Palette.DarkOnSurface,
    surface = Palette.DarkSurface,
    onSurface = Palette.DarkOnSurface,
    surfaceVariant = Palette.DarkSurfaceContainer,
    onSurfaceVariant = Palette.DarkOnSurfaceVariant,
    surfaceContainerLowest = Palette.DarkBackground,
    surfaceContainerLow = Palette.DarkSurfaceLow,
    surfaceContainer = Palette.DarkSurfaceContainer,
    surfaceContainerHigh = Palette.DarkSurfaceHigh,
    surfaceContainerHighest = Palette.DarkSurfaceHighest,
    surfaceBright = Palette.DarkSurfaceBright,
    outline = Palette.DarkOutline,
    outlineVariant = Palette.DarkOutlineVariant,
    error = Palette.DarkError,
    onError = Palette.DarkOnError,
    errorContainer = Palette.DarkErrorContainer,
    onErrorContainer = Palette.DarkOnErrorContainer,
)

val LightColors: ColorScheme = lightColorScheme(
    primary = Palette.LightPrimary,
    onPrimary = Palette.LightOnPrimary,
    primaryContainer = Palette.LightPrimaryContainer,
    onPrimaryContainer = Palette.LightOnPrimaryContainer,
    secondary = Palette.LightOnSurfaceVariant,
    onSecondary = Palette.LightSurface,
    secondaryContainer = Palette.LightSecondaryContainer,
    onSecondaryContainer = Palette.LightOnSecondaryContainer,
    tertiary = Palette.LightSuccess,
    background = Palette.LightBackground,
    onBackground = Palette.LightOnSurface,
    surface = Palette.LightSurface,
    onSurface = Palette.LightOnSurface,
    surfaceVariant = Palette.LightSurfaceContainer,
    onSurfaceVariant = Palette.LightOnSurfaceVariant,
    surfaceContainerLowest = Palette.LightSurface,
    surfaceContainerLow = Palette.LightSurfaceLow,
    surfaceContainer = Palette.LightSurfaceContainer,
    surfaceContainerHigh = Palette.LightSurfaceHigh,
    surfaceContainerHighest = Palette.LightSurfaceHighest,
    surfaceBright = Palette.LightSurfaceBright,
    outline = Palette.LightOutline,
    outlineVariant = Palette.LightOutlineVariant,
    error = Palette.LightError,
    onError = Palette.LightOnError,
    errorContainer = Palette.LightErrorContainer,
    onErrorContainer = Palette.LightOnErrorContainer,
)

/** Status colours beyond Material's roles. Always paired with an icon and text, never used alone. */
@Immutable
data class StatusColors(val success: Color, val warning: Color, val danger: Color)

val LocalStatusColors = staticCompositionLocalOf {
    StatusColors(Palette.DarkSuccess, Palette.DarkWarning, Palette.DarkError)
}

/** One weight of the variable Inter font at an optical size (14 for text, up to 32 for display). */
private fun inter(weight: FontWeight, opticalSize: Float) = Font(
    R.font.inter,
    weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight), FontVariation.Setting("opsz", opticalSize)),
)

/**
 * Inter, a typeface drawn for screens and close to the lettering on the car's own touchscreen, for
 * everything you read; its display cut, with tabular figures, for readings.
 */
val Inter = FontFamily(
    inter(FontWeight.Normal, 14f),
    inter(FontWeight.Medium, 14f),
    inter(FontWeight.SemiBold, 14f),
)

val InterDisplay = FontFamily(
    inter(FontWeight.Medium, 32f),
    inter(FontWeight.SemiBold, 32f),
)

/** Larger-than-default type: body text is 16–17 sp and nothing is smaller than 14 sp. */
val AppTypography = Typography().let { base ->
    fun TextStyle.inter(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        copy(fontFamily = Inter, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = 0.sp)
    base.copy(
        displaySmall = base.displaySmall.inter(40, 46, FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.inter(28, 34, FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.inter(24, 30, FontWeight.SemiBold),
        titleLarge = base.titleLarge.inter(22, 28, FontWeight.SemiBold),
        titleMedium = base.titleMedium.inter(19, 25, FontWeight.SemiBold),
        titleSmall = base.titleSmall.inter(17, 22, FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.inter(17, 25),
        bodyMedium = base.bodyMedium.inter(16, 23),
        bodySmall = base.bodySmall.inter(14, 20),
        labelLarge = base.labelLarge.inter(16, 22, FontWeight.Medium),
        labelMedium = base.labelMedium.inter(14, 20, FontWeight.Medium),
        labelSmall = base.labelSmall.inter(14, 18, FontWeight.Medium),
    )
}

/** Big readings: battery, range, temperatures. Tabular figures keep digits from shifting as values change. */
val NumberStyle = TextStyle(
    fontFamily = InterDisplay,
    fontWeight = FontWeight.Medium,
    fontSize = 38.sp,
    lineHeight = 42.sp,
    fontFeatureSettings = "tnum",
)

/** The unit beside a reading, smaller so the number leads. */
val UnitStyle = TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Medium,
    fontSize = 18.sp,
    lineHeight = 22.sp,
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

@Composable
fun AppTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = isDarkTheme(mode)
    val status = if (dark) {
        StatusColors(Palette.DarkSuccess, Palette.DarkWarning, Palette.DarkError)
    } else {
        StatusColors(Palette.LightSuccess, Palette.LightWarning, Palette.LightError)
    }
    CompositionLocalProvider(LocalStatusColors provides status) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
