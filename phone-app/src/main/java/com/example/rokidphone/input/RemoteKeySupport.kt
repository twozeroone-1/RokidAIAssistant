package com.example.rokidphone.input

import android.view.KeyEvent

private val BLOCKED_REMOTE_KEY_CODES = setOf(
    KeyEvent.KEYCODE_UNKNOWN,
    KeyEvent.KEYCODE_HOME,
    KeyEvent.KEYCODE_BACK,
    KeyEvent.KEYCODE_APP_SWITCH,
    KeyEvent.KEYCODE_VOLUME_UP,
    KeyEvent.KEYCODE_VOLUME_MUTE,
)

fun isBlockedRemoteKeyCode(keyCode: Int): Boolean = keyCode in BLOCKED_REMOTE_KEY_CODES

fun formatRemoteKeyCode(keyCode: Int?): String {
    if (keyCode == null) {
        return "Not set"
    }

    val rawName = runCatching { KeyEvent.keyCodeToString(keyCode) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() && it != "KEYCODE_UNKNOWN" }
        ?: fallbackRemoteKeyName(keyCode)

    return rawName
        .removePrefix("KEYCODE_")
        .split('_')
        .joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { char ->
                if (char.isLowerCase()) {
                    char.titlecase()
                } else {
                    char.toString()
                }
            }
        }
}

private fun fallbackRemoteKeyName(keyCode: Int): String {
    return when (keyCode) {
        KeyEvent.KEYCODE_CAMERA -> "KEYCODE_CAMERA"
        KeyEvent.KEYCODE_ENTER -> "KEYCODE_ENTER"
        KeyEvent.KEYCODE_DPAD_CENTER -> "KEYCODE_DPAD_CENTER"
        KeyEvent.KEYCODE_BACK -> "KEYCODE_BACK"
        KeyEvent.KEYCODE_HOME -> "KEYCODE_HOME"
        KeyEvent.KEYCODE_VOLUME_UP -> "KEYCODE_VOLUME_UP"
        KeyEvent.KEYCODE_VOLUME_DOWN -> "KEYCODE_VOLUME_DOWN"
        else -> "KEYCODE_$keyCode"
    }
}
