package com.ghtnql.kkkeyboard.sharedui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kkkeyboard.sharedui.generated.resources.Res
import kkkeyboard.sharedui.generated.resources.seoul_day_app_background
import kkkeyboard.sharedui.generated.resources.seoul_night_app_background

/** App tokens are separate from the canonical native keyboard palette. */
internal data class AppThemeSpec(
    val colorScheme: ColorScheme,
    val surfaceElevated: Color,
    val surfaceMuted: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val scrim: Color,
    val backgroundArtwork: DrawableResource? = null,
    val artworkHeaderAlpha: Float = 0f,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF166954),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EFE5),
    onPrimaryContainer = Color(0xFF103F33),
    secondary = Color(0xFF49685D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDFECE5),
    onSecondaryContainer = Color(0xFF1F242A),
    tertiary = Color(0xFF49685D),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDFECE5),
    onTertiaryContainer = Color(0xFF1F242A),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1F242A),
    surface = Color.White,
    onSurface = Color(0xFF1F242A),
    surfaceVariant = Color(0xFFEBEEF1),
    onSurfaceVariant = Color(0xFF5B656F),
    surfaceDim = Color(0xFFE8ECEA),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF5F7F6),
    surfaceContainer = Color(0xFFF0F3F1),
    surfaceContainerHigh = Color(0xFFE9EEEB),
    surfaceContainerHighest = Color(0xFFE2E8E4),
    outline = Color(0xFF78847E),
    outlineVariant = Color(0xFFC9D2CC),
    inverseSurface = Color(0xFF28332D),
    inverseOnSurface = Color(0xFFF2F6F3),
    inversePrimary = Color(0xFF91D5B7),
)

private val LightSpec = AppThemeSpec(LightColors, Color.White, LightColors.surfaceVariant,
    Color(0xFF187A52), Color(0xFFAD5B10), LightColors.error, LightColors.scrim)

private fun tokenSpec(
    dark: Boolean, background: Long, surface: Long, elevated: Long, muted: Long,
    outline: Long, text: Long, secondaryText: Long, accent: Long, onAccent: Long,
    accentContainer: Long, onAccentContainer: Long, success: Long, warning: Long,
    danger: Long, scrim: Long, art: DrawableResource? = null, alpha: Float = 0f,
): AppThemeSpec {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    val colors = base.copy(
        background = Color(background), onBackground = Color(text),
        surface = Color(surface), onSurface = Color(text),
        surfaceVariant = Color(muted), onSurfaceVariant = Color(secondaryText),
        surfaceDim = Color(surface), surfaceBright = Color(elevated),
        surfaceContainerLowest = Color(surface), surfaceContainerLow = Color(surface),
        surfaceContainer = Color(surface), surfaceContainerHigh = Color(elevated),
        surfaceContainerHighest = Color(muted),
        primary = Color(accent), onPrimary = Color(onAccent),
        primaryContainer = Color(accentContainer), onPrimaryContainer = Color(onAccentContainer),
        secondary = Color(accent), onSecondary = Color(onAccent),
        secondaryContainer = Color(accentContainer), onSecondaryContainer = Color(onAccentContainer),
        tertiary = Color(accent), onTertiary = Color(onAccent),
        tertiaryContainer = Color(accentContainer), onTertiaryContainer = Color(onAccentContainer),
        outline = Color(outline), outlineVariant = Color(outline).copy(alpha = 0.5f),
        error = Color(danger), onError = Color(background),
        errorContainer = Color(surface), onErrorContainer = Color(danger), scrim = Color(scrim),
    )
    return AppThemeSpec(colors, Color(elevated), Color(muted), Color(success), Color(warning),
        Color(danger), Color(scrim), art, alpha)
}

private val DarkSpec = tokenSpec(true,
    0xFF0F1418, 0xFF161A1D, 0xFF20262B, 0xFF2B3136,
    0xFF56616A, 0xFFF5F7F8, 0xFFB4BFC7, 0xFF5AC8A8, 0xFF0B211A,
    0xFF375B51, 0xFFF5F7F8, 0xFF63D39B, 0xFFE8B25F, 0xFFFF7B86, 0xB0000000)
private val SeoulDaySpec = tokenSpec(false,
    0xFFF4F9FC, 0xFFFDFEFF, 0xFFFFFFFF, 0xFFE8F2F7,
    0xFFA8C7DA, 0xFF1C3043, 0xFF566D7C, 0xFF4484B4, 0xFFFFFFFF,
    0xFFD8EAF5, 0xFF113652, 0xFF287B5B, 0xFF9A651B, 0xFFB84952, 0x28FFFFFF,
    Res.drawable.seoul_day_app_background, 0.18f)
private val SeoulNightSpec = tokenSpec(true,
    0xFF0C1426, 0xFF15213A, 0xFF1B2A48, 0xFF223352,
    0xFF526B9A, 0xFFF7F9FF, 0xFFCBD5EA, 0xFF7A91D0, 0xFFFFFFFF,
    0xFF2A3E69, 0xFFF7F9FF, 0xFF63D39B, 0xFFF0BF66, 0xFFFF8690, 0xA00A0F2E,
    Res.drawable.seoul_night_app_background, 0.24f)

internal fun appThemeSpec(choice: KeyboardThemeChoice, systemDark: Boolean): AppThemeSpec = when (choice) {
    KeyboardThemeChoice.SYSTEM -> if (systemDark) DarkSpec else LightSpec
    KeyboardThemeChoice.LIGHT -> LightSpec
    KeyboardThemeChoice.DARK -> DarkSpec
    KeyboardThemeChoice.SEOUL_DAY -> SeoulDaySpec
    KeyboardThemeChoice.SEOUL_NIGHT -> SeoulNightSpec
}
internal val LocalAppTheme = staticCompositionLocalOf { LightSpec }

/** Decorative header only; the solid page background remains readable without artwork. */
@Composable
internal fun ThemeBackdrop(spec: AppThemeSpec, modifier: Modifier = Modifier) {
    val art = spec.backgroundArtwork ?: return
    Box(modifier) {
        Image(painterResource(art), contentDescription = null, contentScale = ContentScale.Crop,
            alpha = spec.artworkHeaderAlpha, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            spec.colorScheme.background.copy(alpha = 0.15f), spec.colorScheme.background))))
    }
}

@Composable
internal fun appTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = LocalAppTheme.current.surfaceElevated,
    unfocusedContainerColor = LocalAppTheme.current.surfaceElevated,
    disabledContainerColor = LocalAppTheme.current.surfaceElevated,
    errorContainerColor = LocalAppTheme.current.surfaceElevated,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
internal fun appFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = MaterialTheme.colorScheme.surface,
    labelColor = MaterialTheme.colorScheme.onSurface,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
)
