package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.ui.theme.LocalVybColors
import com.example.ui.theme.VybMonoTabularStyle
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class VybBombVisualStage {
    STAGE_1_ACTIVE,
    STAGE_2_CRITICAL,
    STAGE_3_VAPORIZE
}

data class BombPerimeterPoint(
    val x: Float,
    val y: Float,
    val tangentAngleRad: Float
)

fun resolveBombVisualStage(
    isTriggered: Boolean,
    remainingSec: Float
): VybBombVisualStage {
    if (!isTriggered) return VybBombVisualStage.STAGE_1_ACTIVE
    return when {
        remainingSec <= 0f -> VybBombVisualStage.STAGE_3_VAPORIZE
        remainingSec <= 2.0f -> VybBombVisualStage.STAGE_2_CRITICAL
        else -> VybBombVisualStage.STAGE_1_ACTIVE
    }
}

fun resolveBombUiRemainingSeconds(
    isTriggered: Boolean,
    triggeredAtSec: Long?,
    durationSec: Long,
    nowMs: Long = System.currentTimeMillis()
): Float {
    val safeDuration = durationSec.coerceAtLeast(1L).toFloat()
    if (!isTriggered || triggeredAtSec == null) return safeDuration
    val diffSec = (nowMs / 1000.0 - triggeredAtSec.toDouble()).toFloat()
    // Clock skew tolerance matching ChatRepository trigger behavior
    val elapsed = if (diffSec in -15f..1.8f) 0f else diffSec.coerceAtLeast(0f)
    return (safeDuration - elapsed).coerceAtLeast(0f)
}

/**
 * Exact rounded-rectangle perimeter tracker with +0.28 start offset so the fuse tip
 * travels smoothly along the right & bottom edges toward the bottom-left bomb icon.
 */
fun computeRoundedRectPerimeterPoint(
    width: Float,
    height: Float,
    radius: Float,
    progress01: Float
): BombPerimeterPoint {
    if (width <= 0f || height <= 0f) return BombPerimeterPoint(0f, 0f, 0f)
    val r = min(radius, min(width / 2f, height / 2f)).coerceAtLeast(0f)
    val straightW = max(0f, width - 2f * r)
    val straightH = max(0f, height - 2f * r)
    val arcLen = ((PI / 2.0) * r).toFloat()
    val totalPerimeter = (2f * (straightW + straightH) + 4f * arcLen).coerceAtLeast(1f)

    val norm = ((progress01 % 1f) + 1f) % 1f
    val shifted = (norm + 0.28f) % 1f
    var d = shifted * totalPerimeter

    if (d <= straightW) {
        return BombPerimeterPoint(r + d, 0f, 0f)
    }
    d -= straightW

    if (d <= arcLen && r > 0f) {
        val a = (-PI / 2.0 + d / r).toFloat()
        return BombPerimeterPoint(
            x = width - r + cos(a) * r,
            y = r + sin(a) * r,
            tangentAngleRad = (a + PI / 2.0).toFloat()
        )
    }
    d -= arcLen

    if (d <= straightH) {
        return BombPerimeterPoint(width, r + d, (PI / 2.0).toFloat())
    }
    d -= straightH

    if (d <= arcLen && r > 0f) {
        val a = (d / r)
        return BombPerimeterPoint(
            x = width - r + cos(a) * r,
            y = height - r + sin(a) * r,
            tangentAngleRad = (a + PI / 2.0).toFloat()
        )
    }
    d -= arcLen

    if (d <= straightW) {
        return BombPerimeterPoint(width - r - d, height, PI.toFloat())
    }
    d -= straightW

    if (d <= arcLen && r > 0f) {
        val a = (PI / 2.0 + d / r).toFloat()
        return BombPerimeterPoint(
            x = r + cos(a) * r,
            y = height - r + sin(a) * r,
            tangentAngleRad = (a + PI / 2.0).toFloat()
        )
    }
    d -= arcLen

    if (d <= straightH) {
        return BombPerimeterPoint(0f, height - r - d, (-PI / 2.0).toFloat())
    }
    d -= straightH

    val a = if (r > 0f) (PI + d / r).toFloat() else PI.toFloat()
    return BombPerimeterPoint(
        x = r + cos(a) * r,
        y = r + sin(a) * r,
        tangentAngleRad = (a + PI / 2.0).toFloat()
    )
}

private enum class BombParticleKind {
    FUSE_SPARK,
    GLASS_FLECK,
    EMBER,
    FIRE_SPARK,
    SMOKE
}

private class BombParticle(
    val kind: BombParticleKind,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val gravity: Float,
    val drag: Float,
    val size: Float,
    val maxSize: Float,
    val life: Float,
    var age: Float,
    val color: Color,
    val smokeRgb: Color = color,
    val wobblePhase: Float = 0f,
    var rotationDeg: Float = 0f,
    val vRotDeg: Float = 0f
)

private class BombShockwave(
    val x: Float,
    val y: Float,
    var radius: Float,
    val maxRadius: Float,
    var alpha: Float
)

private class BombFxSimulation {
    val particles = ArrayList<BombParticle>(200)
    val shockwaves = ArrayList<BombShockwave>(2)
    var sparkAccumulator = 0f
    var crackAccumulator = 0f
    var ambientSparkAccumulator = 0f

    fun spawnFuseSpark(x: Float, y: Float, isStage2: Boolean, density: Float) {
        val count = if (isStage2) 2 else 1
        val colorsStage1 = arrayOf(Color(0xFFFFF59D), Color(0xFF00E5FF))
        val colorsStage2 = arrayOf(Color(0xFFFF2A85), Color(0xFFFFD54F))
        for (i in 0 until count) {
            val angle = Random.nextFloat() * (2f * PI.toFloat())
            val speed = (if (isStage2) 32f else 18f) + Random.nextFloat() * 44f
            particles.add(
                BombParticle(
                    kind = BombParticleKind.FUSE_SPARK,
                    x = x + (Random.nextFloat() - 0.5f) * 3f * density,
                    y = y + (Random.nextFloat() - 0.5f) * 3f * density,
                    vx = cos(angle) * speed * density,
                    vy = (sin(angle) * speed - 18f) * density,
                    gravity = 92f * density,
                    drag = 0.93f,
                    size = (1.3f + Random.nextFloat() * 1.9f) * density,
                    maxSize = 0f,
                    life = 0.24f + Random.nextFloat() * 0.24f,
                    age = 0f,
                    color = if (isStage2) {
                        colorsStage2[Random.nextInt(colorsStage2.size)]
                    } else {
                        colorsStage1[Random.nextInt(colorsStage1.size)]
                    }
                )
            )
        }
    }

