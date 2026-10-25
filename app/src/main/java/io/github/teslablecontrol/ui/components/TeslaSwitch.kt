package io.github.teslablecontrol.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/** Measured from the car's screen: a pill twice as wide as it is tall, the knob 3/4 of its height. */
internal val SWITCH_WIDTH = 56.dp
internal val SWITCH_HEIGHT = 28.dp
private val KNOB_INSET = 3.5.dp

/**
 * The car's on/off switch: a blue pill with a white knob on the right when on, grey with the knob on
 * the left when off. It only shows the state; the row around it takes the taps.
 */
@Composable
fun TeslaSwitch(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    val position by animateFloatAsState(if (checked) 1f else 0f, tween(180), label = "knob")
    val on = colors.primaryContainer
    val off = lerp(colors.outlineVariant, colors.outline, 0.35f)
    Canvas(modifier.size(SWITCH_WIDTH, SWITCH_HEIGHT).alpha(if (enabled) 1f else 0.45f)) {
        drawRoundRect(lerp(off, on, position), cornerRadius = CornerRadius(size.height / 2))
        val inset = KNOB_INSET.toPx()
        val radius = size.height / 2 - inset
        val travel = size.width - 2 * (inset + radius)
        drawCircle(Color.White, radius, Offset(inset + radius + position * travel, size.height / 2))
    }
}
