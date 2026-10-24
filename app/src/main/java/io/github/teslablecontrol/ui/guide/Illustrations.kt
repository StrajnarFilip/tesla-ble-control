package io.github.teslablecontrol.ui.guide

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.teslablecontrol.ui.theme.LocalStatusColors
import kotlin.math.min

/** The pictures in the setup guide and troubleshooting. Simple, original line art. */
enum class Picture {
    WELCOME, WHAT_YOU_NEED, VIN, PERMISSIONS, SCANNING, ROLES, KEY_CARD_CHARGER, KEY_CARD_CONSOLE, TILE, SUCCESS,
    TOO_MANY_CONNECTIONS, ASLEEP, BLUETOOTH_OFF,
}

private class Ink(val line: Color, val accent: Color, val fill: Color, val soft: Color, val success: Color, val danger: Color)

/** True unless the user turned animations off in system settings. */
@Composable
fun animationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}

@Composable
fun Illustration(picture: Picture, description: String, modifier: Modifier = Modifier, height: Dp = 170.dp) {
    val scheme = MaterialTheme.colorScheme
    val ink = Ink(
        line = scheme.onSurfaceVariant,
        accent = scheme.primary,
        fill = scheme.surfaceContainerHighest,
        soft = scheme.surfaceContainerHigh,
        success = LocalStatusColors.current.success,
        danger = scheme.error,
    )
    val animate = animationsEnabled()
    val transition = rememberInfiniteTransition(label = "illustration")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val t = if (animate) phase else 0.6f
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description },
    ) {
        // Draw in a 200 × 120 design space, centred and scaled to fit.
        val scale = min(size.width / 200f, size.height / 120f)
        val dx = (size.width - 200f * scale) / 2f
        val dy = (size.height - 120f * scale) / 2f
        withTransform({
            translate(dx, dy)
            scale(scale, scale, pivot = Offset.Zero)
        }) {
            when (picture) {
                Picture.WELCOME -> welcome(ink, t)
                Picture.WHAT_YOU_NEED -> whatYouNeed(ink)
                Picture.VIN -> vin(ink)
                Picture.PERMISSIONS -> permissions(ink, t)
                Picture.SCANNING -> scanning(ink, t)
                Picture.ROLES -> roles(ink)
                Picture.KEY_CARD_CHARGER -> keyCard(ink, t, onCharger = true)
                Picture.KEY_CARD_CONSOLE -> keyCard(ink, t, onCharger = false)
                Picture.TILE -> tile(ink, t)
                Picture.SUCCESS -> success(ink)
                Picture.TOO_MANY_CONNECTIONS -> tooMany(ink)
                Picture.ASLEEP -> asleep(ink, t)
                Picture.BLUETOOTH_OFF -> bluetoothOff(ink)
            }
        }
    }
}

private fun stroke(width: Float = 2.2f) = Stroke(width = width, cap = StrokeCap.Round)

// --- Building blocks ---

private fun DrawScope.phone(ink: Ink, x: Float, y: Float, w: Float = 34f, h: Float = 62f, screen: Color = ink.soft) {
    drawRoundRect(screen, Offset(x, y), Size(w, h), CornerRadius(6f))
    drawRoundRect(ink.line, Offset(x, y), Size(w, h), CornerRadius(6f), style = stroke())
    drawLine(ink.line, Offset(x + w * 0.38f, y + 4f), Offset(x + w * 0.62f, y + 4f), strokeWidth = 2f, cap = StrokeCap.Round)
}

/** A generic car side view; (x, y) is the top-left of a 90 × 40 box. */
private fun DrawScope.car(ink: Ink, x: Float, y: Float, scale: Float = 1f, body: Color = ink.fill) {
    translate(x, y) {
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            val outline = Path().apply {
                moveTo(2f, 30f)
                lineTo(2f, 22f)
                quadraticTo(2f, 17f, 7f, 16f)
                lineTo(22f, 12f)
                lineTo(33f, 3f)
                quadraticTo(35f, 1f, 39f, 1f)
                lineTo(55f, 1f)
                quadraticTo(59f, 1f, 61f, 3f)
                lineTo(72f, 12f)
                lineTo(83f, 14f)
                quadraticTo(88f, 15f, 88f, 21f)
                lineTo(88f, 30f)
                close()
            }
            drawPath(outline, body)
            drawPath(outline, ink.line, style = stroke())
            val windows = Path().apply {
                moveTo(28f, 12f); lineTo(35f, 5f); lineTo(46f, 5f); lineTo(46f, 12f); close()
                moveTo(50f, 12f); lineTo(50f, 5f); lineTo(58f, 5f); lineTo(66f, 12f); close()
            }
            drawPath(windows, ink.soft)
            drawPath(windows, ink.line, style = stroke(1.6f))
            for (cx in listOf(20f, 70f)) {
                drawCircle(ink.soft, 8f, Offset(cx, 30f))
                drawCircle(ink.line, 8f, Offset(cx, 30f), style = stroke())
                drawCircle(ink.line, 3f, Offset(cx, 30f), style = stroke(1.6f))
            }
        }
    }
}

