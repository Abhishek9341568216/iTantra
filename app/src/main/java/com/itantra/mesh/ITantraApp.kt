package com.itantra.mesh

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.itantra.mesh.data.MeshDatabase
import com.itantra.mesh.data.PreferencesRepository
import com.itantra.mesh.mesh.BluetoothMeshManager
import com.itantra.mesh.mesh.CompositeMeshTransport
import com.itantra.mesh.mesh.MeshRoutingEngine
import com.itantra.mesh.mesh.MeshSimulator
import com.itantra.mesh.mesh.SerialLinkManager
import com.itantra.mesh.voice.PiperTtsEngine
import com.itantra.mesh.voice.VoiceCatalog
import com.itantra.mesh.voice.VoskSpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ITantraApp : Application() {

    lateinit var voiceRecognizer: VoskSpeechRecognizer
        private set

    lateinit var ttsEngine: PiperTtsEngine
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var database: MeshDatabase
        private set

    lateinit var bluetoothMeshManager: BluetoothMeshManager
        private set

    lateinit var serialLinkManager: SerialLinkManager
        private set

    /** Convenience alias kept for existing Bluetooth-specific UI (e.g. mesh radar screen). */
    val meshManager: BluetoothMeshManager get() = bluetoothMeshManager

    /**
     * Fans traffic out over Bluetooth and Serial simultaneously. This is the sole [MeshTransport]
     * handed to [routingEngine] — Bluetooth mesh is always active, and Serial joins in
     * automatically whenever a device is connected, rather than the two being exclusive modes.
     */
    lateinit var compositeTransport: CompositeMeshTransport
        private set

    lateinit var routingEngine: MeshRoutingEngine
        private set

    lateinit var simulator: MeshSimulator
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannels()

        preferencesRepository = PreferencesRepository(this)
        database = MeshDatabase(this)
        bluetoothMeshManager = BluetoothMeshManager(this, preferencesRepository, appScope)
        serialLinkManager = SerialLinkManager(this, preferencesRepository, appScope)
        compositeTransport = CompositeMeshTransport(
            bluetooth = bluetoothMeshManager,
            serial = serialLinkManager,
            scope = appScope,
            initialSerialLinkEnabled = preferencesRepository.isSerialLinkEnabled
        )

        routingEngine = MeshRoutingEngine(compositeTransport, database, preferencesRepository, appScope)
        simulator = MeshSimulator(routingEngine, database, preferencesRepository, appScope)
        routingEngine.simulator = simulator

        voiceRecognizer = VoskSpeechRecognizer(this)
        voiceRecognizer.initialize(
            modelOption = VoiceCatalog.sttModelById(preferencesRepository.selectedSttModelId),
            onReady = { Log.d("ITantraApp", "Vosk model ready") },
            onError = { e -> Log.e("ITantraApp", "Vosk init failed", e) }
        )

        ttsEngine = PiperTtsEngine(this)
        ttsEngine.initialize(
            voice = VoiceCatalog.ttsVoiceById(preferencesRepository.selectedTtsVoiceId),
            onReady = { Log.d("ITantraApp", "Piper TTS voice ready") },
            onError = { e -> Log.e("ITantraApp", "Piper TTS init failed", e) }
        )

        // Speak every incoming mesh chat message automatically, as it arrives.
        routingEngine.onMessageReceived = { message ->
            if (preferencesRepository.isTtsEnabled && ttsEngine.isReady) {
                val utterance = if (preferencesRepository.isTtsAnnounceSender) {
                    "${message.senderName} says: ${message.content}"
                } else {
                    message.content
                }
                ttsEngine.speak(utterance)
            }
        }

        if (preferencesRepository.isSimulatorEnabled) {
            simulator.start()
        }
    }

    /**
     * Turns the wired USB-Serial link on or off, persisting the choice. Bluetooth mesh is
     * unaffected either way — this only controls whether the phone also talks to a connected
     * Pico over Serial at the same time.
     */
    fun setSerialLinkEnabled(enabled: Boolean) {
        preferencesRepository.isSerialLinkEnabled = enabled
        compositeTransport.setSerialLinkEnabled(enabled)
    }

    /** Switches the voice used to read incoming messages aloud, persisting the choice. */
    fun switchTtsVoice(voiceId: String, onReady: () -> Unit = {}, onError: (Exception) -> Unit = {}) {
        val voice = VoiceCatalog.ttsVoiceById(voiceId)
        preferencesRepository.selectedTtsVoiceId = voice.id
        ttsEngine.switchVoice(voice, onReady, onError)
    }

    /** Switches the spoken language push-to-talk recognizes, persisting the choice. */
    fun switchSttModel(modelId: String, onReady: () -> Unit = {}, onError: (Exception) -> Unit = {}) {
        val modelOption = VoiceCatalog.sttModelById(modelId)
        preferencesRepository.selectedSttModelId = modelOption.id
        voiceRecognizer.switchModel(modelOption, onReady, onError)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.mesh_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.mesh_notification_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "mesh_service_channel"
        lateinit var instance: ITantraApp
            private set
    }
}