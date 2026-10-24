package io.github.teslablecontrol.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.Dp

/** The colours tools/icons/generate_icons.py uses to mark an icon's accent and muted parts. */
private val ACCENT_MARKER = Color(0xFFFF00FF)
private val MUTED_MARKER = Color(0xFF00FFFF)

/**
 * One of the app's action icons with its accent part (the trunk lid, the window glass…) in [accent],
 * the parts that stay put (the rest of the car) in [muted], and everything else in [base]. Pass
 * opaque colours: overlapping strokes would show through transparent ones.
 */
@Composable
fun TwoToneIcon(@DrawableRes id: Int, base: Color, accent: Color, muted: Color, height: Dp, modifier: Modifier = Modifier) {
    val source = ImageVector.vectorResource(id)
    val vector = remember(source, base, accent, muted) { source.recolored(base, accent, muted) }
    // Wide pictures (the car) keep their proportions at the requested height.
    val width = height * (source.defaultWidth / source.defaultHeight)
    Image(rememberVectorPainter(vector), contentDescription = null, modifier = modifier.size(width, height))
}

private fun ImageVector.recolored(base: Color, accent: Color, muted: Color): ImageVector {
    val builder = ImageVector.Builder(name, defaultWidth, defaultHeight, viewportWidth, viewportHeight)
    fun paint(brush: Brush?): Brush? = (brush as? SolidColor)?.let {
        SolidColor(
            when (it.value) {
                ACCENT_MARKER -> accent
                MUTED_MARKER -> muted
                else -> base
            },
        )
    } ?: brush
    fun copy(group: VectorGroup) {
        for (node in group) {
            when (node) {
                is VectorPath -> builder.addPath(
                    pathData = node.pathData,
                    pathFillType = node.pathFillType,
                    name = node.name,
                    fill = paint(node.fill),
                    fillAlpha = node.fillAlpha,
                    stroke = paint(node.stroke),
                    strokeAlpha = node.strokeAlpha,
                    strokeLineWidth = node.strokeLineWidth,
                    strokeLineCap = node.strokeLineCap,
                    strokeLineJoin = node.strokeLineJoin,
                    strokeLineMiter = node.strokeLineMiter,
                )
                is VectorGroup -> {
                    builder.addGroup(
                        node.name, node.rotation, node.pivotX, node.pivotY, node.scaleX, node.scaleY,
                        node.translationX, node.translationY, node.clipPathData,
                    )
                    copy(node)
                    builder.clearGroup()
                }
            }
        }
    }
    copy(root)
    return builder.build()
}