private fun DrawScope.keyCardShape(ink: Ink, x: Float, y: Float, w: Float = 40f, h: Float = 25f) {
    drawRoundRect(ink.fill, Offset(x, y), Size(w, h), CornerRadius(4f))
    drawRoundRect(ink.line, Offset(x, y), Size(w, h), CornerRadius(4f), style = stroke())
    drawRoundRect(ink.accent, Offset(x + 5f, y + 7f), Size(9f, 7f), CornerRadius(1.5f))
    drawLine(ink.line, Offset(x + 5f, y + h - 6f), Offset(x + w - 6f, y + h - 6f), strokeWidth = 1.6f, cap = StrokeCap.Round)
}

/** Radio waves fanning out from (x, y) towards +x; [t] animates them outwards. */
private fun DrawScope.waves(ink: Ink, x: Float, y: Float, t: Float, color: Color = ink.accent, count: Int = 3) {
    for (i in 0 until count) {
        val r = 8f + i * 7f + t * 7f
        val alpha = (1f - (i + t) / count).coerceIn(0.15f, 1f)
        drawArc(color.copy(alpha = alpha), -45f, 90f, false, Offset(x - r, y - r), Size(2 * r, 2 * r), style = stroke(2.4f))
    }
}

private fun DrawScope.check(ink: Ink, cx: Float, cy: Float, r: Float) {
    drawCircle(ink.success, r, Offset(cx, cy))
    val tick = Path().apply {
        moveTo(cx - r * 0.45f, cy)
        lineTo(cx - r * 0.1f, cy + r * 0.35f)
        lineTo(cx + r * 0.5f, cy - r * 0.35f)
    }
    drawPath(tick, Color.White, style = Stroke(width = r * 0.2f, cap = StrokeCap.Round))
}

private fun DrawScope.cross(ink: Ink, cx: Float, cy: Float, r: Float) {
    drawCircle(ink.danger, r, Offset(cx, cy))
    drawLine(Color.White, Offset(cx - r * 0.4f, cy - r * 0.4f), Offset(cx + r * 0.4f, cy + r * 0.4f), r * 0.2f, StrokeCap.Round)
    drawLine(Color.White, Offset(cx + r * 0.4f, cy - r * 0.4f), Offset(cx - r * 0.4f, cy + r * 0.4f), r * 0.2f, StrokeCap.Round)
}

private fun DrawScope.textLines(ink: Ink, x: Float, y: Float, w: Float, lines: Int, gap: Float = 6f) {
    for (i in 0 until lines) {
        val lw = if (i == lines - 1) w * 0.6f else w
        drawLine(ink.line.copy(alpha = 0.6f), Offset(x, y + i * gap), Offset(x + lw, y + i * gap), 2f, StrokeCap.Round)
    }
}

private fun DrawScope.person(ink: Ink, cx: Float, cy: Float, color: Color = ink.line) {
    drawCircle(color, 7f, Offset(cx, cy - 10f), style = stroke())
    drawArc(color, 180f, 180f, false, Offset(cx - 13f, cy + 1f), Size(26f, 22f), style = stroke())
}

// --- Pictures ---

private fun DrawScope.welcome(ink: Ink, t: Float) {
    phone(ink, 28f, 30f)
    waves(ink, 66f, 60f, t)
    car(ink, 96f, 42f)
}

private fun DrawScope.whatYouNeed(ink: Ink) {
    phone(ink, 18f, 28f)
    keyCardShape(ink, 66f, 46f)
    car(ink, 112f, 40f, scale = 0.8f)
    // Plus signs between the items.
    for (x in listOf(58f, 112f - 6f)) {
        drawLine(ink.line, Offset(x - 4f, 59f), Offset(x + 4f, 59f), 2f, StrokeCap.Round)
        drawLine(ink.line, Offset(x, 55f), Offset(x, 63f), 2f, StrokeCap.Round)
    }
}

