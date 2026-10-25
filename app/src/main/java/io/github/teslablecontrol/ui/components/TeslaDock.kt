package io.github.teslablecontrol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One destination in a [TeslaDock]. */
class DockItem(val label: String, val icon: ImageVector, val selected: Boolean, val onClick: () -> Unit)

/** The edges a [TeslaDock] with `vertical` set pads for; the screen beside it shouldn't pad them again. */
val SideMenuInsets: WindowInsets @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical)

/** The side menu's width, not counting a camera cutout or system bar beside it. */
private val SIDE_MENU_WIDTH = 196.dp
private val SIDE_ITEM_SHAPE = RoundedCornerShape(10.dp)

/**
 * The bar of category icons, as at the bottom of the car's screen and the phone app: a flat strip,
 * each category an icon over its name, the open one brighter and its name in semibold. No pill
 * behind the icons. [vertical] makes it the car's settings menu down the side instead, for wide
 * windows: on the page colour, each category an icon beside its name, the open one on a flat,
 * brighter block.
 */
@Composable
fun TeslaDock(items: List<DockItem>, modifier: Modifier = Modifier, vertical: Boolean = false) {
    if (vertical) {
        Column(
            modifier
                .fillMaxHeight()
                .windowInsetsPadding(SideMenuInsets)
                .width(SIDE_MENU_WIDTH)
                .verticalScroll(rememberScrollState())
                // The first category lines up with the screen's title bar.
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items.forEach { SideMenuItem(it) }
        }
    } else {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp).selectableGroup()) {
                items.forEach { DockButton(it, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

/** One row of the side menu, measured from the car: 48dp, 10dp corners, icon 16dp in, name at 56dp. */
@Composable
private fun SideMenuItem(item: DockItem) {
    val color = if (item.selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(SIDE_ITEM_SHAPE)
            .background(if (item.selected) MaterialTheme.colorScheme.surfaceBright else Color.Transparent)
            .selectable(selected = item.selected, role = Role.Tab, onClick = item.onClick)
            .padding(start = 16.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(
            item.label,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = style.fontSize),
        )
    }
}

@Composable
private fun DockButton(item: DockItem, modifier: Modifier) {
    val color = if (item.selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (item.selected) FontWeight.SemiBold else FontWeight.Medium)
    Column(
        modifier
            .selectable(selected = item.selected, role = Role.Tab, onClick = item.onClick)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(item.icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        // At very large text sizes the name shrinks to fit rather than being cut off.
        Text(
            item.label,
            style = style,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = style.fontSize),
        )
    }
}
