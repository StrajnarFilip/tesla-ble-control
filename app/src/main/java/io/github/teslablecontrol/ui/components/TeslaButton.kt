package io.github.teslablecontrol.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.teslablecontrol.R

/** The height of the car's buttons and − / + controls, the same as a [TeslaSlider]. */
internal val TESLA_CONTROL_HEIGHT = 48.dp

/** Measured from the car's screen: about 8 px of rounding on a 70 px tall button. */
internal val TeslaControlShape = RoundedCornerShape(6.dp)

/**
 * A button as on the car's touchscreen: a flat grey block, 48 dp tall, with its words centred in
 * semibold and no icon. [outlined] is the car's confirm style, the colour of what it sits on with a
 * thin border, for the main action beside a grey one; [destructive] words are red. While [busy] a
 * progress indicator stands in for the label, which keeps its room; [labels] reserves room for every
 * label the button can show, so it never resizes.
 */
@Composable
fun TeslaButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    destructive: Boolean = false,
    busy: Boolean = false,
    enabled: Boolean = true,
    labels: List<String> = emptyList(),
) {
    val colors = MaterialTheme.colorScheme
    val sending = stringResource(R.string.status_sending)
    Surface(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = TeslaControlShape,
        color = if (outlined) Color.Transparent else colors.surfaceContainerHighest,
        contentColor = if (destructive) colors.error else colors.onSurface,
        border = if (outlined) BorderStroke(1.dp, colors.outlineVariant) else null,
        modifier = modifier
            .heightIn(min = TESLA_CONTROL_HEIGHT)
            .alpha(if (enabled) 1f else 0.45f)
            .semantics { if (busy) stateDescription = sending },
    ) {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            StableText(label, labels, style = teslaLabelStyle(), textAlign = TextAlign.Center, modifier = Modifier.alpha(if (busy) 0f else 1f))
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = colors.onSurface)
        }
    }
}

/**
 * A full-width [TeslaButton] with an optional [note] under it, such as what the command needs or
 * where the button leads.
 */
@Composable
fun ButtonWithNote(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    destructive: Boolean = false,
    busy: Boolean = false,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TeslaButton(label, onClick, Modifier.fillMaxWidth(), destructive = destructive, busy = busy, enabled = enabled)
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A media control showing only its well-known symbol, as on the car; [label] is for TalkBack. */
@Composable
fun TeslaIconButton(icon: Painter, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, busy: Boolean = false, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = TeslaControlShape,
        color = colors.surfaceContainerHighest,
        contentColor = colors.onSurface,
        modifier = modifier
            .height(TESLA_CONTROL_HEIGHT)
            .alpha(if (enabled) 1f else 0.45f)
            .semantics { contentDescription = label },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = colors.onSurface)
            } else {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** The words on the car's buttons and sliders. */
@Composable
internal fun teslaLabelStyle() = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)

/** A dialog's buttons as on the car: side by side and equally wide, cancel grey, confirm outlined. */
@Composable
fun DialogButtons(
    dismissLabel: String,
    onDismiss: () -> Unit,
    confirmLabel: String?,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TeslaButton(dismissLabel, onDismiss, Modifier.weight(1f).fillMaxHeight())
        if (confirmLabel != null) {
            TeslaButton(confirmLabel, onConfirm, Modifier.weight(1f).fillMaxHeight(), outlined = true, enabled = confirmEnabled)
        }
    }
}

/**
 * [TeslaButton]s in equal columns ([columns], or as many as the screen allows), all of a row as tall
 * as its tallest, measured at their real width, so a row stays even whatever each button says. A
 * shorter last row shares its width among its buttons.
 */
@Composable
fun ButtonGrid(modifier: Modifier = Modifier, columns: Int = gridColumns(), content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val gap = 12.dp.roundToPx()
        val width = constraints.maxWidth
        val rows = measurables.chunked(columns).map { row ->
            val cell = (width - gap * (row.size - 1)) / row.size
            val height = row.maxOf { it.maxIntrinsicHeight(cell) }
            row.map { it.measure(Constraints.fixed(cell, height)) }
        }
        val total = rows.sumOf { it.first().height } + gap * (rows.size - 1).coerceAtLeast(0)
        layout(width, total) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                row.forEach { button ->
                    button.place(x, y)
                    x += button.width + gap
                }
                y += row.first().height + gap
            }
        }
    }
}
