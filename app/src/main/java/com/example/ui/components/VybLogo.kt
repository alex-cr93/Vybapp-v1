package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.VybDeepIndigo
import com.example.ui.theme.VybElectricBlue
import com.example.ui.theme.VybMagenta
import com.example.ui.theme.VybSkyBlue
import com.example.ui.theme.VybViolet

/**
 * Insignia oscura ("badge"): squircle con degradado profundo de azul oscuro/eléctrico a violeta y magenta,
 * estrella blanca de 4 puntas con centro hueco, órbita inclinada con punto orbital y destellos.
 */
@Composable
fun VybLogoBadge(
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Insignia VYB" }
    ) {
        val w = this.size.width
        val h = this.size.height
        val corner = w * 0.27f

        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(VybDeepIndigo, VybElectricBlue, VybViolet, VybMagenta),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            ),
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(corner, corner)
        )

        drawVybStarAndOrbit(
            w = w,
            h = h,
            starBrush = Brush.linearGradient(listOf(Color.White, Color(0xFFF8FAFC))),
            orbitBrush = Brush.linearGradient(
                listOf(Color.White.copy(alpha = 0.72f), Color.White)
            ),
            dotColor = Color.White,
            sparkleColor = Color.White.copy(alpha = 0.92f)
        )
    }
}

/**
 * Símbolo transparente ("emblem"): estrella de 4 puntas con centro hueco, órbita y destellos
 * en degradado azul eléctrico -> violeta -> magenta.
 */
@Composable
fun VybLogoEmblem(
    size: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Emblema VYB" }
    ) {
        val w = this.size.width
        val h = this.size.height
        val brandBrush = Brush.linearGradient(
            colors = listOf(VybSkyBlue, VybElectricBlue, VybViolet, VybMagenta),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )

        drawVybStarAndOrbit(
            w = w,
            h = h,
            starBrush = brandBrush,
            orbitBrush = brandBrush,
            dotColor = VybSkyBlue,
            sparkleColor = VybMagenta
        )
    }
}

private fun DrawScope.drawVybStarAndOrbit(
    w: Float,
    h: Float,
    starBrush: Brush,
    orbitBrush: Brush,
    dotColor: Color,
    sparkleColor: Color
) {
    val cx = w * 0.5f
    val cy = h * 0.5f

    // Tilted orbit behind/around star
    rotate(degrees = -28f, pivot = Offset(cx, cy)) {
        drawOval(
            brush = orbitBrush,
            topLeft = Offset(cx - w * 0.36f, cy - h * 0.145f),
            size = Size(w * 0.72f, h * 0.29f),
            style = Stroke(width = w * 0.036f, cap = StrokeCap.Round)
        )
        // Orbital dot on upper-right arc
        val dotCenter = Offset(cx + w * 0.31f, cy - h * 0.06f)
        drawCircle(
            color = dotColor,
            radius = w * 0.052f,
            center = dotCenter
        )
    }

    // 4-point star with hollow diamond center (EvenOdd fill)
    val outerR = w * 0.31f
    val waistR = w * 0.11f
    val innerOuterR = w * 0.14f
    val innerWaistR = w * 0.048f

    val starPath = Path().apply {
        fillType = PathFillType.EvenOdd
        // Outer 4-point star
        moveTo(cx, cy - outerR)
        quadraticTo(cx + waistR * 0.45f, cy - waistR * 0.45f, cx + outerR, cy)
        quadraticTo(cx + waistR * 0.45f, cy + waistR * 0.45f, cx, cy + outerR)
        quadraticTo(cx - waistR * 0.45f, cy + waistR * 0.45f, cx - outerR, cy)
        quadraticTo(cx - waistR * 0.45f, cy - waistR * 0.45f, cx, cy - outerR)
        close()

        // Hollow inner 4-point center
        moveTo(cx, cy - innerOuterR)
        quadraticTo(cx + innerWaistR * 0.5f, cy - innerWaistR * 0.5f, cx + innerOuterR, cy)
        quadraticTo(cx + innerWaistR * 0.5f, cy + innerWaistR * 0.5f, cx, cy + innerOuterR)
        quadraticTo(cx - innerWaistR * 0.5f, cy + innerWaistR * 0.5f, cx - innerOuterR, cy)
        quadraticTo(cx - innerWaistR * 0.5f, cy - innerWaistR * 0.5f, cx, cy - innerOuterR)
        close()
    }
    drawPath(path = starPath, brush = starBrush)

    // Small 4-point sparkles
    drawSparkle(Offset(w * 0.25f, h * 0.27f), w * 0.052f, sparkleColor)
    drawSparkle(Offset(w * 0.77f, h * 0.74f), w * 0.042f, sparkleColor)
}

private fun DrawScope.drawSparkle(center: Offset, radius: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(path = p, color = color)
}