    fun spawnGlassFleck(x: Float, y: Float, density: Float) {
        particles.add(
            BombParticle(
                kind = BombParticleKind.GLASS_FLECK,
                x = x,
                y = y,
                vx = (12f + Random.nextFloat() * 30f) * density,
                vy = (10f + Random.nextFloat() * 40f) * density,
                gravity = 135f * density,
                drag = 0.97f,
                size = (1.6f + Random.nextFloat() * 2.2f) * density,
                maxSize = 0f,
                life = 0.45f + Random.nextFloat() * 0.35f,
                age = 0f,
                color = if (Random.nextFloat() > 0.35f) Color(0xFFE0F7FA) else Color(0xFFFF80AB),
                rotationDeg = Random.nextFloat() * 360f,
                vRotDeg = (Random.nextFloat() - 0.5f) * 520f
            )
        )
    }

    fun triggerStage3Explosion(boxW: Float, boxH: Float, density: Float) {
        if (boxW <= 0f || boxH <= 0f) return
        val centerX = boxW * 0.5f
        val centerY = boxH * 0.5f
        shockwaves.add(
            BombShockwave(
                x = centerX,
                y = centerY,
                radius = 10f * density,
                maxRadius = max(boxW, 170f * density) * 0.85f,
                alpha = 0.95f
            )
        )

        // A) ~95 Golden embers floating upward
        repeat(95) {
            val px = Random.nextFloat() * boxW
            val py = Random.nextFloat() * boxH
            val fromRightFactor = (px / boxW).coerceIn(0f, 1f)
            particles.add(
                BombParticle(
                    kind = BombParticleKind.EMBER,
                    x = px,
                    y = py,
                    vx = ((Random.nextFloat() - 0.55f) * 38f + fromRightFactor * 16f) * density,
                    vy = (-18f - Random.nextFloat() * 52f) * density,
                    gravity = -14f * density,
                    drag = 0.97f,
                    size = (1.6f + Random.nextFloat() * 2.8f) * density,
                    maxSize = 0f,
                    life = 0.95f + Random.nextFloat() * 0.75f,
                    age = Random.nextFloat() * 0.14f,
                    color = if (Random.nextFloat() > 0.35f) Color(0xFFFFD54F) else Color(0xFFFF9F1C),
                    wobblePhase = Random.nextFloat() * (2f * PI.toFloat())
                )
            )
        }

        // B) ~42 Fast fire sparks
        repeat(42) {
            val px = Random.nextFloat() * boxW
            val py = Random.nextFloat() * boxH
            val angle = ((Random.nextFloat() - 0.5f) * PI * 1.4 - PI * 0.25).toFloat()
            val speed = (55f + Random.nextFloat() * 95f) * density
            particles.add(
                BombParticle(
                    kind = BombParticleKind.FIRE_SPARK,
                    x = px,
                    y = py,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed - 15f * density,
                    gravity = 45f * density,
                    drag = 0.94f,
                    size = (1.4f + Random.nextFloat() * 1.6f) * density,
                    maxSize = 0f,
                    life = 0.55f + Random.nextFloat() * 0.45f,
                    age = 0f,
                    color = if (Random.nextFloat() > 0.5f) Color(0xFFFFF59D) else Color(0xFFFF5E00)
                )
            )
        }

        // C) ~24 Soft violet/purple smoke plumes
        repeat(24) {
            val px = 12f * density + Random.nextFloat() * max(10f, boxW - 24f * density)
            val py = 10f * density + Random.nextFloat() * max(10f, boxH - 20f * density)
            val smokeColor = if (Random.nextFloat() > 0.45f) Color(0xFFA855F7) else Color(0xFF7C4DFF)
            particles.add(
                BombParticle(
                    kind = BombParticleKind.SMOKE,
                    x = px,
                    y = py,
                    vx = (Random.nextFloat() - 0.5f) * 24f * density,
                    vy = (-16f - Random.nextFloat() * 28f) * density,
                    gravity = -6f * density,
                    drag = 0.96f,
                    size = (14f + Random.nextFloat() * 18f) * density,
                    maxSize = (34f + Random.nextFloat() * 22f) * density,
                    life = 1.1f + Random.nextFloat() * 0.65f,
                    age = Random.nextFloat() * 0.12f,
                    color = smokeColor,
                    smokeRgb = smokeColor
                )
            )
        }
    }

    fun step(dt: Float, density: Float) {
        val safeDt = dt.coerceIn(0.001f, 0.05f)
        for (i in shockwaves.indices.reversed()) {
            val sw = shockwaves[i]
            sw.radius += (sw.maxRadius - sw.radius) * (1f - kotlin.math.exp(-9f * safeDt))
            sw.alpha -= safeDt * 1.85f
            if (sw.alpha <= 0f) {
                shockwaves.removeAt(i)
            }
        }

        for (i in particles.indices.reversed()) {
            val p = particles[i]
            p.age += safeDt
            if (p.age >= p.life) {
                particles.removeAt(i)
                continue
            }
            val dragFactor = p.drag.toDouble().pow((safeDt * 60f).toDouble()).toFloat()
            p.vx *= dragFactor
            p.vy = p.vy * dragFactor + p.gravity * safeDt
            if (p.kind == BombParticleKind.EMBER) {
                p.x += (p.vx + sin(p.age * 7f + p.wobblePhase) * 14f * density) * safeDt
            } else {
                p.x += p.vx * safeDt
            }
            p.y += p.vy * safeDt
            if (p.kind == BombParticleKind.GLASS_FLECK) {
                p.rotationDeg += p.vRotDeg * safeDt
            }
        }
    }
}

