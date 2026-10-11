package com.itantra.mesh.mesh

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.itantra.mesh.data.PreferencesRepository
import com.itantra.mesh.model.MeshNode
import com.itantra.mesh.model.MeshPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Wired USB-serial transport for talking to a single external device — in practice a
 * Raspberry Pi Pico W running the iTantra Mesh firmware, connected via USB (CDC-ACM).
 *
 * There is no mesh here on the phone's side: exactly one serial peer is ever connected, no
 * discovery/flooding/relaying happens on-device, and whatever real mesh/radio routing is needed
 * beyond this single hop is entirely the Pico's responsibility. The phone simply:
 *  - reads newline-delimited JSON-encoded [MeshPacket]s from the serial port and feeds them into
 *    [MeshRoutingEngine], and
 *  - writes newline-delimited JSON-encoded [MeshPacket]s out to the serial port whenever a
 *    message needs to leave the phone.
 *
 * Wire format contract (must match the Pico firmware):
 *   One [MeshPacket.toJson] object per line, terminated by '\n'. UTF-8. No length prefix — the
 *   framing is simply "one JSON object per line" so it is trivial to produce/consume from
 *   MicroPython/CircuitPython on the Pico side.
 *
 * Requires the `com.github.mik3y:usb-serial-for-android` library and the USB host feature; see
 * the notes at the bottom of this file for the manifest/gradle additions this needs.
 */
