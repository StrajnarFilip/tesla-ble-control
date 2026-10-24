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

/**
 * Barlow, drawn from California highway signs and licence plates, for everything you read; its semi
 * condensed cut, with tabular figures, for readings, like an instrument cluster.
 */
val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)

val BarlowSemiCondensed = FontFamily(
    Font(R.font.barlow_semi_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
)

/** Larger-than-default type: body text is 16–17 sp and nothing is smaller than 14 sp. */
val AppTypography = Typography().let { base ->
    fun TextStyle.barlow(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) =
        copy(fontFamily = Barlow, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = 0.sp)
    base.copy(
        displaySmall = base.displaySmall.barlow(40, 46, FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.barlow(28, 34, FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.barlow(24, 30, FontWeight.SemiBold),
        titleLarge = base.titleLarge.barlow(22, 28, FontWeight.SemiBold),
        titleMedium = base.titleMedium.barlow(19, 25, FontWeight.SemiBold),
        titleSmall = base.titleSmall.barlow(17, 22, FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.barlow(17, 25),
        bodyMedium = base.bodyMedium.barlow(16, 23),
        bodySmall = base.bodySmall.barlow(14, 20),
        labelLarge = base.labelLarge.barlow(16, 22, FontWeight.Medium),
        labelMedium = base.labelMedium.barlow(14, 20, FontWeight.Medium),
        labelSmall = base.labelSmall.barlow(14, 18, FontWeight.Medium),
    )
}

/** Big readings: battery, range, temperatures. Tabular figures keep digits from shifting as values change. */
val NumberStyle = TextStyle(
    fontFamily = BarlowSemiCondensed,
    fontWeight = FontWeight.SemiBold,
    fontSize = 38.sp,
    lineHeight = 42.sp,
    fontFeatureSettings = "tnum",
)

/** The unit beside a reading, smaller so the number leads. */
val UnitStyle = TextStyle(
    fontFamily = BarlowSemiCondensed,
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
