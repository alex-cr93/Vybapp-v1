package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.model.ThemeMode

@Immutable
data class VybExtendedColors(
    val isDark: Boolean,
    val hairlineBorder: Color,
    val sinpeEmerald: Color,
    val sinpeContainer: Color,
    val sinpeOnContainer: Color,
    val subtleSurface: Color,
    val nestedSurface: Color,
    val secondaryText: Color,
    val chatCanvas: Color,
    val incomingBubbleBg: Color,
    val incomingBubbleBorder: Color,
    val senderBubbleBorder: Color,
    val cardElevationDp: Dp,
    val linkAccent: Color,
    val missedAccent: Color
)

val LocalVybColors = staticCompositionLocalOf {
    VybExtendedColors(
        isDark = false,
        hairlineBorder = LightBorder,
        sinpeEmerald = SinpeEmeraldDark,
        sinpeContainer = SinpeEmeraldTintLight,
        sinpeOnContainer = SinpeEmeraldDark,
        subtleSurface = LightSubtleSurface,
        nestedSurface = LightNestedSurface,
        secondaryText = LightTextSecondary,
        chatCanvas = LightChatCanvas,
        incomingBubbleBg = LightSurface,
        incomingBubbleBorder = LightIncomingBubbleBorder,
        senderBubbleBorder = Color(0x4DFFFFFF),
        cardElevationDp = 2.dp,
        linkAccent = LightLinkAccent,
        missedAccent = VybErrorLight
    )
}

private val VybLightColorScheme = lightColorScheme(
    primary = VybElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = VybDeepIndigo,
    secondary = VybViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF4C1D95),
    tertiary = VybMagenta,
    onTertiary = Color.White,
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = VybErrorLight,
    onError = Color.White
)

private val VybAmoledDarkColorScheme = darkColorScheme(
    primary = VybSkyBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF172554),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = VybViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2E1065),
    onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = VybMagenta,
    onTertiary = Color.White,
    background = AmoledCanvas,
    onBackground = DarkTextPrimary,
    surface = AmoledSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = AmoledBorder,
    outlineVariant = AmoledBorder,
    error = VybErrorDark,
    onError = Color.Black
)

val VybShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VYBTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.AMOLED -> true
    }

    val colorScheme = if (useDark) VybAmoledDarkColorScheme else VybLightColorScheme
    val extendedColors = if (useDark) {
        VybExtendedColors(
            isDark = true,
            hairlineBorder = AmoledBorder,
            sinpeEmerald = SinpeEmeraldBright,
            sinpeContainer = SinpeEmeraldTintDark,
            sinpeOnContainer = Color(0xFFA7F3D0),
            subtleSurface = DarkSubtleSurface,
            nestedSurface = DarkNestedSurface,
            secondaryText = DarkTextSecondary,
            chatCanvas = DarkChatCanvas,
            incomingBubbleBg = AmoledSurfaceVariant,
            incomingBubbleBorder = Color(0x3D6488EA),
            senderBubbleBorder = Color(0x47FFFFFF),
            cardElevationDp = 0.dp,
            linkAccent = DarkLinkAccent,
            missedAccent = VybErrorDark
        )
    } else {
        VybExtendedColors(
            isDark = false,
            hairlineBorder = LightBorder,
            sinpeEmerald = SinpeEmeraldDark,
            sinpeContainer = SinpeEmeraldTintLight,
            sinpeOnContainer = SinpeEmeraldDark,
            subtleSurface = LightSubtleSurface,
            nestedSurface = LightNestedSurface,
            secondaryText = LightTextSecondary,
            chatCanvas = LightChatCanvas,
            incomingBubbleBg = LightSurface,
            incomingBubbleBorder = LightIncomingBubbleBorder,
            senderBubbleBorder = Color(0x4DFFFFFF),
            cardElevationDp = 2.dp,
            linkAccent = LightLinkAccent,
            missedAccent = VybErrorLight
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !useDark
            }
        }
    }

    CompositionLocalProvider(LocalVybColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VybTypography,
            shapes = VybShapes,
            content = content
        )
    }
}
