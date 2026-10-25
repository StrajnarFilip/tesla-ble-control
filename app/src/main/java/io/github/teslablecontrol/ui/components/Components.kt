package io.github.teslablecontrol.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.teslablecontrol.R
import io.github.teslablecontrol.ui.format.Reading
import io.github.teslablecontrol.ui.theme.NumberStyle
import io.github.teslablecontrol.ui.theme.UnitStyle

/**
 * Text that always takes the size of the largest of [variants] (and [text]), so switching between
 * them never moves anything around it. The reserve copies are invisible and hidden from TalkBack.
 * Centred text stays centred in the reserved room.
 */
@Composable
fun StableText(
    text: String,
    variants: List<String>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    Box(modifier, contentAlignment = if (textAlign == TextAlign.Center) Alignment.Center else Alignment.TopStart) {
        variants.forEach { variant ->
            if (variant != text) {
                Text(variant, style = style, textAlign = textAlign, modifier = Modifier.alpha(0f).clearAndSetSemantics {})
            }
        }
        Text(text, style = style, color = color, textAlign = textAlign)
    }
}

/** Section title, announced as a heading by TalkBack. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .padding(top = 20.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/** The side margin of every screen's content; rows in a [SectionGroup] reach into it. */
private val PAGE_MARGIN = 16.dp

/**
 * Rows that belong together, straight on the page: the car's screen groups by headings and space,
 * not cards. Rows bring their own side padding, so the group reaches into the page's side margins;
 * their words line up with the section header and their touch feedback spans the screen.
 */
@Composable
fun SectionGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.bleed(PAGE_MARGIN).fillMaxWidth(), content = content)
}

/** Widens the content by [margin] on each side, into the parent's padding. */
private fun Modifier.bleed(margin: Dp) = layout { measurable, constraints ->
    val extra = (margin * 2).roundToPx()
    val wider = if (constraints.hasBoundedWidth) constraints.copy(maxWidth = constraints.maxWidth + extra) else constraints
    val placeable = measurable.measure(wider)
    layout((placeable.width - extra).coerceAtLeast(0), placeable.height) { placeable.place(-margin.roundToPx(), 0) }
}

/** Padding for a button among the rows of a [SectionGroup], so it lines up with them. */
fun Modifier.inGroup() = padding(horizontal = 16.dp, vertical = 6.dp)

/** The width of the app's window (not the whole screen, which differs in split screen). */
@Composable
fun windowWidth(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }

/** Two-column grids fall back to one column when text is very large or the screen is narrow. */
@Composable
fun gridColumns(): Int {
    val width = windowWidth()
    val fontScale = LocalDensity.current.fontScale
    return when {
        fontScale >= 1.5f || width < 360.dp -> 1
        width >= 840.dp -> 4
        width >= 600.dp -> 3
        else -> 2
    }
}

/**
 * Controls such as [ChoiceRow]s side by side in equal columns, wrapping into rows; a shorter last row
 * keeps the column width. Each control is measured at its column's width and placed at the top of
 * its row, so it never needs an intrinsic size, which [TeslaSlider] can't give.
 */
@Composable
fun ControlGrid(modifier: Modifier = Modifier, columns: Int = gridColumns(), content: @Composable () -> Unit) {
    // These grids contain two-state closure sliders. Reserve space for the longest translated
    // state, including the selected label's heavier font, before choosing how many columns fit.
    // Their titles must fit on one line too: a title that wraps would push its slider below the
    // one beside it.
    val measurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
    val titles = listOf(R.string.closure_frunk, R.string.closure_trunk, R.string.closure_tonneau, R.string.charging_port, R.string.closure_windows, R.string.closure_sunroof)
        .map { stringResource(it) }
    val widestTitle = with(LocalDensity.current) {
        titles.maxOf { measurer.measure(AnnotatedString(it), titleStyle, softWrap = false).size.width }.toDp()
    }
    val minimumCell = maxOf(
        minimumSliderWidth(
            listOf(stringResource(R.string.state_closed), stringResource(R.string.state_open), stringResource(R.string.state_vented)),
            optionCount = 2,
        ),
        widestTitle,
    ) + 32.dp
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val width = constraints.maxWidth
        val fittingColumns = minOf(columns, (width / minimumCell.roundToPx().coerceAtLeast(1)).coerceAtLeast(1))
        val cell = width / fittingColumns
        val rows = measurables.chunked(fittingColumns).map { row -> row.map { it.measure(Constraints(minWidth = cell, maxWidth = cell)) } }
        layout(width, rows.sumOf { row -> row.maxOf { it.height } }) {
            var y = 0
            rows.forEach { row ->
                row.forEachIndexed { index, control -> control.place(index * cell, y) }
                y += row.maxOf { it.height }
            }
        }
    }
}

