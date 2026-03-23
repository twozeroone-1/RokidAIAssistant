package com.example.rokidglasses.input

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpriteButtonIntentInterpreterTest {

    @Test
    fun `button up maps to capture photo`() {
        assertThat(
            SpriteButtonIntentInterpreter.interpret("com.android.action.ACTION_SPRITE_BUTTON_UP")
        ).isEqualTo(SpriteButtonAction.TriggerCapturePhoto)
    }

    @Test
    fun `button long press maps to capture photo`() {
        assertThat(
            SpriteButtonIntentInterpreter.interpret("com.android.action.ACTION_SPRITE_BUTTON_LONG_PRESS")
        ).isEqualTo(SpriteButtonAction.TriggerCapturePhoto)
    }

    @Test
    fun `button down is ignored`() {
        assertThat(
            SpriteButtonIntentInterpreter.interpret("com.android.action.ACTION_SPRITE_BUTTON_DOWN")
        ).isEqualTo(SpriteButtonAction.None)
    }

    @Test
    fun `unknown action is ignored`() {
        assertThat(
            SpriteButtonIntentInterpreter.interpret("com.android.action.SOMETHING_ELSE")
        ).isEqualTo(SpriteButtonAction.None)
    }
}