private fun DrawScope.vin(ink: Ink) {
    // Windshield corner seen from outside, with the VIN plate at the bottom.
    val glass = Path().apply {
        moveTo(20f, 100f); lineTo(60f, 18f); lineTo(185f, 18f); lineTo(185f, 100f); close()
    }
    drawPath(glass, ink.soft)
    drawPath(glass, ink.line, style = stroke())
    drawRoundRect(ink.fill, Offset(52f, 78f), Size(64f, 16f), CornerRadius(3f))
    drawRoundRect(ink.accent, Offset(52f, 78f), Size(64f, 16f), CornerRadius(3f), style = stroke(2.4f))
    textLines(ink, 58f, 86f, 52f, 1)
    // Touchscreen as the other place to look.
    drawRoundRect(ink.fill, Offset(130f, 32f), Size(48f, 32f), CornerRadius(4f))
    drawRoundRect(ink.line, Offset(130f, 32f), Size(48f, 32f), CornerRadius(4f), style = stroke())
    textLines(ink, 136f, 42f, 36f, 3)
}

private fun DrawScope.permissions(ink: Ink, t: Float) {
    phone(ink, 76f, 10f, w = 48f, h = 100f)
    drawRoundRect(ink.fill, Offset(82f, 36f), Size(36f, 48f), CornerRadius(4f))
    drawRoundRect(ink.line, Offset(82f, 36f), Size(36f, 48f), CornerRadius(4f), style = stroke(1.6f))
    textLines(ink, 86f, 44f, 28f, 3, gap = 5f)
    // Highlighted "Allow" button with a pulsing ring.
    drawRoundRect(ink.accent, Offset(86f, 68f), Size(28f, 9f), CornerRadius(4.5f))
    val pulse = 2f + t * 4f
    drawRoundRect(
        ink.accent.copy(alpha = 1f - t), Offset(86f - pulse, 68f - pulse), Size(28f + 2 * pulse, 9f + 2 * pulse),
        CornerRadius(4.5f + pulse), style = stroke(1.6f),
    )
}

private fun DrawScope.scanning(ink: Ink, t: Float) {
    car(ink, 100f, 42f)
    phone(ink, 24f, 30f)
    for (i in 0 until 3) {
        val r = 14f + ((i / 3f + t) % 1f) * 52f
        drawCircle(ink.accent.copy(alpha = (1f - r / 66f).coerceAtLeast(0f)), r, Offset(41f, 61f), style = stroke(2f))
    }
}

private fun DrawScope.roles(ink: Ink) {
    drawRoundRect(ink.soft, Offset(18f, 14f), Size(76f, 92f), CornerRadius(10f))
    drawRoundRect(ink.accent, Offset(18f, 14f), Size(76f, 92f), CornerRadius(10f), style = stroke(2.4f))
    person(ink, 56f, 52f, ink.accent)
    // Owner: a key below the person.
    drawCircle(ink.accent, 5f, Offset(44f, 88f), style = stroke())
    drawLine(ink.accent, Offset(49f, 88f), Offset(70f, 88f), 2.4f, StrokeCap.Round)
    drawLine(ink.accent, Offset(64f, 88f), Offset(64f, 93f), 2.4f, StrokeCap.Round)

    drawRoundRect(ink.soft, Offset(106f, 14f), Size(76f, 92f), CornerRadius(10f))
    drawRoundRect(ink.line, Offset(106f, 14f), Size(76f, 92f), CornerRadius(10f), style = stroke())
    person(ink, 144f, 52f)
    // Driver: a steering wheel.
    drawCircle(ink.line, 9f, Offset(144f, 88f), style = stroke())
    drawLine(ink.line, Offset(135f, 88f), Offset(153f, 88f), 2f, StrokeCap.Round)
    drawLine(ink.line, Offset(144f, 88f), Offset(144f, 97f), 2f, StrokeCap.Round)
}

/**
 * The centre console from above, front of the car on the left: wireless phone charger, cup holders,
 * then the reader area behind them. The card comes down onto the highlighted spot.
 */
