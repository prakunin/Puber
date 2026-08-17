package com.kino.puber.core.ui.uikit.state

import androidx.compose.runtime.MutableState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SessionSaveableStateTest {

    @Test
    fun lazyListSaver_restoresPositionWithinTheSameProcessSession() {
        val restored = sessionLazyListStateSaver("current").restore(listOf("current", 4, 32))

        assertEquals(4, restored?.firstVisibleItemIndex)
        assertEquals(32, restored?.firstVisibleItemScrollOffset)
    }

    @Test
    fun lazyListSaver_rejectsPositionFromAnEarlierProcessSession() {
        val restored = sessionLazyListStateSaver("current").restore(listOf("previous", 4, 32))

        assertNull(restored)
    }

    @Test
    fun focusSaver_rejectsFocusFromAnEarlierProcessSession() {
        val saver = sessionMutableStateSaver<Int?>("current")

        val restored: MutableState<Int?>? = saver.restore(listOf("previous", 42))

        assertNull(restored)
    }
}
