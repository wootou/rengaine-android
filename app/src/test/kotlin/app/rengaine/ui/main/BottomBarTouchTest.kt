package app.rengaine.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Barre du bas : un bouton répond au premier appui même si le doigt
 * bouge d'un pixel (écrans Samsung), et un appui entre deux boutons
 * n'atteint pas la liste dessous.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class BottomBarTouchTest {

    @get:Rule val rule = createComposeRule()

    private var buttonClicks = 0
    private var listClicks = 0

    private fun setUp() {
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().testTag("list").clickable { listClicks++ })
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.White).blockTouchesBelow()) {
                    Row(Modifier.testTag("bar").height(80.dp)) {
                        Box(Modifier.size(80.dp).testTag("button").clickable { buttonClicks++ })
                        Spacer(Modifier.width(80.dp))
                    }
                }
            }
        }
    }

    @Test fun tapWithTinyMoveOpensTheTab() {
        setUp()
        rule.onNodeWithTag("button").performTouchInput {
            down(center)
            moveBy(Offset(1f, 1f))
            up()
        }
        rule.waitForIdle()
        assertEquals(1, buttonClicks)
    }

    @Test fun plainTapOpensTheTab() {
        setUp()
        rule.onNodeWithTag("button").performTouchInput { down(center); up() }
        rule.waitForIdle()
        assertEquals(1, buttonClicks)
    }

    @Test fun tapBetweenButtonsStaysInTheBar() {
        setUp()
        rule.onNodeWithTag("bar").performTouchInput {
            val p = Offset(width * 0.75f, height / 2f)
            down(p)
            moveBy(Offset(1f, 1f))
            up()
        }
        rule.waitForIdle()
        assertEquals(0, listClicks)
    }
}
