package com.itantra.mesh.voice

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * Thin wrapper around the offline Vosk engine. Each [SttModelOption] (one per supported
 * spoken language) is unpacked once from assets into its own internal storage folder and
 * reused for the life of the process; switching the active language just swaps which
 * already-unpacked (or freshly-unpacked) [Model] is used going forward.
 */
class VoskSpeechRecognizer(private val appContext: Context) {

    enum class State { UNINITIALIZED, LOADING, READY, LISTENING, ERROR }

    private val _state = MutableStateFlow(State.UNINITIALIZED)
    val stateFlow: StateFlow<State> = _state.asStateFlow()

    var state: State
        get() = _state.value
        private set(value) {
            _state.value = value
        }

    private val _currentModel = MutableStateFlow<SttModelOption?>(null)
    val currentModelFlow: StateFlow<SttModelOption?> = _currentModel.asStateFlow()
    val currentModelOption: SttModelOption? get() = _currentModel.value

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var onPartial: ((String) -> Unit)? = null
    private var onFinal: ((String) -> Unit)? = null
    private var onErrorCallback: ((Exception) -> Unit)? = null

    // Vosk finalizes and resets its internal buffer on every pause in speech.
    // Those finalized chunks arrive via onResult(), and must be accumulated here,
    // or everything said before the last pause is lost.
    private val accumulatedText = StringBuilder()

    val isReady: Boolean get() = model != null

    /** Call once, early (e.g. Application.onCreate), to unpack the initially selected model. */
    fun initialize(
        modelOption: SttModelOption,
        onReady: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        if (state == State.READY || state == State.LOADING) return
        loadModel(modelOption, onReady, onError)
    }

    /**
     * Switches to a different bundled language model at runtime. If currently
     * listening, that session is cancelled (without delivering a result) before the
     * switch proceeds.
     */
    fun switchModel(
        modelOption: SttModelOption,
        onReady: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        if (currentModelOption?.id == modelOption.id && model != null) {
            onReady()
            return
        }
        if (state == State.LISTENING) cancel()
        loadModel(modelOption, onReady, onError)
    }

    private fun loadModel(
        modelOption: SttModelOption,
        onReady: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        state = State.LOADING
        StorageService.unpack(
            appContext,
            modelOption.assetFolder,  // folder name under assets/
            modelOption.storageFolder, // target folder name under the app's files dir
            { unpacked ->
                model = unpacked
                _currentModel.value = modelOption
                state = State.READY
                onReady()
            },
            { exception ->
                state = State.ERROR
                Log.e(TAG, "Failed to unpack Vosk model '${modelOption.id}'", exception)
                onError(exception)
            }
        )
    }

    /** Push-to-talk "press": starts listening on the mic. */
    fun startListening(
        onFinalText: (String) -> Unit,
        onPartialText: ((String) -> Unit)? = null,
        onError: (Exception) -> Unit = {}
    ) {
        val loadedModel = model
        if (loadedModel == null) {
            onError(IllegalStateException("Vosk model not loaded yet"))
            return
        }
        if (state == State.LISTENING) return

        onFinal = onFinalText
        onPartial = onPartialText
        onErrorCallback = onError
        accumulatedText.clear()

        try {
            val recognizer = Recognizer(loadedModel, SAMPLE_RATE)
            val service = SpeechService(recognizer, SAMPLE_RATE)
            speechService = service
            state = State.LISTENING
            service.startListening(listener)
        } catch (e: Exception) {
            state = State.ERROR
            Log.e(TAG, "Could not start Vosk listening", e)
            onError(e)
        }
    }

    /** Push-to-talk "release": stops listening; the final transcript arrives via onFinalText. */
    fun stopListening() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
        if (state == State.LISTENING) state = State.READY
    }

    /** Cancel without delivering a result — e.g. the user drags off the button instead of releasing on it. */
    fun cancel() {
        speechService?.cancel()
        speechService?.shutdown()
        speechService = null
        accumulatedText.clear()
        if (state == State.LISTENING) state = State.READY
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            val partial = hypothesis?.let { extractText(it, key = "partial") } ?: ""
            val combined = joinNonEmpty(accumulatedText.toString(), partial)
            if (combined.isNotEmpty()) onPartial?.invoke(combined)
        }

        override fun onResult(hypothesis: String?) {
            // Finalized chunk from a pause mid-utterance. Must be kept, or everything
            // said before this pause would be lost when onFinalResult fires later.
            val chunk = hypothesis?.let { extractText(it, key = "text") } ?: ""
            if (chunk.isNotEmpty()) {
                if (accumulatedText.isNotEmpty()) accumulatedText.append(' ')
                accumulatedText.append(chunk)
            }
        }

        override fun onFinalResult(hypothesis: String?) {
            val lastChunk = hypothesis?.let { extractText(it, key = "text") } ?: ""
            val fullText = joinNonEmpty(accumulatedText.toString(), lastChunk)
            accumulatedText.clear()
            onFinal?.invoke(fullText)
        }

        override fun onError(exception: Exception?) {
            state = State.ERROR
            exception?.let { onErrorCallback?.invoke(it) }
        }

        override fun onTimeout() {
            stopListening()
        }
    }

    private fun extractText(hypothesisJson: String, key: String): String =
        try {
            JSONObject(hypothesisJson).optString(key, "").trim()
        } catch (e: Exception) {
            ""
        }

    private fun joinNonEmpty(a: String, b: String): String = when {
        a.isEmpty() -> b
        b.isEmpty() -> a
        else -> "$a $b"
    }

    companion object {
        private const val TAG = "VoskSpeechRecognizer"
        private const val SAMPLE_RATE = 16000.0f
    }
}
