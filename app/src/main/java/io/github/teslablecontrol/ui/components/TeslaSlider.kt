package io.github.teslablecontrol.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.teslablecontrol.R
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Each option's width, as on the car's screen; options share the row when that's too wide. */
private val OPTION_WIDTH = 104.dp
private val HEIGHT = 48.dp
private val TRACK_SHAPE = RoundedCornerShape(7.dp)
private val PILL_RADIUS = 5.dp

/** The border, then a hairline gap around the chosen option. */
private val INSET = 2.dp

/** How long a new choice is shown after its command if the car hasn't reported it yet. */
private const val CONFIRM_WAIT_MILLIS = 3000L

private val SLIDE = tween<Float>(240, easing = FastOutSlowInEasing)

/**
 * The TeslaSlider: a choice among a few options, styled like the car's touchscreen. It is the control
 * for anything with a few states, such as open and closed or a mode. The chosen option is a lighter
 * block that can be tapped to, or dragged along the row and let go near an option. [selected] is the
 * car's state, or null until it reports one (no block then). A new choice stays shown, with a bar
 * sweeping under it, while [busy] and until the car reports it, so the block never jumps back and
 * forth; if the car doesn't confirm, it returns to the car's state. Nothing changes size. TalkBack
 * treats the options as radio buttons.
 */
@Composable
fun <T> TeslaSlider(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    enabled: Boolean = true,
) {
    val count = options.size
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    val sending = stringResource(R.string.status_sending)

    var target by remember { mutableStateOf<T?>(null) }
    val selectedIndex = options.indexOfFirst { it.first == selected }.takeIf { it >= 0 }
    val targetIndex = options.indexOfFirst { it.first == target }.takeIf { target != null && it >= 0 }
    val shown = targetIndex ?: selectedIndex
    val pending = busy || (targetIndex != null && targetIndex != selectedIndex)
    val interactive = enabled && !busy

    // The car has confirmed the choice, or it hasn't within a while of the command: show its state again.
    LaunchedEffect(target, selected, busy) {
        if (target == null) return@LaunchedEffect
        if (target == selected) {
            target = null
        } else if (!busy) {
            delay(CONFIRM_WAIT_MILLIS)
            target = null
        }
    }

    val position = remember { Animatable((shown ?: 0).toFloat()) }
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    var blockShown by remember { mutableStateOf(shown != null) }
    LaunchedEffect(shown) {
        when {
            shown == null -> blockShown = false
            blockShown -> position.animateTo(shown.toFloat(), SLIDE)
            else -> {
                // Appears in place rather than sliding in from wherever it last was.
                position.snapTo(shown.toFloat())
                blockShown = true
            }
        }
    }

    val choose: (Int) -> Unit = { index ->
        if (index != shown) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            val value = options[index].first
            target = value
            onSelect(value)
        }
    }
    val currentChoose by rememberUpdatedState(choose)
    val currentShown by rememberUpdatedState(shown)

    // Lets go of a dragged block at [at]: it settles on the nearest option.
    fun settle(at: Float) {
        val index = at.roundToInt()
        scope.launch {
            position.snapTo(at)
            dragPosition = null
            blockShown = true
            position.animateTo(index.toFloat(), SLIDE)
        }
        currentChoose(index)
    }

    val sweep = if (pending) {
        rememberInfiniteTransition(label = "sending").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
            label = "sweep",
        )
    } else {
        null
    }

    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp)
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = minOf(maxWidth, OPTION_WIDTH * fontScale * count + INSET * 2)
        Box(
            Modifier
                .width(width)
                .alpha(if (enabled) 1f else 0.45f)
                .border(1.dp, colors.outlineVariant, TRACK_SHAPE)
                .pointerInput(count, interactive) {
                    if (!interactive) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val inset = INSET.toPx()
                        val cell = (size.width - 2 * inset) / count
                        val pressed = ((down.position.x - inset) / cell).toInt().coerceIn(0, count - 1)
                        val from = currentShown
                        // Holding the block keeps the finger where it took it; elsewhere it centres under the finger.
                        val grab = if (from == pressed) down.position.x - inset - pressed * cell else cell / 2
                        var detent = from ?: pressed
                        var dragged: Float? = null
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                if (dragged == null && !change.isConsumed) currentChoose(pressed)
                                break
                            }
                            if (dragged == null) {
                                // A scrolling parent took the gesture.
                                if (change.isConsumed) break
                                val moved = change.position - down.position
                                if (abs(moved.y) > viewConfiguration.touchSlop && abs(moved.y) >= abs(moved.x)) break
                                if (abs(moved.x) <= viewConfiguration.touchSlop) continue
                            }
                            change.consume()
                            val at = ((change.position.x - inset - grab) / cell).coerceIn(0f, count - 1f)
                            dragged = at
                            dragPosition = at
                            if (at.roundToInt() != detent) {
                                detent = at.roundToInt()
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            }
                        }
                        dragged?.let(::settle)
                    }
                }
                .padding(INSET)
                .selectableGroup(),
        ) {
            // The options as they look off the block, and as TalkBack reads them.
            Row(Modifier.fillMaxWidth().heightIn(min = HEIGHT - INSET * 2).height(IntrinsicSize.Min)) {
                options.forEachIndexed { index, (_, label) ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics(mergeDescendants = true) {
                                role = Role.RadioButton
                                this.selected = index == shown
                                if (pending && index == shown) stateDescription = sending
                                if (interactive) {
                                    onClick { currentChoose(index); true }
                                } else {
                                    disabled()
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = labelStyle,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            // The block, and the options again in its colours, shown only where the block is.
            Row(
                Modifier
                    .matchParentSize()
                    .clearAndSetSemantics {}
                    .drawWithContent {
                        val at = dragPosition ?: position.value.takeIf { blockShown && shown != null } ?: return@drawWithContent
                        val cell = size.width / count
                        val left = at * cell
                        drawRoundRect(colors.surfaceBright, Offset(left, 0f), Size(cell, size.height), CornerRadius(PILL_RADIUS.toPx()))
                        clipRect(left, 0f, left + cell, size.height) { this@drawWithContent.drawContent() }
                        if (sweep != null) {
                            val bar = 3.dp.toPx()
                            val barLeft = left + cell * 0.16f
                            val barWidth = cell * 0.68f
                            val top = size.height - bar - 3.dp.toPx()
                            val radius = CornerRadius(bar / 2)
                            drawRoundRect(colors.primary.copy(alpha = 0.2f), Offset(barLeft, top), Size(barWidth, bar), radius)
                            clipRect(barLeft, top, barLeft + barWidth, top + bar) {
                                val x = barLeft + (sweep.value * 1.4f - 0.4f) * barWidth
                                drawRoundRect(colors.primary, Offset(x, top), Size(barWidth * 0.4f, bar), radius)
                            }
                        }
                    },
            ) {
                options.forEach { (_, label) ->
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            style = labelStyle.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
