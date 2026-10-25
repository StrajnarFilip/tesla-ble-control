package io.github.teslablecontrol.ui.components

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class TeslaSliderTest {
    private fun oneWord(vararg widths: Int) = widths.map { LabelWidth(it.dp, it.dp) }

    @Test
    fun optionsShareTheTrackEquallyWhenEveryLabelFits() {
        assertEquals(listOf(1f, 1f, 1f, 1f), cellWeights(oneWord(90, 20, 20, 20), 360.dp))
    }

    @Test
    fun aLongLabelKeepsItsWidthAndTheRestIsSharedEqually() {
        // "Izklopljeno | 1 | 2 | 3" on a narrow phone: 100 + 3 × 20 needed, 80 left over.
        assertEquals(listOf(120f, 40f, 40f, 40f), cellWeights(oneWord(100, 20, 20, 20), 240.dp))
    }

    @Test
    fun labelsThatCanWrapGiveWayToSingleWords() {
        // "Off | Keep on | Dog mode" too wide for one line each: "Off" keeps its width, and the
        // 20 left over goes to the two that wrap, by how much each lacks.
        val needed = listOf(LabelWidth(40.dp, 40.dp), LabelWidth(90.dp, 60.dp), LabelWidth(70.dp, 60.dp))
        assertEquals(listOf(40f, 75f, 65f), cellWeights(needed, 180.dp))
    }

    @Test
    fun withNoRoomEvenForTheWordsEachGetsItsShareOfWhatItNeeds() {
        assertEquals(listOf(100f, 50f), cellWeights(oneWord(100, 50), 120.dp))
    }
}
