package com.example.rokidphone.data

/**
 * Official Gemini prebuilt output voices.
 *
 * Source:
 * https://ai.google.dev/gemini-api/docs/speech-generation
 */
enum class GeminiLiveVoice(
    val voiceName: String,
    val styleLabel: String,
) {
    ZEPHYR("Zephyr", "Bright"),
    PUCK("Puck", "Upbeat"),
    CHARON("Charon", "Informative"),
    KORE("Kore", "Firm"),
    FENRIR("Fenrir", "Excitable"),
    LEDA("Leda", "Youthful"),
    ORUS("Orus", "Firm"),
    AOEDE("Aoede", "Breezy"),
    CALLIRRHOE("Callirrhoe", "Easy-going"),
    AUTONOE("Autonoe", "Bright"),
    ENCELADUS("Enceladus", "Breathy"),
    IAPETUS("Iapetus", "Clear"),
    UMBRIEL("Umbriel", "Easy-going"),
    ALGIEBA("Algieba", "Smooth"),
    DESPINA("Despina", "Smooth"),
    ERINOME("Erinome", "Clear"),
    ALGENIB("Algenib", "Gravelly"),
    RASALGETHI("Rasalgethi", "Informative"),
    LAOMEDEIA("Laomedeia", "Upbeat"),
    ACHERNAR("Achernar", "Soft"),
    ALNILAM("Alnilam", "Firm"),
    SCHEDAR("Schedar", "Even"),
    GACRUX("Gacrux", "Mature"),
    PULCHERRIMA("Pulcherrima", "Forward"),
    ACHIRD("Achird", "Friendly"),
    ZUBENELGENUBI("Zubenelgenubi", "Casual"),
    VINDEMIATRIX("Vindemiatrix", "Gentle"),
    SADACHBIA("Sadachbia", "Lively"),
    SADALTAGER("Sadaltager", "Knowledgeable"),
    SULAFAT("Sulafat", "Warm");

    val displayLabel: String
        get() = "$voiceName - $styleLabel"

    companion object {
        fun fromVoiceName(voiceName: String): GeminiLiveVoice {
            return entries.firstOrNull { it.voiceName.equals(voiceName, ignoreCase = true) }
                ?: AOEDE
        }
    }
}
