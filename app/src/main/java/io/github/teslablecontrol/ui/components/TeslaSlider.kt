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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
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
private val LABEL_HORIZONTAL_PADDING = 6.dp

@Composable
private fun sliderLabelStyle() = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp)

/** A label's cell width on one line, and the least it can take without breaking a word. */
internal class LabelWidth(val line: Dp, val word: Dp)

/** The cell widths each label needs, in either weight. */
@Composable
private fun labelCellWidths(labels: List<String>): List<LabelWidth> {
    val measurer = rememberTextMeasurer()
    val style = sliderLabelStyle()
    val density = LocalDensity.current
    fun cell(text: String): Dp {
        val width = listOf(style, style.copy(fontWeight = FontWeight.SemiBold)).maxOf { weight ->
            measurer.measure(AnnotatedString(text), style = weight, softWrap = false).size.width
        }
        // Cells round to whole pixels independently; leave a little space beyond the measured
        // text so rounding cannot push the final letter onto a second line.
        return with(density) { width.toDp() } + LABEL_HORIZONTAL_PADDING * 2 + 2.dp
    }
    return labels.map { label -> LabelWidth(cell(label), label.split(' ').maxOf(::cell)) }
}

/** Width needed for equal cells without breaking a translated label in the middle of a word. */
@Composable
internal fun minimumSliderWidth(labels: List<String>, optionCount: Int): Dp =
    labelCellWidths(labels).maxOf { it.line } * optionCount + INSET * 2

private fun List<Dp>.total() = fold(0.dp) { sum, width -> sum + width }

/**
 * How the options share a [track] this wide: equally when every label fits that way, as on the car.
 * Otherwise each option gets the width its label needs on one line and they share what is left
 * equally. When even that is too wide, each keeps room for its longest word and the rest goes to the
 * labels that wrap, by how much they lack, so no translated word is ever split.
 */
internal fun cellWeights(needed: List<LabelWidth>, track: Dp): List<Float> {
    val lines = needed.map { it.line }
    if (lines.all { it * needed.size <= track }) return needed.map { 1f }
    if (lines.total() <= track) {
        val spare = (track - lines.total()) / needed.size
        return lines.map { (it + spare).value }
    }
    val words = needed.map { it.word }
    if (words.total() >= track) return words.map { it.value }
    val lacking = needed.map { it.line - it.word }
    return words.zip(lacking) { word, lack -> (word + (track - words.total()) * (lack / lacking.total())).value }
}

/** Where each cell starts across [span], and where the last one ends: one more edge than cells. */
private fun cellEdges(weights: List<Float>, span: Float): List<Float> {
    val total = weights.sum()
    return weights.runningFold(0f) { start, weight -> start + span * weight / total }
}

/** The left and right of a block at option position [at], which may lie between two options. */
private fun blockBounds(edges: List<Float>, at: Float): Pair<Float, Float> {
    val index = at.toInt().coerceIn(0, edges.size - 2)
    val fraction = at - index
    fun lerp(from: Float, to: Float) = from + (to - from) * fraction
    val next = (index + 2).coerceAtMost(edges.size - 1)
    return lerp(edges[index], edges[index + 1]) to lerp(edges[index + 1], edges[next])
}

/** The option position whose block starts at [x]; whole numbers are the options themselves. */
private fun positionAt(edges: List<Float>, x: Float): Float {
    val index = (0 until edges.size - 1).lastOrNull { edges[it] <= x } ?: 0
    return index + (x - edges[index]) / (edges[index + 1] - edges[index])
}

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

    val labelStyle = sliderLabelStyle()
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val needed = labelCellWidths(options.map { it.second })
    val preferredWidth = maxOf(OPTION_WIDTH * fontScale, needed.maxOf { it.line }) * count + INSET * 2
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = minOf(maxWidth, preferredWidth)
        // As fractions of the track: Row sizes its height from whole pixels per unit of weight, so
        // weights in dp (about 1.75 px each) would round to 2 px and wrongly fit two-line labels on one.
        val weights = cellWeights(needed, width - INSET * 2).let { w -> w.map { it / w.sum() } }
        Box(
            Modifier
                .width(width)
                .alpha(if (enabled) 1f else 0.45f)
                .border(1.dp, colors.outlineVariant, TRACK_SHAPE)
                .pointerInput(weights, interactive) {
                    if (!interactive) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val inset = INSET.toPx()
                        val edges = cellEdges(weights, size.width - 2 * inset)
                        val pressed = positionAt(edges, down.position.x - inset).toInt().coerceIn(0, count - 1)
                        val from = currentShown
                        // Holding the block keeps the finger where it took it; elsewhere it centres under the finger.
                        val grab = if (from == pressed) down.position.x - inset - edges[pressed] else (edges[pressed + 1] - edges[pressed]) / 2
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
                            val at = positionAt(edges, change.position.x - inset - grab).coerceIn(0f, count - 1f)
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
                            .weight(weights[index])
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
                            modifier = Modifier.padding(horizontal = LABEL_HORIZONTAL_PADDING, vertical = 8.dp),
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
                        val (left, right) = blockBounds(cellEdges(weights, size.width), at)
                        val cell = right - left
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
                options.forEachIndexed { index, (_, label) ->
                    Box(Modifier.weight(weights[index]).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(
                            label,
                            style = labelStyle.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.onSurface,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = LABEL_HORIZONTAL_PADDING, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