/**
 * A whole row acts as one switch, laid out as on the car: the switch first, level with the title,
 * then the title and an optional explanation in lighter grey.
 */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean?,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    busy: Boolean = false,
    enabled: Boolean = true,
    descriptions: List<String> = emptyList(),
) {
    val unknown = stringResource(R.string.state_unknown)
    val titleStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
    // The switch sits in a slot one title line tall, so it lines up with the title's first line.
    val line = with(LocalDensity.current) { titleStyle.lineHeight.toDp() }.coerceAtLeast(SWITCH_HEIGHT)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(
                value = checked == true,
                enabled = enabled && !busy,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            // The switch can't show "unknown", so TalkBack says it instead of "off".
            .semantics { if (checked == null) stateDescription = unknown }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // The progress indicator stands in for the switch in the same slot, so nothing moves.
        Box(Modifier.size(width = SWITCH_WIDTH, height = line), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
            } else {
                TeslaSwitch(checked = checked == true, enabled = enabled)
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f).alpha(if (enabled) 1f else 0.6f)) {
            Box(Modifier.heightIn(min = line), contentAlignment = Alignment.CenterStart) {
                Text(title, style = titleStyle)
            }
            if (description != null || descriptions.isNotEmpty()) {
                StableText(
                    description.orEmpty(), descriptions,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * An entry in a list, such as a schedule or a key: its title and description, with its own button
 * (such as Remove) on the right.
 */
@Composable
fun ItemRow(
    title: String,
    description: String?,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    enabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        TeslaButton(actionLabel, onAction, busy = busy, enabled = enabled)
    }
}

/**
 * A choice among more options than fit a [TeslaSlider], as equal-width chips: all in one row
 * when they fit, otherwise in balanced rows of equal cells. Labels wrap rather than truncate at large
 * font sizes. TalkBack treats the group as radio buttons.
 */
@Composable
fun <T> OptionChips(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val gap = 8.dp
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    // Cells are never narrower than the longest word of any label (plus the chip's padding and
    // outline), so a translated word wraps whole onto the next line instead of being split.
    val longestWord = with(LocalDensity.current) {
        options.flatMap { it.second.split(' ') }
            .maxOfOrNull { word -> measurer.measure(AnnotatedString(word), labelStyle, softWrap = false).size.width }
            ?.toDp() ?: 0.dp
    }
    BoxWithConstraints(modifier.fillMaxWidth().selectableGroup()) {
        val minCell = maxOf(76.dp * fontScale, longestWord + 12.dp + 7.dp)
        val fit = ((maxWidth + gap) / (minCell + gap)).toInt().coerceIn(1, options.size.coerceAtLeast(1))
        val rows = (options.size + fit - 1) / fit
        val perRow = (options.size + rows - 1) / rows.coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            options.chunked(perRow.coerceAtLeast(1)).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { (value, label) ->
                        OptionChip(label, value == selected, enabled, { onSelect(value) }, Modifier.weight(1f).fillMaxHeight())
                    }
                    // Keep cells the same width when the last row is shorter.
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val content = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val chip = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = chip,
        contentColor = content,
        // Selection is shown by outline thickness as well as colour.
        border = if (selected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .defaultMinSize(minHeight = 52.dp)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Box {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
            // A tick in the corner as well as the colour and outline; it takes no space of its own.
            if (selected) {
                Icon(
                    Icons.Outlined.Check, contentDescription = null,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(14.dp),
                )
            }
        }
    }
}

/**
 * A labelled [TeslaSlider]: the title, an optional explanation, the slider, then an optional [note]
 * below it (room kept for every one of [notes]). Notes go below so that sliders side by side in a
 * [ControlGrid] stay level. [busy] while its command is being sent.
 */
@Composable
fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    note: String? = null,
    notes: List<String> = emptyList(),
    busy: Boolean = false,
    enabled: Boolean = true,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), modifier = Modifier.semantics { heading() })
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TeslaSlider(options, selected, onSelect, busy = busy, enabled = enabled)
        if (note != null || notes.isNotEmpty()) {
            StableText(note.orEmpty(), notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Both sides wrap within their share, so a long value can't squeeze the label.
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

/** A message with an optional action, straight on the page; the icon and title carry the colour. */
@Composable
fun Notice(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    error: Boolean = false,
) {
    val accent = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall, color = accent, modifier = Modifier.semantics { heading() })
        }
        Text(body, style = MaterialTheme.typography.bodyMedium)
        if (actionLabel != null && onAction != null) {
            TeslaButton(actionLabel, onAction)
        }
    }
}

/** The car's dialogs are a plain panel with the buttons side by side at the bottom. */
@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        confirmButton = { DialogButtons(stringResource(R.string.action_cancel), onDismiss, confirmLabel, onConfirm) },
    )
}

/** Asks for a 4-digit PIN; digits only, hidden as typed. */
@Composable
fun PinDialog(title: String, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    val valid = pin.length == 4 && pin.all(Char::isDigit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pin_title), style = MaterialTheme.typography.bodyLarge)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.pin_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    supportingText = { if (pin.isNotEmpty() && !valid) Text(stringResource(R.string.pin_invalid)) },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        confirmButton = {
            DialogButtons(
                stringResource(R.string.action_cancel), onDismiss,
                stringResource(R.string.action_send), { onSubmit(pin) },
                confirmEnabled = valid,
            )
        },
    )
}

/** A reading such as "422 km": the number large, the unit smaller on the same baseline. */
@Composable
fun ReadingText(reading: Reading, modifier: Modifier = Modifier) {
    Row(modifier) {
        Text(reading.number, style = NumberStyle, maxLines = 1, modifier = Modifier.alignByBaseline())
        if (reading.unit.isNotEmpty()) {
            Spacer(Modifier.width(4.dp))
            Text(reading.unit, style = UnitStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.alignByBaseline())
        }
    }
}
