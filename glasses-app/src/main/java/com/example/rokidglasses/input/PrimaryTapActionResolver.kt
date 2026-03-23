package com.example.rokidglasses.input

data class TapUiSnapshot(
    val isPaginated: Boolean,
    val currentPage: Int,
    val totalPages: Int,
    val isConnected: Boolean,
)

enum class PrimaryTapAction {
    NextPage,
    PrimaryAction,
    ShowDeviceSelector,
}

class PrimaryTapActionResolver {
    fun resolve(snapshot: TapUiSnapshot): PrimaryTapAction {
        return when {
            snapshot.isPaginated && snapshot.currentPage < snapshot.totalPages - 1 -> PrimaryTapAction.NextPage
            snapshot.isPaginated -> PrimaryTapAction.PrimaryAction
            snapshot.isConnected -> PrimaryTapAction.PrimaryAction
            else -> PrimaryTapAction.ShowDeviceSelector
        }
    }
}