/**
 * 3-stage "Mecha y Vaporización VYB" bomb message container + Ghost Pill transition.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VybBombMessageContainer(
    chatId: String,
    message: ChatMessage,
    isMine: Boolean,
    isSpotlightPreview: Boolean = false,
    onTriggeredExpired: () -> Unit,
    onVaporizeStarted: () -> Unit,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit,
    content: @Composable (
        stage: VybBombVisualStage,
        remainingSecFloat: Float,
        isTriggered: Boolean
    ) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current.density
    val durationSec = message.bombDurationSec
    val triggeredAt = message.bombTriggeredAt
    val isInitialGhostTombstone = message.messageType == "bomb_destroyed_ghost"

    if (durationSec == null && !isInitialGhostTombstone) {
        Box(
            modifier = Modifier.combinedClickable(
                onClick = {},
                onDoubleClick = onDoubleTap,
                onLongClick = onLongPress
            )
        ) {
            content(VybBombVisualStage.STAGE_1_ACTIVE, 0f, false)
        }
        return
    }

    if (isSpotlightPreview) {
        val previewRem = resolveBombUiRemainingSeconds(
            isTriggered = triggeredAt != null,
            triggeredAtSec = triggeredAt?.seconds,
            durationSec = durationSec ?: 5L
        )
        val previewStage = resolveBombVisualStage(triggeredAt != null, previewRem)
            .let { if (it == VybBombVisualStage.STAGE_3_VAPORIZE) VybBombVisualStage.STAGE_2_CRITICAL else it }
        VybBombSurfaceWrapper(
            isMine = isMine,
            stage = previewStage,
            remainingSecFloat = previewRem,
            durationSec = (durationSec ?: 5L).toFloat(),
            isTriggered = triggeredAt != null,
            jitterX = 0f,
            jitterY = 0f,
            crackProgress = if (previewStage == VybBombVisualStage.STAGE_2_CRITICAL) 1f else 0f,
            dissolveProgress = 0f,
            sim = null,
            fxTick = 0L
        ) {
            content(previewStage, previewRem, triggeredAt != null)
        }
        return
    }

    val safeDuration = (durationSec ?: 5L).coerceAtLeast(1L).toFloat()
    val isTriggered = triggeredAt != null

    var remainingSecFloat by remember(message.id, triggeredAt?.seconds, durationSec, isInitialGhostTombstone) {
        mutableFloatStateOf(
            if (isInitialGhostTombstone) 0f
            else resolveBombUiRemainingSeconds(
                isTriggered = isTriggered,
                triggeredAtSec = triggeredAt?.seconds,
                durationSec = durationSec ?: 5L
            )
        )
    }

    var isVaporizing by remember(message.id) { mutableStateOf(false) }
    var showGhostPill by remember(message.id) { mutableStateOf(isInitialGhostTombstone) }
    val dissolveProgress = remember(message.id) { Animatable(0f) }
    var bubbleSize by remember(message.id) { mutableStateOf(IntSize.Zero) }
    var fxTick by remember(message.id) { mutableLongStateOf(0L) }
    var jitterX by remember(message.id) { mutableFloatStateOf(0f) }
    var jitterY by remember(message.id) { mutableFloatStateOf(0f) }

    val sim = remember(message.id) { BombFxSimulation() }

    // If a tombstone replaces a still-resting message (e.g. peer expired earlier while offline or opening), show ghost
    LaunchedEffect(isInitialGhostTombstone) {
        if (isInitialGhostTombstone && !isVaporizing) {
            showGhostPill = true
        }
    }

    val currentStage = when {
        isVaporizing || remainingSecFloat <= 0f && isTriggered -> VybBombVisualStage.STAGE_3_VAPORIZE
        !isTriggered -> VybBombVisualStage.STAGE_1_ACTIVE
        remainingSecFloat <= 2.0f -> VybBombVisualStage.STAGE_2_CRITICAL
        else -> VybBombVisualStage.STAGE_1_ACTIVE
    }

    val crackProgress by animateFloatAsState(
        targetValue = if (currentStage == VybBombVisualStage.STAGE_2_CRITICAL) 1f else 0f,
        animationSpec = tween(durationMillis = 520, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
        label = "bomb_crack_reveal"
    )

    // 60fps continuous fuse countdown & particle simulation
    LaunchedEffect(message.id, isTriggered, triggeredAt?.seconds, safeDuration, isInitialGhostTombstone) {
        if (isInitialGhostTombstone || isVaporizing) {
            return@LaunchedEffect
        }
        val initialRem = resolveBombUiRemainingSeconds(
            isTriggered = isTriggered,
            triggeredAtSec = triggeredAt?.seconds,
            durationSec = durationSec ?: 5L
        )
        remainingSecFloat = initialRem

        var lastFrameNs = System.nanoTime()
        val triggerStartRem = initialRem

        if (!isTriggered) {
            // Armed state (Stage 1): keep subtle ambient sparks twinkling near the fuse icon
            while (true) {
                val frameNs = withFrameNanos { it }
                val dt = ((frameNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameNs = frameNs
                if (bubbleSize.width > 0 && bubbleSize.height > 0) {
                    sim.ambientSparkAccumulator += dt * 8f
                    while (sim.ambientSparkAccumulator >= 1f) {
                        sim.ambientSparkAccumulator -= 1f
                        val rPx = 21f * density
                        val pt = computeRoundedRectPerimeterPoint(
                            width = bubbleSize.width.toFloat(),
                            height = bubbleSize.height.toFloat(),
                            radius = rPx,
                            progress01 = 0f
                        )
                        sim.spawnFuseSpark(pt.x, pt.y, isStage2 = false, density = density)
                    }
                }
                sim.step(dt, density)
                fxTick++
            }
        } else {
            val countdownStartNs = lastFrameNs
            while (remainingSecFloat > 0f && !isVaporizing) {
                val frameNs = withFrameNanos { it }
                val dt = ((frameNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastFrameNs = frameNs

                val elapsedSinceStart = ((frameNs - countdownStartNs) / 1_000_000_000.0).toFloat()
                remainingSecFloat = (triggerStartRem - elapsedSinceStart).coerceAtLeast(0f)

                val inStage2 = remainingSecFloat > 0f && remainingSecFloat <= 2.0f
                if (inStage2) {
                    val urgency = (1f - (remainingSecFloat / 2.0f)).coerceIn(0f, 1f)
                    val ampPx = (0.7f + urgency * 1.9f) * density
                    jitterX = (Random.nextFloat() - 0.5f) * 2f * ampPx
                    jitterY = (Random.nextFloat() - 0.5f) * 2f * ampPx
                } else {
                    jitterX = 0f
                    jitterY = 0f
                }

                if (bubbleSize.width > 0 && bubbleSize.height > 0 && remainingSecFloat > 0f) {
                    val ratio = (remainingSecFloat / safeDuration).coerceIn(0f, 1f)
                    val pt = computeRoundedRectPerimeterPoint(
                        width = bubbleSize.width.toFloat(),
                        height = bubbleSize.height.toFloat(),
                        radius = 21f * density,
                        progress01 = 1f - ratio
                    )
                    sim.sparkAccumulator += dt * (if (inStage2) 58f else 34f)
                    while (sim.sparkAccumulator >= 1f) {
                        sim.sparkAccumulator -= 1f
                        sim.spawnFuseSpark(pt.x, pt.y, isStage2 = inStage2, density = density)
                    }

                    if (inStage2) {
                        sim.crackAccumulator += dt * 16f
                        while (sim.crackAccumulator >= 1f) {
                            sim.crackAccumulator -= 1f
                            val rx = bubbleSize.width - (4f + Random.nextFloat() * 14f) * density
                            val ry = (10f * density) + Random.nextFloat() * max(8f, bubbleSize.height - 20f * density)
                            sim.spawnGlassFleck(rx, ry, density)
                        }
                    }
                }

                sim.step(dt, density)
                fxTick++
            }
        }
    }

    // Trigger Stage 3 Vaporization -> Ghost Pill -> Firestore Delete
    LaunchedEffect(message.id, isTriggered, remainingSecFloat <= 0f) {
        if (!isTriggered || remainingSecFloat > 0f || isVaporizing || isInitialGhostTombstone) return@LaunchedEffect
        isVaporizing = true
        jitterX = 0f
        jitterY = 0f
        onVaporizeStarted()
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)

        val w = if (bubbleSize.width > 0) bubbleSize.width.toFloat() else 230f * density
        val h = if (bubbleSize.height > 0) bubbleSize.height.toFloat() else 72f * density
        sim.triggerStage3Explosion(w, h, density)

        // Start dissolve & particle animation concurrently
        dissolveProgress.snapTo(0f)
    }

    // Step particles smoothly through Stage 3 until all embers/smoke fade
    LaunchedEffect(isVaporizing) {
        if (!isVaporizing) return@LaunchedEffect
        var lastNs = System.nanoTime()
        var elapsedStage3 = 0f
        var triggeredGhost = false
        var triggeredDelete = false

        while (elapsedStage3 < 1.85f || sim.particles.isNotEmpty() || sim.shockwaves.isNotEmpty()) {
            val frameNs = withFrameNanos { it }
            val dt = ((frameNs - lastNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
            lastNs = frameNs
            elapsedStage3 += dt

            val dissolveFrac = (elapsedStage3 / 0.62f).coerceIn(0f, 1f)
            // Cubic ease out
            val eased = 1f - (1f - dissolveFrac).pow(3f)
            dissolveProgress.snapTo(eased)

            if (!triggeredGhost && elapsedStage3 >= 0.46f) {
                triggeredGhost = true
                showGhostPill = true
            }
            if (!triggeredDelete && elapsedStage3 >= 1.50f) {
                triggeredDelete = true
                onTriggeredExpired()
            }

            sim.step(dt, density)
            fxTick++
        }
        if (!triggeredDelete) {
            onTriggeredExpired()
        }
    }

    Box(
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .combinedClickable(
                enabled = !isVaporizing && !showGhostPill,
                onClick = {},
                onDoubleClick = onDoubleTap,
                onLongClick = onLongPress
            )
    ) {
        if (!showGhostPill || dissolveProgress.value < 0.99f) {
            Box(
                modifier = Modifier
                    .onSizeChanged { bubbleSize = it }
                    .alpha(if (showGhostPill && dissolveProgress.value >= 0.99f) 0f else 1f)
            ) {
                VybBombSurfaceWrapper(
                    isMine = isMine,
                    stage = currentStage,
                    remainingSecFloat = remainingSecFloat,
                    durationSec = safeDuration,
                    isTriggered = isTriggered,
                    jitterX = jitterX,
                    jitterY = jitterY,
                    crackProgress = crackProgress,
                    dissolveProgress = dissolveProgress.value,
                    sim = sim,
                    fxTick = fxTick
                ) {
                    content(currentStage, remainingSecFloat, isTriggered)
                }
            }
        }

        if (showGhostPill) {
            VybDestroyedBombGhostPill(
                isMine = isMine,
                createdAt = message.createdAt,
                animateIn = !isInitialGhostTombstone || isVaporizing,
                modifier = Modifier.testTag("msg_bomb_destroyed_${message.id}")
            )
        }
    }
}

@Composable
private fun VybBombSurfaceWrapper(
    isMine: Boolean,
    stage: VybBombVisualStage,
    remainingSecFloat: Float,
    durationSec: Float,
    isTriggered: Boolean,
    jitterX: Float,
    jitterY: Float,
    crackProgress: Float,
    dissolveProgress: Float,
    sim: BombFxSimulation?,
    fxTick: Long,
    content: @Composable BoxScope.() -> Unit
) {
    val vybColors = LocalVybColors.current
    val density = LocalDensity.current.density
    val isStage2 = stage == VybBombVisualStage.STAGE_2_CRITICAL || stage == VybBombVisualStage.STAGE_3_VAPORIZE

    val stageBlend by animateFloatAsState(
        targetValue = if (isStage2) 1f else 0f,
        animationSpec = tween(durationMillis = 380),
        label = "bomb_stage_blend"
    )

    val borderStartColor by animateColorAsState(
        targetValue = if (isStage2) Color(0xFFFF5E00) else Color(0xFFFF9F1C),
        animationSpec = tween(380),
        label = "bomb_border_start"
    )
    val borderMidColor by animateColorAsState(
        targetValue = if (isStage2) Color(0xFFFF2A85) else Color(0xFFB86BFF),
        animationSpec = tween(380),
        label = "bomb_border_mid"
    )
    val borderEndColor by animateColorAsState(
        targetValue = if (isStage2) Color(0xFFB5179E) else Color(0xFF00E5FF),
        animationSpec = tween(380),
        label = "bomb_border_end"
    )

    val bgAlphaBoost = if (vybColors.isDark) 0f else 0.09f
    val bgTopLeft by animateColorAsState(
        targetValue = if (isStage2) {
            Color(0xFF40183A).copy(alpha = (0.86f + bgAlphaBoost).coerceAtMost(0.96f))
        } else {
            Color(0xFF252052).copy(alpha = (0.84f + bgAlphaBoost).coerceAtMost(0.95f))
        },
        animationSpec = tween(420),
        label = "bomb_bg_tl"
    )
    val bgMid by animateColorAsState(
        targetValue = if (isStage2) {
            Color(0xFF2E123E).copy(alpha = (0.83f + bgAlphaBoost).coerceAtMost(0.95f))
        } else {
            Color(0xFF181D44).copy(alpha = (0.79f + bgAlphaBoost).coerceAtMost(0.93f))
        },
        animationSpec = tween(420),
        label = "bomb_bg_mid"
    )
    val bgBottomRight by animateColorAsState(
        targetValue = if (isStage2) {
            Color(0xFF481438).copy(alpha = (0.89f + bgAlphaBoost).coerceAtMost(0.96f))
        } else {
            Color(0xFF12294C).copy(alpha = (0.84f + bgAlphaBoost).coerceAtMost(0.95f))
        },
        animationSpec = tween(420),
        label = "bomb_bg_br"
    )

    val heatTransition = rememberInfiniteTransition(label = "thermal_shimmer")
    val heatPhase by heatTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heat_phase"
    )

    val cornerBottomLeft = if (isMine) 22.dp else 8.dp
    val cornerBottomRight = if (isMine) 8.dp else 22.dp
    val bubbleShape = RoundedCornerShape(
        topStart = 22.dp,
        topEnd = 22.dp,
        bottomStart = cornerBottomLeft,
        bottomEnd = cornerBottomRight
    )

    val isVaporizing = stage == VybBombVisualStage.STAGE_3_VAPORIZE && dissolveProgress > 0f
    val dissolveShiftX = if (isVaporizing) -16f * density * dissolveProgress else 0f
    val dissolveShiftY = if (isVaporizing) -10f * density * dissolveProgress else 0f
    val dissolveScale = if (isVaporizing) {
        if (dissolveProgress < 0.35f) 1f + 0.014f * (dissolveProgress / 0.35f)
        else 1.014f - 0.064f * ((dissolveProgress - 0.35f) / 0.65f)
    } else 1f
    val dissolveAlpha = if (isVaporizing) (1f - dissolveProgress * 1.05f).coerceIn(0f, 1f) else 1f

    Box(modifier = Modifier.widthIn(min = 216.dp, max = 300.dp)) {
        // Bubble body with Stage 1/2 glow, glassmorphic fill, glare, thermal wave, right cracks & Stage 3 dissolve mask
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = jitterX + dissolveShiftX
                    translationY = jitterY + dissolveShiftY
                    scaleX = dissolveScale
                    scaleY = dissolveScale
                    alpha = dissolveAlpha
                    if (isVaporizing) {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                }
                .drawWithContent {
                    drawContent()
                    if (isVaporizing) {
                        // Subtle brightness surge during vaporization
                        val flareAlpha = if (dissolveProgress < 0.55f) {
                            (dissolveProgress / 0.55f) * 0.28f
                        } else {
                            ((1f - dissolveProgress) / 0.45f) * 0.28f
                        }
                        if (flareAlpha > 0.01f) {
                            drawRect(
                                color = Color(0xFFFFF59D).copy(alpha = flareAlpha),
                                blendMode = BlendMode.SrcAtop
                            )
                        }

                        // Right-to-left dissolution alpha wipe
                        val solidStop = (0.85f - dissolveProgress * 1.05f).coerceIn(0f, 1f)
                        val clearStop = (1.0f - dissolveProgress * 1.08f).coerceIn(0.001f, 1f)
                        val maskBrush = if (clearStop <= solidStop + 0.01f) {
                            Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        } else {
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Black,
                                    solidStop to Color.Black,
                                    clearStop to Color.Transparent,
                                    1f to Color.Transparent
                                )
                            )
                        }
                        drawRect(brush = maskBrush, blendMode = BlendMode.DstIn)
                    }
                }
                .drawBehind {
                    val cornerPx = 22.dp.toPx()
                    val blPx = cornerBottomLeft.toPx()
                    val brPx = cornerBottomRight.toPx()
                    val rr = RoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        topLeftCornerRadius = CornerRadius(cornerPx, cornerPx),
                        topRightCornerRadius = CornerRadius(cornerPx, cornerPx),
                        bottomRightCornerRadius = CornerRadius(brPx, brPx),
                        bottomLeftCornerRadius = CornerRadius(blPx, blPx)
                    )
                    val path = Path().apply { addRoundRect(rr) }

                    // Soft dual-tone outer aura shadows
                    val leftHalo = if (isStage2) Color(0xFFFF5E00) else Color(0xFFFF9F1C)
                    val rightHalo = if (isStage2) Color(0xFFFF2A85) else Color(0xFF00E5FF)
                    val haloAlpha = (if (isStage2) 0.28f else 0.16f) * (1f - dissolveProgress)
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            listOf(leftHalo.copy(alpha = haloAlpha), rightHalo.copy(alpha = haloAlpha))
                        ),
                        style = Stroke(width = (if (isStage2) 8.dp else 6.dp).toPx())
                    )
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            listOf(leftHalo.copy(alpha = haloAlpha * 0.45f), rightHalo.copy(alpha = haloAlpha * 0.45f))
                        ),
                        style = Stroke(width = 13.dp.toPx())
                    )

                    // Glassmorphic backdrop gradient
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            colors = listOf(bgTopLeft, bgMid, bgBottomRight),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        )
                    )

                    // Stage 2 thermal shimmer radial haze inside bubble
                    if (stageBlend > 0.01f) {
                        val pulseShiftX = (heatPhase - 0.5f) * size.width * 0.14f
                        val pulseShiftY = (0.5f - heatPhase) * size.height * 0.12f
                        val center = Offset(size.width * 0.45f + pulseShiftX, size.height * 0.55f + pulseShiftY)
                        drawPath(
                            path = path,
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFFF6E14).copy(alpha = 0.34f * stageBlend * (0.65f + 0.35f * heatPhase)),
                                    Color(0xFFFF2A85).copy(alpha = 0.22f * stageBlend),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = max(size.width, size.height) * 0.72f
                            )
                        )
                    }

                    // Luminous dual-tone border stroke (1.8dp)
                    drawPath(
                        path = path,
                        brush = Brush.linearGradient(
                            colors = listOf(borderStartColor, borderMidColor, borderEndColor),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height * 0.25f)
                        ),
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                }
                .clip(bubbleShape)
                .drawWithContent {
                    drawContent()

                    // Diagonal top-left specular glass glare (118 deg)
                    drawRect(
                        brush = Brush.linearGradient(
                            colorStops = arrayOf(
                                0f to Color.White.copy(alpha = 0.16f),
                                0.44f to Color.White.copy(alpha = 0.04f),
                                0.45f to Color.Transparent,
                                1f to Color.Transparent
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width * 0.85f, size.height * 0.95f)
                        )
                    )

                    // Stage 2 subtle wave shimmer strips + right-edge crystal fracture cracks
                    if (stageBlend > 0.01f) {
                        val w = size.width
                        val h = size.height
                        val shimmerY = h * (0.28f + heatPhase * 0.44f)
                        drawLine(
                            brush = Brush.horizontalGradient(
                                listOf(Color.Transparent, Color(0xFFFF9F1C).copy(alpha = 0.16f * stageBlend), Color.Transparent)
                            ),
                            start = Offset(0f, shimmerY),
                            end = Offset(w, shimmerY - 8.dp.toPx()),
                            strokeWidth = 6.dp.toPx()
                        )
                    }

                    if (crackProgress > 0.01f) {
                        drawRightEdgeGlassCracks(
                            width = size.width,
                            height = size.height,
                            reveal = crackProgress
                        )
                    }
                }
        ) {
            Box(modifier = Modifier.padding(horizontal = 15.dp, vertical = 11.dp)) {
                content()
            }
        }

        // Unclipped overlay Canvas for perimeter burning fuse + live fuse sparks + Stage 2 glass flecks + Stage 3 shockwave/embers/smoke
        if (!isVaporizing || (sim != null && (sim.particles.isNotEmpty() || sim.shockwaves.isNotEmpty()))) {
            Canvas(modifier = Modifier.matchParentSize()) {
                // Read fxTick so 60fps simulation redraws cleanly
                @Suppress("UNUSED_VARIABLE")
                val tick = fxTick

                if (!isVaporizing) {
                    val ratio = if (isTriggered) (remainingSecFloat / durationSec).coerceIn(0f, 1f) else 1f
                    drawPerimeterBurningFuse(
                        width = size.width,
                        height = size.height,
                        radiusPx = 21.dp.toPx(),
                        ratio = ratio,
                        isTriggered = isTriggered,
                        isStage2 = isStage2
                    )
                }

                if (sim != null) {
                    drawBombFxSimulation(sim)
                }
            }
        }
    }
}

private fun DrawScope.drawRightEdgeGlassCracks(
    width: Float,
    height: Float,
    reveal: Float
) {
    val crackRegionW = min(96.dp.toPx(), width * 0.46f)
    val ox = width - crackRegionW
    val sx = crackRegionW / 96f
    val sy = height / 76f

    val path1 = Path().apply {
        moveTo(ox + 95f * sx, 14f * sy)
        lineTo(ox + 78f * sx, 24f * sy)
        lineTo(ox + 83f * sx, 34f * sy)
        lineTo(ox + 67f * sx, 45f * sy)
        lineTo(ox + 73f * sx, 56f * sy)
        lineTo(ox + 58f * sx, 68f * sy)
    }
    val path2 = Path().apply {
        moveTo(ox + 78f * sx, 24f * sy)
        lineTo(ox + 64f * sx, 21f * sy)
        lineTo(ox + 55f * sx, 29f * sy)
    }
    val path3 = Path().apply {
        moveTo(ox + 83f * sx, 34f * sy)
        lineTo(ox + 94f * sx, 42f * sy)
        moveTo(ox + 67f * sx, 45f * sy)
        lineTo(ox + 54f * sx, 44f * sy)
        lineTo(ox + 47f * sx, 51f * sy)
    }

    drawAnimatedCrack(path1, reveal.coerceIn(0f, 1f), 1.4.dp.toPx())
    val p2 = ((reveal - 0.18f) / 0.82f).coerceIn(0f, 1f)
    if (p2 > 0f) drawAnimatedCrack(path2, p2, 1.05.dp.toPx())
    val p3 = ((reveal - 0.34f) / 0.66f).coerceIn(0f, 1f)
    if (p3 > 0f) drawAnimatedCrack(path3, p3, 1.0.dp.toPx())
}

private fun DrawScope.drawAnimatedCrack(fullPath: Path, progress: Float, strokePx: Float) {
    val pm = PathMeasure()
    pm.setPath(fullPath, false)
    val len = pm.length
    if (len <= 0f) return
    val partial = Path()
    pm.getSegment(0f, len * progress, partial, true)

    // Magenta glow underneath + crisp cyan-ice fracture stroke
    drawPath(
        path = partial,
        color = Color(0xFFFF2A85).copy(alpha = 0.75f),
        style = Stroke(width = strokePx * 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    drawPath(
        path = partial,
        color = Color(0xFFE0F7FA),
        style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawPerimeterBurningFuse(
    width: Float,
    height: Float,
    radiusPx: Float,
    ratio: Float,
    isTriggered: Boolean,
    isStage2: Boolean
) {
    if (width <= 2f || height <= 2f) return
    val inset = -1.dp.toPx()
    val fuseRect = RoundRect(
        left = inset,
        top = inset,
        right = width - inset,
        bottom = height - inset,
        cornerRadius = CornerRadius(radiusPx, radiusPx)
    )
    val fullPath = Path().apply { addRoundRect(fuseRect) }
    val pm = PathMeasure()
    pm.setPath(fullPath, true)
    val totalLen = pm.length
    if (totalLen <= 0f) return

    val progress = if (isTriggered) (1f - ratio) else 0f
    val visibleLength = if (isTriggered) {
        max(14.dp.toPx(), totalLen * ratio * 0.48f)
    } else {
        totalLen * 0.44f
    }

    val startDist = ((progress + 0.28f) % 1f) * totalLen
    val endDist = startDist + visibleLength
    val segmentPath = Path()
    if (endDist <= totalLen) {
        pm.getSegment(startDist, endDist, segmentPath, true)
    } else {
        pm.getSegment(startDist, totalLen, segmentPath, true)
        pm.getSegment(0f, endDist - totalLen, segmentPath, true)
    }

    val fuseBrush = Brush.linearGradient(
        colors = listOf(Color(0xFFFFF59D), Color(0xFFFF9F1C), Color(0xFFFF2A85)),
        start = Offset.Zero,
        end = Offset(width, height)
    )
    val glowColor = if (isStage2) Color(0xFFFF2A85) else Color(0xFFFFB428)

    // Soft outer glow + crisp burning fuse core
    drawPath(
        path = segmentPath,
        color = glowColor.copy(alpha = if (isStage2) 0.55f else 0.36f),
        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
    )
    drawPath(
        path = segmentPath,
        brush = fuseBrush,
        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
    )

    // Incandescent tip highlight
    val tip = computeRoundedRectPerimeterPoint(width, height, radiusPx, progress)
    drawCircle(
        color = Color(0xFFFFF59D).copy(alpha = 0.85f),
        radius = if (isStage2) 4.2.dp.toPx() else 3.2.dp.toPx(),
        center = Offset(tip.x, tip.y)
    )
    drawCircle(
        color = Color.White,
        radius = if (isStage2) 2.0.dp.toPx() else 1.5.dp.toPx(),
        center = Offset(tip.x, tip.y)
    )
}

private fun DrawScope.drawBombFxSimulation(sim: BombFxSimulation) {
    // A) Shockwaves
    for (i in 0 until sim.shockwaves.size) {
        val sw = sim.shockwaves[i]
        val alpha = sw.alpha.coerceIn(0f, 1f)
        val center = Offset(sw.x, sw.y)
        val flashRadius = (sw.radius * 0.85f).coerceAtLeast(3f)
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color(0xFFFFF3E0).copy(alpha = alpha * 0.55f),
                    0.5f to Color(0xFFA855F7).copy(alpha = alpha * 0.28f),
                    1f to Color(0xFFA855F7).copy(alpha = 0f)
                ),
                center = center,
                radius = flashRadius
            ),
            radius = flashRadius,
            center = center
        )
        drawCircle(
            color = Color(0xFFFFD54F).copy(alpha = alpha * 0.75f),
            radius = sw.radius.coerceAtLeast(1f),
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
    }

    // B) Particles
    for (i in 0 until sim.particles.size) {
        val p = sim.particles[i]
        val progress = (p.age / p.life).coerceIn(0f, 1f)
        val alpha = (if (progress < 0.15f) progress / 0.15f else 1f - ((progress - 0.15f) / 0.85f))
            .coerceIn(0f, 1f)
        if (alpha <= 0.01f) continue

        when (p.kind) {
            BombParticleKind.SMOKE -> {
                val curRadius = (p.size + (p.maxSize - p.size) * progress).coerceAtLeast(2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to p.smokeRgb.copy(alpha = alpha * 0.28f),
                            0.6f to p.smokeRgb.copy(alpha = alpha * 0.12f),
                            1f to p.smokeRgb.copy(alpha = 0f)
                        ),
                        center = Offset(p.x, p.y),
                        radius = curRadius
                    ),
                    radius = curRadius,
                    center = Offset(p.x, p.y)
                )
            }
            BombParticleKind.GLASS_FLECK -> {
                val tri = Path().apply {
                    moveTo(p.x - p.size, p.y - p.size * 0.6f)
                    lineTo(p.x + p.size, p.y - p.size * 0.3f)
                    lineTo(p.x, p.y + p.size)
                    close()
                }
                rotate(degrees = p.rotationDeg, pivot = Offset(p.x, p.y)) {
                    drawPath(tri, color = p.color.copy(alpha = alpha))
                }
            }
            BombParticleKind.FIRE_SPARK, BombParticleKind.FUSE_SPARK -> {
                val tailMul = if (p.kind == BombParticleKind.FIRE_SPARK) 0.035f else 0.024f
                drawLine(
                    color = p.color.copy(alpha = alpha),
                    start = Offset(p.x, p.y),
                    end = Offset(p.x - p.vx * tailMul, p.y - p.vy * tailMul),
                    strokeWidth = p.size,
                    cap = StrokeCap.Round
                )
            }
            BombParticleKind.EMBER -> {
                val r = (p.size * (1f - progress * 0.35f)).coerceAtLeast(0.8f)
                drawCircle(
                    color = p.color.copy(alpha = alpha * 0.35f),
                    radius = r * 2.2f,
                    center = Offset(p.x, p.y)
                )
                drawCircle(
                    color = p.color.copy(alpha = alpha),
                    radius = r,
                    center = Offset(p.x, p.y)
                )
            }
        }
    }
}

/**
 * Glossy 3D Bomb + Flickering Animated Flame + "Al leer • Xs" countdown label.
 */
