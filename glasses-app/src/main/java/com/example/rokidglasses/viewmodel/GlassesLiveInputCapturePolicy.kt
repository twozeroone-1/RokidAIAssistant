package com.example.rokidglasses.viewmodel

import com.example.rokidcommon.protocol.LiveControlInputSource

fun shouldCaptureLiveInputFromGlasses(
    isBluetoothConnected: Boolean,
    liveModeEnabled: Boolean,
    sessionActive: Boolean,
    inputSource: LiveControlInputSource,
): Boolean {
    return isBluetoothConnected &&
        liveModeEnabled &&
        sessionActive &&
        inputSource == LiveControlInputSource.GLASSES
}
