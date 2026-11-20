package com.kino.puber.core.ui.uikit.component

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.unit.dp
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TvDialogOverlayFocusTrapTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val focusedSentinels = mutableStateListOf<String>()

    @Test
    fun tvDialogOverlayDpadDoesNotLeaveToContentBeneath() {
        val button = FocusRequester()
        composeRule.setContent {
            PuberTheme {
                Box(Modifier.fillMaxSize()) {
                    Sentinel("top", Alignment.TopCenter)
                    Sentinel("bottom", Alignment.BottomCenter)
                    Sentinel("left", Alignment.CenterStart)
                    Sentinel("right", Alignment.CenterEnd)
                    TvDialogOverlay(onDismiss = {}) {
                        Box(
                            Modifier
                                .size(80.dp)
                                .testTag(ButtonTag)
                                .focusRequester(button)
                                .focusable(),
                        )
                    }
                }
            }
        }
        composeRule.runOnIdle { button.requestFocus() }

        listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight).forEach { key ->
            composeRule.onNode(isFocused()).performKeyInput {
                keyDown(key)
                keyUp(key)
            }
            composeRule.waitForIdle()
            assertTrue("Focus left the dialog on $key: $focusedSentinels", focusedSentinels.isEmpty())
            composeRule.onNodeWithTag(ButtonTag).assertIsFocused()
        }
    }

    @Test
    fun tvDialogOverlayClearFocusStillLeavesTextField() {
        val field = FocusRequester()
        var focusManagerRef: FocusManager? = null
        composeRule.setContent {
            PuberTheme {
                TvDialogOverlay(onDismiss = {}) {
                    focusManagerRef = LocalFocusManager.current
                    var text by remember { mutableStateOf("") }
                    // Focus handed back by clearFocus needs somewhere to land, as the dialog's buttons are.
                    Column {
                        Box(Modifier.size(40.dp).focusable())
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            modifier = Modifier
                                .size(80.dp)
                                .testTag(FieldTag)
                                .focusRequester(field),
                        )
                    }
                }
            }
        }
        composeRule.runOnIdle { field.requestFocus() }
        composeRule.onNodeWithTag(FieldTag).assertIsFocused()

        composeRule.runOnIdle { focusManagerRef!!.clearFocus() }

        composeRule.onNodeWithTag(FieldTag).assertIsNotFocused()
    }

    @Composable
    private fun Sentinel(name: String, alignment: Alignment) {
        Box(Modifier.fillMaxSize(), contentAlignment = alignment) {
            Box(
                Modifier
                    .size(40.dp)
                    .onFocusChanged {
                        if (it.isFocused) focusedSentinels.add(name) else focusedSentinels.remove(name)
                    }
                    .focusable(),
            )
        }
    }

    private companion object {
        const val ButtonTag = "button"
        const val FieldTag = "field"
    }
}
