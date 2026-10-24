package io.github.teslablecontrol.ui.climate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** One seat in the plan: the catalog's seat id, its name for TalkBack and the car's reported level (0–3). */
class PlanSeat(val id: String, val name: String, val level: Int?)

/** How long after the last tap on a seat its level is sent, so several taps send one command. */
private const val SETTLE_MILLIS = 800L

/**
 * The cabin from above, front at the top, with each seat where it is in the car. Tapping a seat
 * steps it Off → Low → Medium → High → Off; the new level shows at once and is sent once taps stop.
 * [rows] lists the seats per row, front row first.
 */
@Composable
fun SeatPlan(
    rows: List<List<PlanSeat>>,
    levelNames: List<String>,
    heating: Boolean,
    onSet: (seatId: String, level: Int) -> Unit,
    frontLabel: String,
    modifier: Modifier = Modifier,
) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    val cabin = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(modifier.fillMaxWidth()) {
        // The car body behind the seats: rounded nose at the top, windshield and rear window lines.
        Canvas(Modifier.matchParentSize()) {
            val stroke = 2.dp.toPx()
            val nose = size.width * 0.22f
            val tail = size.width * 0.12f
            val body = Path().apply {
                moveTo(0f, nose)
                quadraticTo(0f, 0f, nose * 1.4f, 0f)
                lineTo(size.width - nose * 1.4f, 0f)
                quadraticTo(size.width, 0f, size.width, nose)
                lineTo(size.width, size.height - tail)
                quadraticTo(size.width, size.height, size.width - tail, size.height)
                lineTo(tail, size.height)
                quadraticTo(0f, size.height, 0f, size.height - tail)
                close()
            }
            drawPath(body, cabin)
            drawPath(body, outline, style = Stroke(stroke))
            val glass = 58.dp.toPx()
            val windshield = Path().apply {
                moveTo(size.width * 0.12f, glass + 12.dp.toPx())
                quadraticTo(size.width / 2f, glass - 4.dp.toPx(), size.width * 0.88f, glass + 12.dp.toPx())
            }
            drawPath(windshield, outline, style = Stroke(stroke, cap = StrokeCap.Round))
            val rearWindow = Path().apply {
                moveTo(size.width * 0.16f, size.height - tail * 1.1f)
                quadraticTo(size.width / 2f, size.height - tail * 0.45f, size.width * 0.84f, size.height - tail * 1.1f)
            }
            drawPath(rearWindow, outline, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                frontLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            // Clear of the windshield line, which sits just below the label.
            Spacer(Modifier.height(28.dp))
            rows.forEachIndexed { index, row ->
                // Front seats sit either side of the centre console; rear rows are a bench.
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (index == 0 && row.size == 2) 28.dp else 10.dp),
                ) {
                    row.forEach { seat ->
                        SeatButton(seat, levelNames, heating, onSet, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SeatButton(
    seat: PlanSeat,
    levelNames: List<String>,
    heating: Boolean,
    onSet: (String, Int) -> Unit,
    modifier: Modifier,
) {
    val set by rememberUpdatedState(onSet)
    // The level chosen here but not yet confirmed by the car; shown instead of the car's level.
    var chosen by remember { mutableStateOf<Int?>(null) }
    var taps by remember { mutableIntStateOf(0) }
    LaunchedEffect(taps) {
        val level = chosen ?: return@LaunchedEffect
        delay(SETTLE_MILLIS)
        set(seat.id, level)
    }
    // Forget the choice once the car reports it, or after a while if it never does.
    LaunchedEffect(seat.level, chosen) {
        if (chosen != null && chosen == seat.level) chosen = null
        if (chosen != null) {
            delay(8_000)
            chosen = null
        }
    }
    val level = chosen ?: seat.level
    val next = ((level ?: 0) + 1) % 4
    val active = (level ?: 0) > 0
    val warm = Color(0xFFF2A65A)
    val markColor = if (heating) warm else MaterialTheme.colorScheme.primary
    val levelName = level?.let { levelNames[it] } ?: levelNames[0]
    Surface(
        onClick = {
            chosen = next
            taps++
        },
        // The backrest is at the rear of the seat, so the bottom corners are squarer.
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 10.dp, bottomEnd = 10.dp),
        color = if (active) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(
            if (active) 2.dp else 1.dp,
            if (active) markColor else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier
            .heightIn(min = 104.dp)
            .semantics {
                role = Role.Button
                contentDescription = seat.name
                stateDescription = levelName
                onClick(label = levelNames[next]) { chosen = next; taps++; true }
            },
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(width = 44.dp, height = 30.dp), contentAlignment = Alignment.Center) {
                LevelMarks(level ?: 0, heating, markColor, MaterialTheme.colorScheme.outlineVariant)
            }
            Text(levelName, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

/** Three marks, as many lit as the level: heat waves for heating, airflow lines for cooling. */
@Composable
private fun LevelMarks(level: Int, heating: Boolean, lit: Color, unlit: Color) {
    Canvas(Modifier.size(width = 44.dp, height = 30.dp)) {
        val stroke = 3.dp.toPx()
        for (i in 0 until 3) {
            val color = if (i < level) lit else unlit
            if (heating) {
                val x = size.width * (0.22f + i * 0.28f)
                val wave = Path().apply {
                    moveTo(x, size.height)
                    quadraticTo(x - 6.dp.toPx(), size.height * 0.75f, x, size.height * 0.5f)
                    quadraticTo(x + 6.dp.toPx(), size.height * 0.25f, x, 0f)
                }
                drawPath(wave, color, style = Stroke(stroke, cap = StrokeCap.Round))
            } else {
                val y = size.height * (0.18f + i * 0.32f)
                drawLine(color, Offset(size.width * 0.12f, y), Offset(size.width * 0.88f, y), stroke, StrokeCap.Round)
            }
        }
    }
}
