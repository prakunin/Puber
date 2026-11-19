package com.kino.puber.core.ui.uikit.component

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class SkeletonAsyncImageVisibilityTest {

    @Test
    fun skeletonVisible_hidden_whenOptInAndImageShownBefore() {
        assertFalse(skeletonVisible(isLoading = true, skeletonOnlyUntilFirstImage = true, hasShownImage = true))
    }

    @Test
    fun skeletonVisible_shown_whenOptInButNoImageYet() {
        assertTrue(skeletonVisible(isLoading = true, skeletonOnlyUntilFirstImage = true, hasShownImage = false))
    }

    @Test
    fun skeletonVisible_staysVisibleWhileLoading_whenOptInOff() {
        assertTrue(skeletonVisible(isLoading = true, skeletonOnlyUntilFirstImage = false, hasShownImage = true))
    }
}
