package com.example.rokidphone.service.ai

data class LiveSessionResumptionUpdate(
    val newHandle: String,
    val resumable: Boolean,
)

data class LiveGoAwayNotice(
    val timeLeft: String?,
)
