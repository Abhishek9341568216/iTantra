package com.itantra.mesh.voice

import android.content.Context
import android.content.res.AssetManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Offline text-to-speech powered by a Piper voice running through the sherpa-onnx
 * runtime (VITS ONNX model + tokens + espeak-ng-data). Each [TtsVoiceOption] is a
 * self-contained folder unpacked from assets to internal storage once (espeak-ng-data
 * can't be read directly out of the compressed APK), then loaded from real filesystem
 * paths on every subsequent run. Multiple voices can be unpacked side by side, keyed by
 * voice id, so switching voices/languages at runtime doesn't require re-unpacking.
 *
 * Incoming utterances are queued and spoken one at a time on a dedicated worker so
 * that several mesh messages arriving in quick succession don't talk over each other.
 */
class PiperTtsEngine(private val appContext: Context) {

    enum class State { UNINITIALIZED, LOADING, READY, SPEAKING, ERROR }

    private val _state = MutableStateFlow(State.UNINITIALIZED)
    val stateFlow: StateFlow<State> = _state.asStateFlow()

    var state: State
        get() = _state.value
        private set(value) {
            _state.value = value
        }

    private val _currentVoice = MutableStateFlow<TtsVoiceOption?>(null)
    val currentVoiceFlow: StateFlow<TtsVoiceOption?> = _currentVoice.asStateFlow()
    val currentVoice: TtsVoiceOption? get() = _currentVoice.value

    private var tts: OfflineTts? = null
    private var audioTrack: AudioTrack? = null
    private val engineScope = CoroutineScope(Dispatchers.Default + Job())
    private var speechLoopStarted = false

    // Unbounded queue of text to be spoken, drained sequentially by speechLoop().
    private val speechQueue = Channel<String>(capacity = Channel.UNLIMITED)

    val isReady: Boolean get() = tts != null

    /** Call once, early (e.g. Application.onCreate), to load the initially selected voice. */
    fun initialize(
        voice: TtsVoiceOption,
        onReady: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        if (state == State.READY || state == State.LOADING) return
        state = State.LOADING
        engineScope.launch {
            loadVoiceInternal(voice, onReady, onError, startLoop = true)
        }
    }

    /**
     * Switches to a different bundled voice (e.g. a different language) at runtime.
     * Anything mid-speech or still queued in the old voice is dropped so the switch
     * takes effect immediately.
     */
    fun switchVoice(
        voice: TtsVoiceOption,
        onReady: () -> Unit = {},
        onError: (Exception) -> Unit = {}
    ) {
        if (currentVoice?.id == voice.id && tts != null) {
            onReady()
            return
        }
        stopSpeakingAndClear()
        state = State.LOADING
        engineScope.launch {
            loadVoiceInternal(voice, onReady, onError, startLoop = false)
        }
    }

    private suspend fun loadVoiceInternal(
        voice: TtsVoiceOption,
        onReady: () -> Unit,
        onError: (Exception) -> Unit,
        startLoop: Boolean
    ) {
        try {
            // espeak-ng-data is a directory of many small files that sherpa-onnx's
            // espeak backend opens with plain fopen() internally -- it cannot stream
            // those straight out of the compressed APK assets (AssetManager-based
            // loading aborts the process). So we unpack the whole voice folder to
            // internal storage once per voice id, and always load from real filesystem
            // paths.
            val localModelDir = File(appContext.filesDir, "piper-voices/${voice.id}")
            val markerFile = File(localModelDir, ".unpacked")
            if (!markerFile.exists()) {
                localModelDir.deleteRecursively()
                copyAssetPath(appContext.assets, voice.assetDir, localModelDir)
                markerFile.createNewFile()
            }

            val config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(
                    vits = OfflineTtsVitsModelConfig(
                        model = File(localModelDir, voice.modelFileName).absolutePath,
                        tokens = File(localModelDir, "tokens.txt").absolutePath,
                        dataDir = File(localModelDir, "espeak-ng-data").absolutePath,
                        lengthScale = 1.0f,
                        noiseScale = 0.667f,
                        noiseScaleW = 0.8f
                    ),
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                ),
                maxNumSentences = 1
            )
            // No assetManager passed -> loads from the real filesystem paths above,
            // instead of the asset-streaming path that crashes on espeak-ng-data.
            val newTts = OfflineTts(config = config)

            // Only release the old engine once the new one has loaded successfully,
            // so a failed voice switch leaves the previous voice fully usable.
            tts?.release()
            tts = newTts
            _currentVoice.value = voice
            state = State.READY
            withContext(Dispatchers.Main) { onReady() }

            if (startLoop && !speechLoopStarted) {
                speechLoopStarted = true
                speechLoop()
            }
        } catch (e: Exception) {
            state = State.ERROR
            Log.e(TAG, "Failed to load Piper TTS voice '${voice.id}'", e)
            withContext(Dispatchers.Main) { onError(e) }
        }
    }

    /**
     * Recursively copies an assets/ path (file or directory) to [destFile] on the
     * real filesystem. AssetManager doesn't expose an "is this a directory" check, so
     * we try opening it as a file first and fall back to listing it as a directory.
     */
    private fun copyAssetPath(assetManager: AssetManager, assetPath: String, destFile: File) {
        try {
            assetManager.open(assetPath).use { input ->
                destFile.parentFile?.mkdirs()
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: IOException) {
            // Not a readable file at this path -- treat it as a directory and recurse.
            val children = assetManager.list(assetPath)
            if (children.isNullOrEmpty()) return
            destFile.mkdirs()
            for (child in children) {
                copyAssetPath(assetManager, "$assetPath/$child", File(destFile, child))
            }
        }
    }

    /**
     * Enqueue text to be spoken aloud. Safe to call from any thread; if the engine
     * isn't ready yet or TTS is disabled, callers should check [isReady] first.
     */
    fun speak(text: String) {
        if (text.isBlank()) return
        speechQueue.trySend(text)
    }

    /** Stops whatever is currently playing and drops anything still queued. */
    fun stopSpeakingAndClear() {
        while (speechQueue.tryReceive().isSuccess) { /* drain */ }
        audioTrack?.let {
            try {
                it.pause()
                it.flush()
                it.stop()
            } catch (e: Exception) {
                // AudioTrack wasn't in a playable state; nothing to clean up.
            }
        }
    }

    fun release() {
        stopSpeakingAndClear()
        audioTrack?.release()
        audioTrack = null
        tts?.release()
        tts = null
        _currentVoice.value = null
        engineScope.launch { }.cancel()
        state = State.UNINITIALIZED
    }

    private suspend fun speechLoop() {
        for (text in speechQueue) {
            val engine = tts ?: continue
            try {
                state = State.SPEAKING
                val audio = engine.generate(text = text, sid = 0, speed = 1.0f)
                playSamples(audio)
            } catch (e: Exception) {
                Log.e(TAG, "Piper synthesis/playback failed for: \"$text\"", e)
            } finally {
                if (state == State.SPEAKING) state = State.READY
            }
        }
    }

    private fun playSamples(audio: GeneratedAudio) {
        val samples = audio.samples
        val sampleRate = audio.sampleRate
        if (samples.isEmpty() || sampleRate <= 0) return

        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val encoding = AudioFormat.ENCODING_PCM_FLOAT
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, encoding)
        val bufferSizeBytes = maxOf(minBufferSize, samples.size * 4)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .build()
            )
            .setBufferSizeInBytes(bufferSizeBytes)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack = track
        try {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            track.play()

            // MODE_STATIC playback runs on its own thread inside AudioTrack; block this
            // (already background) coroutine dispatcher thread until it's done so the
            // queue doesn't advance to the next message mid-utterance.
            val durationMs = (samples.size.toLong() * 1000L / sampleRate.toLong()) + 150L
            Thread.sleep(durationMs)
        } finally {
            try {
                track.stop()
            } catch (e: Exception) {
                // Already stopped/released; ignore.
            }
            track.release()
            if (audioTrack === track) audioTrack = null
        }
    }

    companion object {
        private const val TAG = "PiperTtsEngine"
    }
}
