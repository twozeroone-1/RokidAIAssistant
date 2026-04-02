package com.example.rokidphone.service.session

import com.example.rokidphone.data.ApiSettings
import com.example.rokidphone.service.ai.AiServiceProvider

data class AiRequestSessionDecision(
    val createNewConversation: Boolean,
    val clearProviderHistory: Boolean,
)

object AiRequestSessionSupport {

    fun decide(settings: ApiSettings): AiRequestSessionDecision {
        return AiRequestSessionDecision(
            createNewConversation = settings.alwaysStartNewAiSession,
            clearProviderHistory = settings.alwaysStartNewAiSession,
        )
    }

    fun clearHistoryIfNeeded(
        settings: ApiSettings,
        services: List<AiServiceProvider?>,
    ) {
        if (!settings.alwaysStartNewAiSession) {
            return
        }
        val seenServices = HashSet<Int>()
        services.forEach { service ->
            if (service == null) {
                return@forEach
            }
            val identity = System.identityHashCode(service)
            if (seenServices.add(identity)) {
                service.clearHistory()
            }
        }
    }
}
