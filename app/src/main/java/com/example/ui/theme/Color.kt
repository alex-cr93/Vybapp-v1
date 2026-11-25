package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// VYB Brand Gradient Tokens (Electric Blue -> Violet -> Magenta)
val VybSkyBlue = Color(0xFF38BDF8)
val VybElectricBlue = Color(0xFF2563EB)
val VybIndigo = Color(0xFF4F46E5)
val VybViolet = Color(0xFF7C3AED)
val VybMagenta = Color(0xFFD946EF)
val VybPink = Color(0xFFEC4899)
val VybDeepIndigo = Color(0xFF0A1028)

// SINPE Móvil & Confirmation Emerald Tokens
val SinpeEmeraldDark = Color(0xFF059669)
val SinpeEmeraldBright = Color(0xFF10B981)
val SinpeEmeraldTintLight = Color(0xFFECFDF5)
val SinpeEmeraldTintDark = Color(0xFF064E3B)

// Light Mode Soft Tonal Surfaces & Hairlines (4-tier separation: Canvas -> Subtle -> White Surface -> Nested Tile)
val LightCanvas = Color(0xFFEEF3FB)
val LightChatCanvas = Color(0xFFE2ECFB)
val LightSurface = Color(0xFFFFFFFF)
val LightSubtleSurface = Color(0xFFF6F9FF)
val LightNestedSurface = Color(0xFFE7EFFC)
val LightSurfaceVariant = Color(0xFFE5EDF9)
val LightBorder = Color(0xFFC0D1EA)
val LightIncomingBubbleBorder = Color(0xFFB3C8E8)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)

// Deep Midnight Navy Dark Mode Tonal Surfaces (4-tier separation: Canvas -> Surface -> Subtle -> Nested Tile)
val AmoledCanvas = Color(0xFF060A17)
val DarkChatCanvas = Color(0xFF050914)
val AmoledSurface = Color(0xFF0E162B)
val DarkSubtleSurface = Color(0xFF131D38)
val DarkNestedSurface = Color(0xFF192646)
val AmoledSurfaceVariant = Color(0xFF15203B)
val AmoledBorder = Color(0x367DA2FF) // luminous slate-blue hairline (~21% alpha)
val DarkTextPrimary = Color(0xFFF8FAFC)
val DarkTextSecondary = Color(0xFF9BB0D4)

// Status & Contrast-Safe Accent Tokens
val VybErrorLight = Color(0xFFDC2626)
val VybErrorDark = Color(0xFFF87171)
val LightLinkAccent = Color(0xFF1D4ED8)
val DarkLinkAccent = VybSkyBlue

val VybGradientBrush = Brush.linearGradient(
    colors = listOf(VybSkyBlue, VybElectricBlue, VybViolet, VybMagenta)
)

val VybSenderBubbleBrush = Brush.linearGradient(
    colors = listOf(VybElectricBlue, VybIndigo, VybViolet)
)

val VybLikeGradientBrush = Brush.linearGradient(
    colors = listOf(VybPink, Color(0xFF9333EA))
)

val VybBadgeGradientBrush = Brush.linearGradient(
    colors = listOf(VybDeepIndigo, VybElectricBlue, VybViolet, VybMagenta)
)
