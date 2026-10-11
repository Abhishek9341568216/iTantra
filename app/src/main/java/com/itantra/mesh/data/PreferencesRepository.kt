package com.itantra.mesh.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.itantra.mesh.voice.VoiceCatalog
import java.util.UUID

class PreferencesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val nodeId: String
        get() {
            var id = prefs.getString(KEY_NODE_ID, null)
            if (id == null) {
                // Generate a friendly yet unique 8-character hex nodeId, e.g. "NODE-7A3B"
                val raw = UUID.randomUUID().toString().replace("-", "").uppercase()
                id = "NODE-${raw.substring(0, 4)}-${raw.substring(4, 8)}"
                prefs.edit().putString(KEY_NODE_ID, id).apply()
            }
            return id
        }

    var displayName: String
        get() {
            val defaultName = Build.MODEL.takeIf { !it.isNullOrBlank() } ?: "Android-Peer"
            return prefs.getString(KEY_DISPLAY_NAME, defaultName) ?: defaultName
        }
        set(value) {
            prefs.edit().putString(KEY_DISPLAY_NAME, value.trim()).apply()
        }

    var maxTtl: Int
        get() = prefs.getInt(KEY_MAX_TTL, 5)
        set(value) {
            prefs.edit().putInt(KEY_MAX_TTL, value).apply()
        }

    var isSimulatorEnabled: Boolean
        get() = prefs.getBoolean(KEY_SIMULATOR_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SIMULATOR_ENABLED, value).apply()
        }

    var isBackgroundServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_BG_SERVICE_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_BG_SERVICE_ENABLED, value).apply()
        }

    /** When true, incoming mesh chat messages are read aloud via the offline Piper TTS voice. */
    var isTtsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TTS_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_TTS_ENABLED, value).apply()
        }

    /** Whether to prefix spoken messages with the sender's name, e.g. "Alice says ...". */
    var isTtsAnnounceSender: Boolean
        get() = prefs.getBoolean(KEY_TTS_ANNOUNCE_SENDER, true)
        set(value) {
            prefs.edit().putBoolean(KEY_TTS_ANNOUNCE_SENDER, value).apply()
        }

    /** Which bundled Piper voice (and thus spoken language) reads incoming messages aloud. */
    var selectedTtsVoiceId: String
        get() = prefs.getString(KEY_TTS_VOICE_ID, VoiceCatalog.ttsVoices.first().id)
            ?: VoiceCatalog.ttsVoices.first().id
        set(value) {
            prefs.edit().putString(KEY_TTS_VOICE_ID, value).apply()
        }

    /** Which bundled Vosk model (and thus recognized spoken language) push-to-talk uses. */
    var selectedSttModelId: String
        get() = prefs.getString(KEY_STT_MODEL_ID, VoiceCatalog.sttModels.first().id)
            ?: VoiceCatalog.sttModels.first().id
        set(value) {
            prefs.edit().putString(KEY_STT_MODEL_ID, value).apply()
        }

    /**
     * Bluetooth mesh is always on. This additionally enables the wired USB-Serial link
     * (e.g. a Pi Pico W) as a second, simultaneous transport: whenever a serial device is
     * connected, packets flow over both Bluetooth and Serial at once instead of replacing one
     * with the other. Defaults to enabled so plugging in a Pico "just works".
     */
    var isSerialLinkEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERIAL_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SERIAL_ENABLED, value).apply()
        }

    /** Baud rate used when opening the USB-serial connection to the Pico. */
    var serialBaudRate: Int
        get() = prefs.getInt(KEY_SERIAL_BAUD_RATE, DEFAULT_SERIAL_BAUD_RATE)
        set(value) {
            prefs.edit().putInt(KEY_SERIAL_BAUD_RATE, value).apply()
        }

    /** Vendor ID of the last successfully connected serial device, for auto-reconnect. */
    var lastSerialVendorId: Int
        get() = prefs.getInt(KEY_SERIAL_LAST_VENDOR_ID, -1)
        set(value) {
            prefs.edit().putInt(KEY_SERIAL_LAST_VENDOR_ID, value).apply()
        }

    /** Product ID of the last successfully connected serial device, for auto-reconnect. */
    var lastSerialProductId: Int
        get() = prefs.getInt(KEY_SERIAL_LAST_PRODUCT_ID, -1)
        set(value) {
            prefs.edit().putInt(KEY_SERIAL_LAST_PRODUCT_ID, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "itantra_mesh_prefs"
        private const val KEY_NODE_ID = "node_id"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_MAX_TTL = "max_ttl"
        private const val KEY_SIMULATOR_ENABLED = "simulator_enabled"
        private const val KEY_BG_SERVICE_ENABLED = "bg_service_enabled"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_TTS_ANNOUNCE_SENDER = "tts_announce_sender"
        private const val KEY_TTS_VOICE_ID = "tts_voice_id"
        private const val KEY_STT_MODEL_ID = "stt_model_id"
        private const val KEY_SERIAL_ENABLED = "serial_link_enabled"
        private const val KEY_SERIAL_BAUD_RATE = "serial_baud_rate"
        private const val KEY_SERIAL_LAST_VENDOR_ID = "serial_last_vendor_id"
        private const val KEY_SERIAL_LAST_PRODUCT_ID = "serial_last_product_id"
        const val DEFAULT_SERIAL_BAUD_RATE = 115200
    }
}
