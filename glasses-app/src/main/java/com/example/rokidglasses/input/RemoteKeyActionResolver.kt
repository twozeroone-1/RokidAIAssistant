package com.example.rokidglasses.input

data class RemoteKeySettings(
    val recordKeyCode: Int? = null,
    val cameraKeyCode: Int? = null,
)

enum class RemoteKeyAction {
    None,
    ToggleRecording,
    CapturePhoto,
}

class RemoteKeyActionResolver(
    private val settings: RemoteKeySettings,
) {
    fun resolve(keyCode: Int): RemoteKeyAction {
        return when {
            settings.recordKeyCode == keyCode -> RemoteKeyAction.ToggleRecording
            settings.cameraKeyCode == keyCode -> RemoteKeyAction.CapturePhoto
            else -> RemoteKeyAction.None
        }
    }
}