@Composable
fun VybBombIndicatorRow(
    isTriggered: Boolean,
    durationSec: Long,
    remainingSecFloat: Float,
    stage: VybBombVisualStage,
    modifier: Modifier = Modifier
) {
    val isUrgent = stage == VybBombVisualStage.STAGE_2_CRITICAL || stage == VybBombVisualStage.STAGE_3_VAPORIZE
    val flameAnim = rememberInfiniteTransition(label = "bomb_flame")
    val flicker by flameAnim.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isUrgent) 95 else 180, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bomb_flame_flicker"
    )

    val displaySec = if (isTriggered) {
        kotlin.math.ceil(remainingSecFloat.toDouble()).toInt().coerceAtLeast(1)
    } else {
        durationSec.toInt().coerceAtLeast(1)
    }

    val secColor by animateColorAsState(
        targetValue = if (isUrgent) Color(0xFFFECDD3) else Color(0xFFFDE68A),
        animationSpec = tween(250),
        label = "bomb_sec_color"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Canvas(modifier = Modifier.size(19.dp)) {
            val s = size.minDimension / 64f
            // Metallic fuse collar
            rotate(degrees = 32f, pivot = Offset(37f * s, 16f * s)) {
                drawRoundRect(
                    color = Color(0xFF334155),
                    topLeft = Offset(33f * s, 13f * s),
                    size = Size(9f * s, 7f * s),
                    cornerRadius = CornerRadius(2f * s, 2f * s)
                )
                drawRoundRect(
                    color = Color(0xFF64748B),
                    topLeft = Offset(33f * s, 13f * s),
                    size = Size(9f * s, 7f * s),
                    cornerRadius = CornerRadius(2f * s, 2f * s),
                    style = Stroke(width = 0.9f * s)
                )
            }

            // 3D glossy black bomb sphere
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF64748B),
                        0.32f to Color(0xFF272A3A),
                        0.82f to Color(0xFF11131D),
                        1f to Color(0xFF07080D)
                    ),
                    center = Offset(22f * s, 29f * s),
                    radius = 24f * s
                ),
                radius = 19f * s,
                center = Offset(27f * s, 37f * s)
            )

            // Upper-left specular highlights
            rotate(degrees = -32f, pivot = Offset(20f * s, 28f * s)) {
                drawOval(
                    color = Color.White.copy(alpha = 0.34f),
                    topLeft = Offset(13.5f * s, 24.6f * s),
                    size = Size(13f * s, 6.8f * s)
                )
            }
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = 1.6f * s,
                center = Offset(16.5f * s, 33.5f * s)
            )

            // Fuse cord
            val cord = Path().apply {
                moveTo(39f * s, 15f * s)
                quadraticTo(44f * s, 10f * s, 47f * s, 15f * s)
            }
            drawPath(
                path = cord,
                color = Color(0xFFD4A373),
                style = Stroke(width = 2.6f * s, cap = StrokeCap.Round)
            )

            // Flickering flame tip at (47, 15)
            val fScale = if (isUrgent) 1.15f + flicker * 0.27f else 0.92f + flicker * 0.20f
            val fRot = if (isUrgent) -9f + flicker * 18f else -5f + flicker * 11f
            rotate(degrees = fRot, pivot = Offset(47f * s, 15f * s)) {
                val glowR = (9.5f * fScale) * s
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to Color.White,
                            0.42f to Color(0xFFFFF59D),
                            0.78f to Color(0xFFFF9100),
                            1f to Color(0xFFFF3D00).copy(alpha = 0f)
                        ),
                        center = Offset(47f * s, 15f * s),
                        radius = glowR
                    ),
                    radius = glowR,
                    center = Offset(47f * s, 15f * s)
                )
                val outerFlame = Path().apply {
                    moveTo(47f * s, (15f - 9f * fScale) * s)
                    cubicTo(
                        (47f + 2.5f * fScale) * s, (15f - 4.5f * fScale) * s,
                        (47f + 6f * fScale) * s, (15f - 2.5f * fScale) * s,
                        (47f + 4f * fScale) * s, (15f + 1.5f * fScale) * s
                    )
                    cubicTo(
                        (47f + 2.5f * fScale) * s, (15f + 4.5f * fScale) * s,
                        (47f - 2.5f * fScale) * s, (15f + 4.5f * fScale) * s,
                        (47f - 4f * fScale) * s, (15f + 1.5f * fScale) * s
                    )
                    cubicTo(
                        (47f - 6f * fScale) * s, (15f - 2.5f * fScale) * s,
                        (47f - 2.5f * fScale) * s, (15f - 4.5f * fScale) * s,
                        47f * s, (15f - 9f * fScale) * s
                    )
                    close()
                }
                drawPath(outerFlame, color = Color(0xFFFF9F1C))

                val innerFlame = Path().apply {
                    moveTo(47f * s, (15f - 5.5f * fScale) * s)
                    cubicTo(
                        (47f + 1.4f * fScale) * s, (15f - 3f * fScale) * s,
                        (47f + 3f * fScale) * s, (15f - 1.5f * fScale) * s,
                        (47f + 2f * fScale) * s, (15f + 0.5f * fScale) * s
                    )
                    cubicTo(
                        (47f + 1.2f * fScale) * s, (15f + 2f * fScale) * s,
                        (47f - 1.2f * fScale) * s, (15f + 2f * fScale) * s,
                        (47f - 2f * fScale) * s, (15f + 0.5f * fScale) * s
                    )
                    cubicTo(
                        (47f - 3f * fScale) * s, (15f - 1.5f * fScale) * s,
                        (47f - 1.4f * fScale) * s, (15f - 3f * fScale) * s,
                        47f * s, (15f - 5.5f * fScale) * s
                    )
                    close()
                }
                drawPath(innerFlame, color = Color(0xFFFFF9C4))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Al leer · ",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFF1F5F9).copy(alpha = 0.95f)
            )
            Text(
                text = "${displaySec}s",
                style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = secColor
            )
        }
    }
}

