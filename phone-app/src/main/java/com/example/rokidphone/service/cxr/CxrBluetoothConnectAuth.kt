package com.example.rokidphone.service.cxr

internal data class CxrBluetoothConnectAuth(
    val snBytes: ByteArray,
    val clientSecret: String
)

internal fun buildCxrBluetoothConnectAuth(
    rawClientSecret: String,
    snBytes: ByteArray?
): CxrBluetoothConnectAuth? {
    val normalizedSecret = rawClientSecret
        .trim()
        .replace("-", "")
        .takeIf { it.isNotEmpty() }
    val normalizedSnBytes = snBytes?.takeIf { it.isNotEmpty() }

    return if (normalizedSecret != null && normalizedSnBytes != null) {
        CxrBluetoothConnectAuth(
            snBytes = normalizedSnBytes,
            clientSecret = normalizedSecret
        )
    } else {
        null
    }
}
