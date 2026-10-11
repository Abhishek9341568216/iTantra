package com.itantra.mesh.voice

/**
 * Catalog of offline TTS voices and STT (speech-to-text) language models bundled with
 * the app, plus a data class describing each. Adding support for a new spoken language
 * is just a matter of dropping the model assets in and adding an entry below -- no
 * other code needs to change.
 *
 *  TTS (Piper voice, run through the sherpa-onnx runtime):
 *   1. Place a self-contained voice folder under `app/src/main/assets/<assetDir>/`
 *      containing the voice's `.onnx` model, `tokens.txt`, and an `espeak-ng-data/` dir.
 *   2. Add a [TtsVoiceOption] below pointing at that folder and model file.
 *
 *  STT (Vosk):
 *   1. Place the unzipped Vosk model folder under `app/src/main/assets/<assetFolder>/`.
 *   2. Add a [SttModelOption] below pointing at that folder, with a [SttModelOption.storageFolder]
 *      that's unique across all STT entries (it's the unpack target on internal storage).
 *
 * An entry whose assets haven't been bundled yet will simply surface a clear load error
 * when the user selects it in Settings, rather than crashing the app.
 */

data class TtsVoiceOption(
    val id: String,
    val displayName: String,
    val languageCode: String,
    val languageLabel: String,
    val assetDir: String,
    val modelFileName: String
)

data class SttModelOption(
    val id: String,
    val displayName: String,
    val languageCode: String,
    val languageLabel: String,
    val assetFolder: String,
    val storageFolder: String
)

object VoiceCatalog {

    val ttsVoices: List<TtsVoiceOption> = listOf(
        TtsVoiceOption(
            id = "piper-en-us-amy-low",
            displayName = "Amy",
            languageCode = "en-US",
            languageLabel = "English (US)",
            assetDir = "piper-model",
            modelFileName = "en_US-amy-low.onnx"
        ),
        TtsVoiceOption(
            id = "piper-en-gb-alan-medium",
            displayName = "Alan",
            languageCode = "en-GB",
            languageLabel = "English (UK)",
            assetDir = "piper-model-en-gb",
            modelFileName = "en_US-amy-low.onnx"
        ),
        TtsVoiceOption(
            id = "piper-hi-in-pratham-medium",
            displayName = "Pratham",
            languageCode = "hi-IN",
            languageLabel = "Hindi",
            assetDir = "vits-piper-hi_IN-pratham-medium",
            modelFileName = "hi_IN-pratham-medium.onnx"
        ),
    )

    val sttModels: List<SttModelOption> = listOf(
        SttModelOption(
            id = "vosk-en-in",
            displayName = "English (IN)",
            languageCode = "en-IN",
            languageLabel = "English (IN)",
            assetFolder = "vosk-model-small-en-in-0.4",
            storageFolder = "model-en-in"
        ),
        SttModelOption(
            id = "vosk-gu-in",
            displayName = "Gujrati",
            languageCode = "gu-IN",
            languageLabel = "Gujrati",
            assetFolder = "vosk-model-small-gu-0.42",
            storageFolder = "model-gu-in"
        ),
        SttModelOption(
            id = "vosk-te-in",
            displayName = "Telugu",
            languageCode = "gu-TE",
            languageLabel = "Telugu",
            assetFolder = "vosk-model-small-te-0.42",
            storageFolder = "model-te-in"
        ),
        SttModelOption(
            id = "vosk-hi",
            displayName = "Hindi",
            languageCode = "hi-IN",
            languageLabel = "Hindi",
            assetFolder = "vosk-model-small-hi-0.22",
            storageFolder = "model-hi"
        ),
    )

    fun ttsVoiceById(id: String?): TtsVoiceOption =
        ttsVoices.firstOrNull { it.id == id } ?: ttsVoices.first()

    fun sttModelById(id: String?): SttModelOption =
        sttModels.firstOrNull { it.id == id } ?: sttModels.first()
}
