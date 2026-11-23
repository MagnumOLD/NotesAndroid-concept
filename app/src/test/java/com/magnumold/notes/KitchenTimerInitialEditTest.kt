package com.magnumold.notes

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class KitchenTimerInitialEditTest {
    @Test
    fun requestedTimerIsResolvedSynchronouslyForInitialEditorState() {
        val other = KitchenTimer(
            id = 93000L,
            title = "Другой",
            durationSeconds = 300L,
            status = KitchenTimerStatus.READY
        )
        val requested = KitchenTimer(
            id = 93001L,
            title = "Гречка",
            durationSeconds = 600L,
            remainingSeconds = 255L,
            status = KitchenTimerStatus.PAUSED
        )

        assertSame(requested, resolveInitialKitchenTimer(listOf(other, requested), requested.id))
    }

    @Test
    fun noRequestedTimerKeepsTheListAsInitialDestination() {
        val timer = KitchenTimer(
            id = 93001L,
            title = "Гречка",
            durationSeconds = 600L,
            status = KitchenTimerStatus.READY
        )

        assertNull(resolveInitialKitchenTimer(listOf(timer), null))
        assertNull(resolveInitialKitchenTimer(listOf(timer), 99999L))
    }
}
