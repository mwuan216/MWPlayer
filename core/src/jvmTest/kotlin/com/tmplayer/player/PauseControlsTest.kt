package com.tmplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PauseControlsTest {

    @Test
    fun `a resume after a pause over the bare picture lets the controls go sooner`() {
        assertEquals(1_500L, PauseControls.hideAfterResumeMs(3_000L, downBeforePause = true))
        assertEquals(3_000L, PauseControls.hideAfterResumeMs(3_000L, downBeforePause = false))
    }

    @Test
    fun `a shorter setting and never are kept`() {
        assertEquals(1_000L, PauseControls.hideAfterResumeMs(1_000L, downBeforePause = true))
        assertEquals(0L, PauseControls.hideAfterResumeMs(0L, downBeforePause = true))
    }
}
