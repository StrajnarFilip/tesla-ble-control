@file:OptIn(ExperimentalLayoutApi::class)

package io.github.teslablecontrol.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    Box(modifier) {
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

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(vertical = 8.dp), content = content)
    }
}

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
 * A large labelled button for one action. The picture and the state line show how things are now
 * ("Locked"), the title says what tapping does ("Unlock"). TalkBack reads title, state and action.
 * The picture's accent part (a trunk lid, window glass…) is drawn in the accent colour.
 */
@Composable
fun ActionTile(
    @DrawableRes icon: Int,
    title: String,
    state: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    highlighted: Boolean = false,
    enabled: Boolean = true,
    titles: List<String> = emptyList(),
    states: List<String> = emptyList(),
) {
    val container = if (highlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val accent = if (highlighted) content else MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        modifier = modifier
            .heightIn(min = 120.dp)
            .semantics { if (state != null) stateDescription = state },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.height(TILE_PICTURE), contentAlignment = Alignment.CenterStart) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp, color = content)
                } else {
                    TwoToneIcon(icon, base = content, accent = accent, muted = lerp(container, content, 0.5f), height = TILE_PICTURE)
                }
            }
            // Room for every title and state this tile can show, so it never changes size.
            StableText(title, titles, style = MaterialTheme.typography.titleSmall)
            if (state != null || states.isNotEmpty()) {
                StableText(
                    state.orEmpty(),
                    states,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (highlighted) content else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val TILE_PICTURE = 56.dp

/**
 * A grid of [ActionTile]s that adapts its column count. Tiles in a row share the height of the
 * tallest, measured at their real width, so a row stays even whatever each tile says. A shorter
 * last row shares its width among its tiles.
 */
@Composable
fun TileGrid(modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    val columns = gridColumns()
    Layout(content = { content(Modifier) }, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
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
                row.forEach { tile ->
                    tile.place(x, y)
                    x += tile.width + gap
                }
                y += row.first().height + gap
            }
        }
    }
}

/** A whole row acts as one switch: title, optional explanation, and the switch. */
@Composable
fun ToggleRow(
    title: String,
    checked: Boolean?,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: Painter? = null,
    busy: Boolean = false,
    enabled: Boolean = true,
    descriptions: List<String> = emptyList(),
) {
    val unknown = stringResource(R.string.state_unknown)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(
                value = checked == true,
                enabled = enabled && !busy,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            // The switch can't show "unknown", so TalkBack says it instead of "off".
            .semantics { if (checked == null) stateDescription = unknown }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null || descriptions.isNotEmpty()) {
                StableText(
                    description.orEmpty(), descriptions,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        // The switch's slot keeps its size while the progress indicator stands in for it.
        Box(Modifier.size(width = 52.dp, height = 32.dp), contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            } else {
                // The row handles the click; the switch is only a visual.
                Switch(checked = checked == true, onCheckedChange = null, enabled = enabled)
            }
        }
    }
}

@Composable
fun ToggleRow(
    title: String,
    checked: Boolean?,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    description: String? = null,
    busy: Boolean = false,
    enabled: Boolean = true,
    descriptions: List<String> = emptyList(),
) = ToggleRow(title, checked, onCheckedChange, modifier, description, rememberVectorPainter(icon), busy, enabled, descriptions)

/** A tappable row with an icon, title and description, for navigation or one-shot commands. */
@Composable
fun ActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: Painter? = null,
    trailing: String? = null,
    busy: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    Surface(
        onClick = onClick,
        enabled = enabled && !busy,
        color = Color.Transparent,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .heightIn(min = 60.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(
                    icon, contentDescription = null, modifier = Modifier.size(26.dp),
                    tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title, style = MaterialTheme.typography.bodyLarge,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // A fixed slot on the right: the trailing word or the progress indicator, never a resize.
            Spacer(Modifier.width(8.dp))
            Box(Modifier.defaultMinSize(minWidth = 24.dp), contentAlignment = Alignment.Center) {
                if (trailing != null) {
                    Text(
                        trailing, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alpha(if (busy) 0f else 1f),
                    )
                }
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            }
        }
    }
}

@Composable
fun ActionRow(
    title: String,
    onClick: () -> Unit,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    description: String? = null,
    trailing: String? = null,
    busy: Boolean = false,
    enabled: Boolean = true,
    destructive: Boolean = false,
) = ActionRow(title, onClick, modifier, description, rememberVectorPainter(icon), trailing, busy, enabled, destructive)

/**
 * A choice among a few options as equal-width chips: all in one row when they fit, otherwise in
 * balanced rows of equal cells. Labels wrap rather than truncate at large font sizes. TalkBack
 * treats the group as radio buttons.
 */