private fun DrawScope.keyCard(ink: Ink, t: Float, onCharger: Boolean) {
    drawRoundRect(ink.soft, Offset(10f, 30f), Size(180f, 72f), CornerRadius(16f))
    drawRoundRect(ink.line, Offset(10f, 30f), Size(180f, 72f), CornerRadius(16f), style = stroke())
    // Wireless charger with a lightning mark.
    val charger = Rect(Offset(20f, 40f), Size(58f, 52f))
    drawRoundRect(ink.fill, charger.topLeft, charger.size, CornerRadius(8f))
    drawRoundRect(ink.line, charger.topLeft, charger.size, CornerRadius(8f), style = stroke(1.6f))
    val bolt = Path().apply {
        moveTo(51f, 52f); lineTo(43f, 67f); lineTo(50f, 67f); lineTo(46f, 80f); lineTo(56f, 62f); lineTo(49f, 62f); close()
    }
    drawPath(bolt, ink.line.copy(alpha = 0.6f))
    // Cup holders.
    drawCircle(ink.line, 11f, Offset(100f, 53f), style = stroke())
    drawCircle(ink.line, 11f, Offset(100f, 79f), style = stroke())
    // Reader area behind the cup holders.
    val behind = Rect(Offset(124f, 44f), Size(54f, 44f))
    drawRoundRect(ink.line.copy(alpha = 0.35f), behind.topLeft, behind.size, CornerRadius(6f), style = stroke(1.6f))

    val target = if (onCharger) charger else behind
    drawRoundRect(ink.accent.copy(alpha = 0.25f), target.topLeft, target.size, CornerRadius(8f))
    drawRoundRect(ink.accent, target.topLeft, target.size, CornerRadius(8f), style = stroke(2.4f))
    val lift = (1f - t) * 28f
    val card = Offset(target.center.x - 20f, target.center.y - 12.5f - lift)
    keyCardShape(ink, card.x, card.y, w = 40f, h = 25f)
    if (t > 0.7f) waves(ink, card.x + 42f, target.center.y, (t - 0.7f) / 0.3f)
}

private fun DrawScope.tile(ink: Ink, t: Float) {
    phone(ink, 66f, 4f, w = 68f, h = 112f)
    // Pulled-down shade with a grid of tiles; ours is highlighted.
    drawRoundRect(ink.fill, Offset(72f, 14f), Size(56f, 58f), CornerRadius(5f))
    for (row in 0..1) for (col in 0..1) {
        val ox = 76f + col * 26f
        val oy = 20f + row * 24f
        val ours = row == 1 && col == 0
        drawRoundRect(if (ours) ink.accent else ink.soft, Offset(ox, oy), Size(22f, 18f), CornerRadius(5f))
    }
    // Swipe-down gesture.
    val y = 80f + t * 18f
    drawLine(ink.accent, Offset(100f, 78f), Offset(100f, y), 2.4f, StrokeCap.Round)
    drawCircle(ink.accent, 4f, Offset(100f, y))
}

private fun DrawScope.success(ink: Ink) {
    car(ink, 32f, 48f)
    check(ink, 150f, 60f, 24f)
}

private fun DrawScope.tooMany(ink: Ink) {
    car(ink, 10f, 42f)
    val devices = listOf(Offset(150f, 18f), Offset(150f, 50f), Offset(150f, 82f))
    devices.forEach { d ->
        drawLine(ink.line, Offset(98f, 60f), Offset(d.x - 4f, d.y + 6f), 1.8f, StrokeCap.Round)
        drawRoundRect(ink.fill, Offset(d.x - 4f, d.y), Size(16f, 14f), CornerRadius(3f))
        drawRoundRect(ink.line, Offset(d.x - 4f, d.y), Size(16f, 14f), CornerRadius(3f), style = stroke(1.6f))
    }
    phone(ink, 176f, 52f, w = 18f, h = 32f)
    cross(ink, 185f, 98f, 8f)
}

private fun DrawScope.asleep(ink: Ink, t: Float) {
    car(ink, 30f, 52f)
    // "Z z z" rising above the car.
    for (i in 0 until 3) {
        val s = 6f + i * 3f
        val x = 128f + i * 16f
        val y = 50f - i * 14f - t * 4f
        val z = Path().apply { moveTo(x, y); lineTo(x + s, y); lineTo(x, y + s); lineTo(x + s, y + s) }
        drawPath(z, ink.accent, style = stroke(2.2f))
    }
}

private fun DrawScope.bluetoothOff(ink: Ink) {
    val rune = Path().apply {
        moveTo(86f, 36f); lineTo(114f, 64f); lineTo(100f, 78f); lineTo(100f, 22f); lineTo(114f, 36f); lineTo(86f, 64f)
    }
    drawPath(rune, ink.line, style = stroke(3f))
    drawLine(ink.danger, Offset(78f, 20f), Offset(122f, 80f), 3f, StrokeCap.Round)
}
