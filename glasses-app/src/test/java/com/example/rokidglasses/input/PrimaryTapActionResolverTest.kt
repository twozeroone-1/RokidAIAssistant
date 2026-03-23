package com.example.rokidglasses.input

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PrimaryTapActionResolverTest {

    private val resolver = PrimaryTapActionResolver()

    @Test
    fun `paginated non-final page advances`() {
        assertThat(
            resolver.resolve(
                TapUiSnapshot(
                    isPaginated = true,
                    currentPage = 0,
                    totalPages = 3,
                    isConnected = false,
                )
            )
        ).isEqualTo(PrimaryTapAction.NextPage)
    }

    @Test
    fun `paginated final page triggers primary action`() {
        assertThat(
            resolver.resolve(
                TapUiSnapshot(
                    isPaginated = true,
                    currentPage = 2,
                    totalPages = 3,
                    isConnected = false,
                )
            )
        ).isEqualTo(PrimaryTapAction.PrimaryAction)
    }

    @Test
    fun `connected non paginated triggers primary action`() {
        assertThat(
            resolver.resolve(
                TapUiSnapshot(
                    isPaginated = false,
                    currentPage = 0,
                    totalPages = 1,
                    isConnected = true,
                )
            )
        ).isEqualTo(PrimaryTapAction.PrimaryAction)
    }

    @Test
    fun `disconnected non paginated shows device selector`() {
        assertThat(
            resolver.resolve(
                TapUiSnapshot(
                    isPaginated = false,
                    currentPage = 0,
                    totalPages = 1,
                    isConnected = false,
                )
            )
        ).isEqualTo(PrimaryTapAction.ShowDeviceSelector)
    }
}