class SerialLinkManager(
    private val context: Context,
    private val prefs: PreferencesRepository,
    private val scope: CoroutineScope
) : MeshTransport {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private var port: UsbSerialPort? = null
    private var ioManager: SerialInputOutputManager? = null

    // Raw bytes, NOT decoded text. USB serial hands data over in whatever chunks the driver
    // feels like; a multi-byte UTF-8 character (Hindi, Chinese, emoji, etc.) can be split
    // across two separate onNewData() callbacks. Decoding each chunk independently would turn
    // the split character into replacement/"unknown" characters on both sides of the split, so
    // we must buffer raw bytes and only decode once a complete line has arrived.
    private var readBuffer = ByteArray(0)
    private val readBufferLock = Any()

    private val _connectedPeersCount = MutableStateFlow(0)
    override val connectedPeersCount: StateFlow<Int> = _connectedPeersCount.asStateFlow()

    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<SerialDeviceInfo>>(emptyList())
    val availableDevices: StateFlow<List<SerialDeviceInfo>> = _availableDevices.asStateFlow()

    override var onPacketReceivedFromPeer: ((MeshPacket, String) -> Unit)? = null
    override var onPeerConnected: ((MeshNode) -> Unit)? = null
    override var onPeerDisconnected: ((String) -> Unit)? = null

    private var wantsAutoConnect = false
    private var permissionReceiverRegistered = false

    private val usbStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    val device = getDeviceExtra(intent)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    refreshAvailableDevices()
                    if (granted && device != null) {
                        openDevice(device)
                    } else {
                        Log.w(TAG, "USB permission denied for ${device?.deviceName}")
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    refreshAvailableDevices()
                    if (wantsAutoConnect && port == null) {
                        connectToPreferredOrFirstAvailable()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = getDeviceExtra(intent)
                    refreshAvailableDevices()
                    if (device != null && device.deviceName == currentDeviceName) {
                        handleDisconnected()
                    }
                }
            }
        }
    }

    private var currentDeviceName: String? = null

    /** Register for USB attach/detach/permission broadcasts. Call once, e.g. from [start]. */
    private fun registerReceiverIfNeeded() {
        if (permissionReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbStateReceiver, filter)
        }
        permissionReceiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!permissionReceiverRegistered) return
        try {
            context.unregisterReceiver(usbStateReceiver)
        } catch (e: IllegalArgumentException) {
            // Already unregistered
        }
        permissionReceiverRegistered = false
    }

    /** Lists every USB device currently plugged in that a known serial driver recognizes. */
    fun refreshAvailableDevices(): List<SerialDeviceInfo> {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        val infos = drivers.map { driver ->
            SerialDeviceInfo(
                deviceName = driver.device.deviceName,
                vendorId = driver.device.vendorId,
                productId = driver.device.productId,
                displayLabel = driver.device.productName
                    ?: "USB Serial (${driver.device.vendorId.toHex()}:${driver.device.productId.toHex()})",
                hasPermission = usbManager.hasPermission(driver.device)
            )
        }
        _availableDevices.value = infos
        return infos
    }

    /** Begin listening for USB devices and try to auto-connect to a known/first device. */
    override fun start() {
        wantsAutoConnect = true
        registerReceiverIfNeeded()
        refreshAvailableDevices()
        connectToPreferredOrFirstAvailable()
    }

    override fun stop() {
        wantsAutoConnect = false
        closePort()
        unregisterReceiver()
    }

    /** Try the last-remembered device first, otherwise fall back to whatever is plugged in. */
    private fun connectToPreferredOrFirstAvailable() {
        val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (drivers.isEmpty()) return

        val remembered = prefs.lastSerialVendorId to prefs.lastSerialProductId
        val preferred = drivers.firstOrNull {
            it.device.vendorId == remembered.first && it.device.productId == remembered.second
        }
        connectToDriver(preferred ?: drivers.first())
    }

    /** Explicitly connect to a device the user picked from [availableDevices]. */
    fun connect(deviceInfo: SerialDeviceInfo) {
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            .firstOrNull { it.device.deviceName == deviceInfo.deviceName } ?: return
        connectToDriver(driver)
    }

    private fun connectToDriver(driver: UsbSerialDriver) {
        val device = driver.device
        if (!usbManager.hasPermission(device)) {
            requestPermission(device)
            return
        }
        openDevice(device)
    }

    private fun requestPermission(device: UsbDevice) {
        registerReceiverIfNeeded()
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun openDevice(device: UsbDevice) {
        val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
            .firstOrNull { it.device.deviceName == device.deviceName } ?: return

        val connection = usbManager.openDevice(driver.device)
        if (connection == null) {
            Log.e(TAG, "Failed to open USB connection to ${driver.device.deviceName}")
            return
        }

        val newPort = driver.ports.firstOrNull()
        if (newPort == null) {
            Log.e(TAG, "Device ${driver.device.deviceName} exposes no serial ports")
            connection.close()
            return
        }

        try {
            newPort.open(connection)
            newPort.setParameters(
                prefs.serialBaudRate,
                UsbSerialPort.DATABITS_8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            // Many CDC-ACM devices (Arduino, Pico, etc.) won't accept writes — and some won't
            // even start transmitting — until the host raises DTR/RTS. Some drivers (e.g. raw
            // FTDI in certain modes) throw UnsupportedOperationException for these; that's fine
            // to ignore since it means the device doesn't need/support them.
            try {
                newPort.setDTR(true)
                newPort.setRTS(true)
            } catch (e: UnsupportedOperationException) {
                Log.d(TAG, "Driver does not support DTR/RTS control, continuing without it")
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to open/configure serial port", e)
            try { newPort.close() } catch (ignored: IOException) {}
            return
        }

        port = newPort
        currentDeviceName = device.deviceName
        prefs.lastSerialVendorId = device.vendorId
        prefs.lastSerialProductId = device.productId
        readBuffer = ByteArray(0)

        ioManager = SerialInputOutputManager(newPort, object : SerialInputOutputManager.Listener {
            override fun onNewData(data: ByteArray) {
                handleIncomingBytes(data)
            }

            override fun onRunError(e: Exception) {
                Log.w(TAG, "Serial IO error: ${e.message}")
                scope.launch(Dispatchers.Main) { handleDisconnected() }
            }
        })
        ioExecutor.submit(ioManager)

        _isReady.value = true
        _connectedPeersCount.value = 1

        val node = MeshNode(
            nodeId = SERIAL_PEER_ADDRESS,
            displayName = device.productName ?: "Pi Pico (Serial)",
            isDirectNeighbor = true,
            hopDistance = 1,
            lastSeenTimestamp = System.currentTimeMillis()
        )
        onPeerConnected?.invoke(node)
        Log.i(TAG, "Serial link open on ${device.deviceName} @ ${prefs.serialBaudRate} baud")
    }

    private fun handleIncomingBytes(data: ByteArray) {
        synchronized(readBufferLock) {
            readBuffer += data

            var newlineIndex = readBuffer.indexOfByte(NEWLINE_BYTE)
            while (newlineIndex >= 0) {
                val lineBytes = readBuffer.copyOfRange(0, newlineIndex)
                readBuffer = readBuffer.copyOfRange(newlineIndex + 1, readBuffer.size)

                // Decode as UTF-8 only now that we have every byte of the line — this is what
                // keeps multi-byte characters intact even when a USB read split them.
                val line = String(lineBytes, Charsets.UTF_8).trim()
                if (line.isNotEmpty()) {
                    Log.d(TAG, "Serial RX line: $line")
                    val parsed = MeshPacket.fromJson(line)
                    if (parsed != null) {
                        // The Pico has no RTC, so any timestamp it sends is just "ms since boot",
                        // not a real epoch time. Trusting it would sort these messages ahead of
                        // everything else. Stamp with receive time on the phone instead.
                        val packet = parsed.copy(timestamp = System.currentTimeMillis())
                        onPacketReceivedFromPeer?.invoke(packet, SERIAL_PEER_ADDRESS)
                    } else {
                        Log.w(TAG, "Failed to parse serial packet line: $line")
                    }
                }
                newlineIndex = readBuffer.indexOfByte(NEWLINE_BYTE)
            }
        }
    }

    private fun ByteArray.indexOfByte(target: Byte): Int {
        for (i in indices) {
            if (this[i] == target) return i
        }
        return -1
    }

    private fun handleDisconnected() {
        closePort()
        onPeerDisconnected?.invoke(SERIAL_PEER_ADDRESS)
        // If the user still wants a serial connection, keep watching for the device to come back
        // (e.g. after it's unplugged/replugged or the Pico reboots).
    }

    private fun closePort() {
        ioManager?.stop()
        ioManager = null
        try {
            port?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing serial port", e)
        }
        port = null
        currentDeviceName = null
        _isReady.value = false
        _connectedPeersCount.value = 0
    }

    fun disconnect() {
        handleDisconnected()
    }

    val isConnected: Boolean get() = port != null

    /** Sends [packet] out over the wire. There is only ever one peer, so [excludeAddress]
     * simply suppresses the send if it refers to the serial link itself (i.e. this packet just
     * arrived from the Pico and shouldn't be echoed straight back to it). */
    override fun broadcastPacket(packet: MeshPacket, excludeAddress: String?) {
        if (excludeAddress == SERIAL_PEER_ADDRESS) return
        writePacket(packet)
    }

    override fun sendPacketToNode(targetNodeId: String, packet: MeshPacket): Boolean {
        // No mesh routing table on the phone in serial mode: everything simply goes down the
        // one wire, and the Pico decides what to do with it.
        return writePacket(packet)
    }

    private fun writePacket(packet: MeshPacket): Boolean {
        val activePort = port ?: return false
        return try {
            val line = (packet.toJson() + "\n").toByteArray(Charsets.UTF_8)
            activePort.write(line, WRITE_TIMEOUT_MS)
            true
        } catch (e: IOException) {
            Log.w(TAG, "Failed to write to serial port: ${e.message}")
            scope.launch(Dispatchers.Main) { handleDisconnected() }
            false
        }
    }

    override fun getActivePeerAddresses(): List<String> =
        if (isConnected) listOf(SERIAL_PEER_ADDRESS) else emptyList()

    private fun getDeviceExtra(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }

    private fun Int.toHex(): String = String.format("%04X", this)

    data class SerialDeviceInfo(
        val deviceName: String,
        val vendorId: Int,
        val productId: Int,
        val displayLabel: String,
        val hasPermission: Boolean
    )

    companion object {
        private const val TAG = "SerialLinkManager"
        private const val ACTION_USB_PERMISSION = "com.itantra.mesh.USB_PERMISSION"
        private const val WRITE_TIMEOUT_MS = 1000
        private const val NEWLINE_BYTE: Byte = '\n'.code.toByte()

        /** Fixed synthetic "peer address" for the single serial link (there's only ever one). */
        const val SERIAL_PEER_ADDRESS = "SERIAL"
    }
}

/*
 * Setup required outside this file (not included in this source-only checkout, add manually):
 *
 * 1. build.gradle (app module) dependency:
 *      implementation("com.github.mik3y:usb-serial-for-android:3.7.0")
 *    and in settings.gradle (or root build.gradle) repositories:
 *      maven { url = uri("https://jitpack.io") }
 *
 * 2. AndroidManifest.xml:
 *      <uses-feature android:name="android.hardware.usb.host" android:required="false" />
 *
 *    Optionally, to auto-launch the app when the Pico is plugged in, add to MainActivity's
 *    <activity> tag:
 *      <intent-filter>
 *          <action android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED" />
 *      </intent-filter>
 *      <meta-data
 *          android:name="android.hardware.usb.action.USB_DEVICE_ATTACHED"
 *          android:resource="@xml/usb_device_filter" />
 *
 *    with res/xml/usb_device_filter.xml listing the Pico's vendor/product IDs, e.g. for the
 *    default Pico W CDC vendor/product ID (RP2040 boot media claims a different one; if you
 *    flash a custom CDC composite firmware, adjust these to match):
 *      <resources>
 *          <usb-device vendor-id="11914" product-id="5" /> <!-- 0x2E8A:0x0005, example -->
 *      </resources>
 *
 *    This filter is optional — SerialLinkManager also happily works if the user launches the
 *    app first and plugs the Pico in afterwards, or picks it manually from Settings.
 */