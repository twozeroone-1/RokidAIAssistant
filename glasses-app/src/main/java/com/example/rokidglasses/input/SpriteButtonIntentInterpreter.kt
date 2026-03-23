package com.example.rokidglasses.input

enum class SpriteButtonAction {
    None,
    TriggerCapturePhoto,
}

object SpriteButtonIntentInterpreter {
    private const val ACTION_SPRITE_BUTTON_UP = "com.android.action.ACTION_SPRITE_BUTTON_UP"
    private const val ACTION_SPRITE_BUTTON_LONG_PRESS = "com.android.action.ACTION_SPRITE_BUTTON_LONG_PRESS"

    fun interpret(action: String?): SpriteButtonAction {
        return when (action) {
            ACTION_SPRITE_BUTTON_UP,
            ACTION_SPRITE_BUTTON_LONG_PRESS -> SpriteButtonAction.TriggerCapturePhoto
            else -> SpriteButtonAction.None
        }
    }
}