/**
 * Dashed glassmorphic capsule shown once a bomb message finishes vaporizing:
 * "Mensaje autodestruido"
 */
@Composable
fun VybDestroyedBombGhostPill(
    isMine: Boolean,
    createdAt: Timestamp?,
    animateIn: Boolean = true,
    modifier: Modifier = Modifier
) {
    val vybColors = LocalVybColors.current
    val appearProgress = remember(animateIn) { Animatable(if (animateIn) 0f else 1f) }
    LaunchedEffect(animateIn) {
        if (animateIn) {
            appearProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 450, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f))
            )
        }
    }

    val p = appearProgress.value
    val bgColor = if (vybColors.isDark) {
        Color(0xFF14182A).copy(alpha = 0.65f)
    } else {
        vybColors.subtleSurface.copy(alpha = 0.85f)
    }
    val dashColor = if (vybColors.isDark) {
        Color(0xFF94A3B8).copy(alpha = 0.28f)
    } else {
        vybColors.hairlineBorder.copy(alpha = 0.85f)
    }
    val textColor = if (vybColors.isDark) Color(0xFF8E9BAE) else vybColors.secondaryText
    val bottomLeftDp = if (isMine) 16.dp else 6.dp
    val bottomRightDp = if (isMine) 6.dp else 16.dp

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = p
                val s = 0.96f + 0.04f * p
                scaleX = s
                scaleY = s
                translationY = (1f - p) * 4.dp.toPx()
            }
            .drawBehind {
                val r = 16.dp.toPx()
                val bl = bottomLeftDp.toPx()
                val br = bottomRightDp.toPx()
                val rr = RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    topLeftCornerRadius = CornerRadius(r, r),
                    topRightCornerRadius = CornerRadius(r, r),
                    bottomRightCornerRadius = CornerRadius(br, br),
                    bottomLeftCornerRadius = CornerRadius(bl, bl)
                )
                val path = Path().apply { addRoundRect(rr) }
                drawPath(path, color = bgColor)
                drawPath(
                    path = path,
                    color = dashColor,
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                    )
                )
            }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Extinguished bomb SVG icon with smoke wisps
            Canvas(modifier = Modifier.size(16.dp)) {
                val s = size.minDimension / 24f
                drawCircle(
                    color = Color(0xFF94A3B8).copy(alpha = 0.14f),
                    radius = 6.5f * s,
                    center = Offset(10.5f * s, 14.5f * s)
                )
                drawCircle(
                    color = textColor,
                    radius = 6.5f * s,
                    center = Offset(10.5f * s, 14.5f * s),
                    style = Stroke(width = 1.8f * s)
                )
                drawLine(
                    color = textColor,
                    start = Offset(15f * s, 9.5f * s),
                    end = Offset(17.2f * s, 7.3f * s),
                    strokeWidth = 1.8f * s,
                    cap = StrokeCap.Round
                )
                val wisp1 = Path().apply {
                    moveTo(17.5f * s, 4.5f * s)
                    quadraticTo(18.5f * s, 5.6f * s, 17.5f * s, 6.7f * s)
                }
                val wisp2 = Path().apply {
                    moveTo(19.8f * s, 3.5f * s)
                    quadraticTo(20.7f * s, 4.5f * s, 19.8f * s, 5.4f * s)
                }
                drawPath(wisp1, color = textColor, style = Stroke(width = 1.6f * s, cap = StrokeCap.Round))
                drawPath(wisp2, color = textColor, style = Stroke(width = 1.5f * s, cap = StrokeCap.Round))
            }

            Text(
                text = "Mensaje autodestruido",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
            if (createdAt != null) {
                Text(
                    text = formatMessageTime(createdAt),
                    style = MaterialTheme.typography.labelSmall.merge(VybMonoTabularStyle),
                    color = textColor.copy(alpha = 0.72f)
                )
            }
        }
    }
}
