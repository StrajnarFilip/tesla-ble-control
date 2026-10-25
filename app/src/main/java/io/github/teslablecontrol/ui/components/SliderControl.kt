@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.teslablecontrol.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** How long after the last − or + tap the value is sent, so several taps send one command. */
private const val BUTTON_SETTLE_MILLIS = 900L

/** Measured from the car's screen: about four times as wide as it is tall. */
private val STEPPER_WIDTH = 192.dp
private val STEP_TARGET = 56.dp

/**
 * The car's − value + control: one grey block, − at the left end, + at the right, the value between
 * them. It keeps its width whatever the value says.
 */
@Composable
fun TeslaStepper(
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    Surface(
        shape = TeslaControlShape,
        color = colors.surfaceContainerHighest,
        contentColor = colors.onSurface,
        modifier = modifier
            .widthIn(max = STEPPER_WIDTH * fontScale)
            .fillMaxWidth()
            .height(TESLA_CONTROL_HEIGHT)
            .alpha(if (enabled) 1f else 0.45f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(Icons.Outlined.Remove, decreaseLabel, enabled && canDecrease, onDecrease)
            // Shrinks rather than wraps at very large text sizes, so the block keeps its height.
            Text(
                value,
                style = style,
                textAlign = TextAlign.Center,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = style.fontSize),
                modifier = Modifier.weight(1f),
            )
            StepButton(Icons.Outlined.Add, increaseLabel, enabled && canIncrease, onIncrease)
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .width(STEP_TARGET)
            .fillMaxHeight()
            .clip(TeslaControlShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp).alpha(if (enabled) 1f else 0.35f))
    }
}

/**
 * A value with both of the car's controls: the [TeslaStepper] beside the title, which alone shows the
 * value, and a slider below it for big changes. [onCommit] runs when the finger lifts or shortly after
 * the last − or + tap, so the car gets one command per adjustment rather than one per step.
 */
@Composable
fun SliderControl(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    onCommit: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    enabled: Boolean = true,
) {
    val commit by rememberUpdatedState(onCommit)
    var buttonTaps by remember { mutableIntStateOf(0) }
    LaunchedEffect(buttonTaps) {
        if (buttonTaps > 0) {
            delay(BUTTON_SETTLE_MILLIS)
            commit()
        }
    }

    fun snap(v: Float): Float {
        val start = valueRange.start
        return (start + ((v - start) / step).roundToInt() * step).coerceIn(valueRange)
    }

    fun nudge(direction: Int) {
        onValueChange(snap(value + direction * step))
        buttonTaps++
    }

    val stepper: @Composable (Modifier) -> Unit = { stepperModifier ->
        TeslaStepper(
            value = valueText,
            onDecrease = { nudge(-1) },
            onIncrease = { nudge(+1) },
            decreaseLabel = decreaseLabel,
            increaseLabel = increaseLabel,
            canDecrease = value > valueRange.start,
            canIncrease = value < valueRange.endInclusive,
            enabled = enabled,
            modifier = stepperModifier,
        )
    }
    val titleStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        BoxWithConstraints {
            // Beside the title normally; below it when large text leaves the title too little room.
            val stepperWidth = minOf(STEPPER_WIDTH * LocalDensity.current.fontScale.coerceAtLeast(1f), maxWidth * 0.58f)
            val besideTitle = LocalDensity.current.fontScale < 1.3f
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = TESLA_CONTROL_HEIGHT)) {
                    Text(title, style = titleStyle, modifier = Modifier.weight(1f))
                    // Reserved so the title doesn't rewrap when the indicator appears.
                    Box(Modifier.padding(horizontal = 8.dp).size(24.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    }
                    if (besideTitle) stepper(Modifier.width(stepperWidth))
                }
                if (!besideTitle) stepper(Modifier)
            }
        }
        TeslaValueSlider(
            value = value,
            onValueChange = { onValueChange(snap(it)) },
            onValueChangeFinished = { commit() },
            valueRange = valueRange,
            step = step,
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = title
                stateDescription = valueText
            },
        )
    }
}

/** A slider drawn like the car's: a thin track, blue up to the value, and a round white knob. */
@Composable
private fun TeslaValueSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val active = colors.primaryContainer
    val inactive = colors.surfaceContainerHighest
    val steps = (((valueRange.endInclusive - valueRange.start) / step).roundToInt() - 1).coerceAtLeast(0)
    Slider(
        value = value.coerceIn(valueRange),
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        // Steps keep values exact, also for TalkBack; no tick marks are drawn.
        steps = steps,
        enabled = enabled,
        thumb = {
            Spacer(
                Modifier
                    .size(26.dp)
                    .shadow(2.dp, CircleShape)
                    .background(Color.White, CircleShape),
            )
        },
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val fraction = if (span > 0f) ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f) else 0f
            Canvas(Modifier.fillMaxWidth().height(6.dp).alpha(if (enabled) 1f else 0.45f)) {
                val y = size.height / 2
                drawLine(inactive, Offset(0f, y), Offset(size.width, y), size.height, StrokeCap.Round)
                drawLine(active, Offset(0f, y), Offset(size.width * fraction, y), size.height, StrokeCap.Round)
            }
        },
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp),
    )
}
