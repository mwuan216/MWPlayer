package com.tmplayer.player

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/** The locked player answers to the volume and a held select key, and to nothing else. */
class LockedKeysTest {

    @Test
    fun `the select keys of a remote, a keyboard and a controller unlock when held`() {
        listOf(
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A,
        ).forEach { assertEquals(LockedKeys.Kind.Unlock, LockedKeys.kind(it)) }
    }

    @Test
    fun `the volume stays the system's`() {
        listOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE)
            .forEach { assertEquals(LockedKeys.Kind.System, LockedKeys.kind(it)) }
    }

    @Test
    fun `media keys, arrows, Back, Menu and the keyboard are ignored`() {
        listOf(
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
            KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
            KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_ESCAPE,
            KeyEvent.KEYCODE_5,
        ).forEach { assertEquals(LockedKeys.Kind.Ignored, LockedKeys.kind(it)) }
    }
}
