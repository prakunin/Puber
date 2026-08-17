package com.kino.puber.core.ui.uikit.state

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import java.util.UUID

/**
 * Identity of this app process, deliberately different after Android recreates the process.
 *
 * Compose's saved-state registry is still used while the process is alive, which preserves state
 * across tab disposal and Activity recreation. A Bundle restored into a new process carries the
 * old token and is rejected, making a cold Home start begin at its default position.
 */
private val processSaveableSessionToken = UUID.randomUUID().toString()

@Composable
internal fun rememberSessionLazyListState(): LazyListState = rememberSaveable(
    saver = sessionLazyListStateSaver(),
) {
    LazyListState()
}

internal fun sessionLazyListStateSaver(
    sessionToken: String = processSaveableSessionToken,
): Saver<LazyListState, Any> = Saver(
    save = { state ->
        listOf(sessionToken, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
    },
    restore = { saved ->
        val values = saved as? List<*> ?: return@Saver null
        if (values.getOrNull(0) != sessionToken) return@Saver null
        LazyListState(
            firstVisibleItemIndex = values.getOrNull(1) as? Int ?: return@Saver null,
            firstVisibleItemScrollOffset = values.getOrNull(2) as? Int ?: return@Saver null,
        )
    },
)

internal fun <T> sessionMutableStateSaver(
    sessionToken: String = processSaveableSessionToken,
): Saver<MutableState<T>, Any> = Saver(
    save = { state -> listOf(sessionToken, state.value) },
    restore = { saved ->
        val values = saved as? List<*> ?: return@Saver null
        if (values.getOrNull(0) != sessionToken || values.size < 2) return@Saver null
        @Suppress("UNCHECKED_CAST")
        mutableStateOf(values[1] as T)
    },
)
