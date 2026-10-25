package io.github.teslablecontrol.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import io.github.teslablecontrol.ui.components.ClosureRow
import io.github.teslablecontrol.ui.components.ControlGrid
import io.github.teslablecontrol.data.ThemeMode
import io.github.teslablecontrol.ui.theme.AppTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class LocalizedControlsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun longTranslatedStatesMoveControlsOntoSeparateRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.GERMAN) }
        val resources = context.createConfigurationContext(config).resources
        compose.setContent {
            CompositionLocalProvider(LocalResources provides resources) {
                AppTheme(ThemeMode.LIGHT) {
                    Box(Modifier.width(360.dp)) {
                        ControlGrid(columns = 2) {
                            ClosureRow("Vorderer Kofferraum", open = false, onSet = {})
                            ClosureRow("Hinterer Kofferraum", open = false, onSet = {})
                        }
                    }
                }
            }
        }
        val front = compose.onNodeWithText("Vorderer Kofferraum").fetchSemanticsNode().boundsInRoot
        val rear = compose.onNodeWithText("Hinterer Kofferraum").fetchSemanticsNode().boundsInRoot
        assertTrue("Long labels need full-width rows", rear.top >= front.bottom)
        compose.onAllNodesWithText("Geschlossen").assertCountEquals(2)
        repeat(2) { index ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onAllNodesWithText("Geschlossen")[index]
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("The complete translated word fits on one line", layouts.isNotEmpty() && layouts.all { it.lineCount == 1 })
        }
    }
}