@Composable
fun <T> OptionChips(
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icons: ((T) -> Int?)? = null,
) {
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val gap = 8.dp
    BoxWithConstraints(modifier.fillMaxWidth().selectableGroup()) {
        val minCell = (if (icons != null) 64.dp else 76.dp) * fontScale
        val fit = ((maxWidth + gap) / (minCell + gap)).toInt().coerceIn(1, options.size.coerceAtLeast(1))
        val rows = (options.size + fit - 1) / fit
        val perRow = (options.size + rows - 1) / rows.coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            options.chunked(perRow.coerceAtLeast(1)).forEach { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { (value, label) ->
                        OptionChip(label, icons?.invoke(value), value == selected, enabled, { onSelect(value) }, Modifier.weight(1f).fillMaxHeight())
                    }
                    // Keep cells the same width when the last row is shorter.
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun OptionChip(label: String, @DrawableRes icon: Int?, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
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
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = if (icon != null) 10.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                if (icon != null) {
                    TwoToneIcon(
                        icon, base = content, accent = if (selected) content else MaterialTheme.colorScheme.primary,
                        muted = lerp(chip, content, 0.5f), height = 30.dp,
                    )
                }
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

/** A labelled choice row: title above [OptionChips]. */
@Composable
fun <T> ChoiceRow(
    title: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    icons: ((T) -> Int?)? = null,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.semantics { heading() })
        if (description != null) {
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OptionChips(options, selected, onSelect, enabled = enabled, icons = icons)
    }
}

/** − value + with large buttons. The value is announced as the row's state. */
@Composable
fun Stepper(
    title: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseLabel: String,
    increaseLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    canDecrease: Boolean = true,
    canIncrease: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
        FilledTonalIconButton(
            onClick = onDecrease, enabled = enabled && canDecrease,
            modifier = Modifier.size(56.dp).semantics { contentDescription = decreaseLabel },
        ) { Icon(Icons.Outlined.Remove, contentDescription = null) }
        Spacer(Modifier.width(12.dp))
        FilledTonalIconButton(
            onClick = onIncrease, enabled = enabled && canIncrease,
            modifier = Modifier.size(56.dp).semantics { contentDescription = increaseLabel },
        ) { Icon(Icons.Outlined.Add, contentDescription = null) }
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

/** A coloured message card with an optional action, for problems that need the user's attention. */
@Composable
fun NoticeCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    error: Boolean = false,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                }
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null && onAction != null) {
                androidx.compose.material3.Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = 48.dp)) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
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
        confirmButton = {
            TextButton(onClick = { onSubmit(pin) }, enabled = valid, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * A part of the car that opens and closes, as one toggle tile: the picture and state line show
 * where it is now, the title says what tapping does. Until the car reports the state, the tile asks
 * which way to go. The tile has room for every title and state, so it never changes size.
 */
@Composable
fun ClosureTile(
    name: String,
    open: Boolean?,
    @DrawableRes openIcon: Int,
    @DrawableRes closedIcon: Int,
    openTitle: String,
    closeTitle: String,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
    busy: Boolean = false,
    openState: String = stringResource(R.string.state_open),
    closedState: String = stringResource(R.string.state_closed),
) {
    val unknownState = stringResource(R.string.state_unknown)
    var asking by remember { mutableStateOf(false) }
    ActionTile(
        icon = if (open == true) openIcon else closedIcon,
        title = when (open) {
            true -> closeTitle
            false -> openTitle
            null -> name
        },
        state = when (open) {
            true -> openState
            false -> closedState
            null -> unknownState
        },
        onClick = {
            when (open) {
                true -> onClose()
                false -> onOpen()
                null -> asking = true
            }
        },
        modifier = modifier,
        busy = busy,
        highlighted = open == true,
        titles = listOf(openTitle, closeTitle, name),
        states = listOf(openState, closedState, unknownState),
    )
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(name) },
            text = { Text(stringResource(R.string.closure_unknown_body), style = MaterialTheme.typography.bodyLarge) },
            confirmButton = {
                TextButton(onClick = { asking = false; onOpen() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(openTitle) }
            },
            dismissButton = {
                TextButton(onClick = { asking = false; onClose() }, modifier = Modifier.heightIn(min = 48.dp)) { Text(closeTitle) }
            },
        )
    }
}

/** One button in an [ActionButtonRow]. */
data class ActionButton(val label: String, val icon: Int, val onClick: () -> Unit, val busy: Boolean = false, val enabled: Boolean = true)

/** Buttons sharing a row equally, each an icon over its label. */
@Composable
fun ActionButtonRow(buttons: List<ActionButton>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        buttons.forEach { button ->
            Surface(
                onClick = button.onClick,
                enabled = button.enabled && !button.busy,
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.weight(1f).heightIn(min = 88.dp),
            ) {
                Column(
                    Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                        if (button.busy) {
                            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                        } else {
                            Icon(painterResource(button.icon), contentDescription = null, modifier = Modifier.size(30.dp))
                        }
                    }
                    Text(button.label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                }
            }
        }
    }
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
