package io.github.teslablecontrol.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.teslablecontrol.ui.theme.NumberStyle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** How long after the last − or + tap the value is sent, so several taps send one command. */
private const val BUTTON_SETTLE_MILLIS = 900L

/**
 * A value set by dragging a slider, with − and + for single steps. The value is shown large above
 * the slider. [onCommit] runs when the finger lifts or shortly after the last button tap, so the car
 * gets one command per adjustment rather than one per step.
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

    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) {}) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(valueText, style = NumberStyle)
            }
            // Reserved so the title doesn't rewrap when the indicator appears.
            Box(Modifier.size(24.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(
                onClick = { nudge(-1) },
                enabled = enabled && value > valueRange.start,
                modifier = Modifier.size(52.dp).semantics { contentDescription = decreaseLabel },
            ) { Icon(Icons.Outlined.Remove, contentDescription = null) }
            Spacer(Modifier.width(8.dp))
            val steps = (((valueRange.endInclusive - valueRange.start) / step).roundToInt() - 1).coerceAtLeast(0)
            Slider(
                value = value.coerceIn(valueRange),
                onValueChange = { onValueChange(snap(it)) },
                onValueChangeFinished = { commit() },
                valueRange = valueRange,
                steps = steps,
                enabled = enabled,
                // Steps keep values exact (also for TalkBack); dozens of tick dots would only add noise.
                colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
                    .semantics {
                        contentDescription = title
                        stateDescription = valueText
                    },
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = { nudge(+1) },
                enabled = enabled && value < valueRange.endInclusive,
                modifier = Modifier.size(52.dp).semantics { contentDescription = increaseLabel },
            ) { Icon(Icons.Outlined.Add, contentDescription = null) }
        }
    }
}
