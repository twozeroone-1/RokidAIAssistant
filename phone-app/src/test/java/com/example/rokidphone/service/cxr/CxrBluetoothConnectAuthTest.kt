package com.example.rokidphone.service.cxr

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CxrBluetoothConnectAuthTest {

    @Test
    fun `build auth strips hyphens from client secret`() {
        val auth = buildCxrBluetoothConnectAuth(
            rawClientSecret = "fd9ec29b-1aea-11f1-961e-043f72fdb9c8",
            snBytes = byteArrayOf(1, 2, 3)
        )

        assertThat(auth).isNotNull()
        assertThat(auth?.clientSecret).isEqualTo("fd9ec29b1aea11f1961e043f72fdb9c8")
        assertThat(auth?.snBytes?.toList()).isEqualTo(listOf<Byte>(1, 2, 3))
    }

    @Test
    fun `build auth returns null when secret blank`() {
        val auth = buildCxrBluetoothConnectAuth(
            rawClientSecret = "   ",
            snBytes = byteArrayOf(1)
        )

        assertThat(auth).isNull()
    }

    @Test
    fun `build auth returns null when sn bytes missing`() {
        val auth = buildCxrBluetoothConnectAuth(
            rawClientSecret = "fd9ec29b-1aea-11f1-961e-043f72fdb9c8",
            snBytes = null
        )

        assertThat(auth).isNull()
    }
}
