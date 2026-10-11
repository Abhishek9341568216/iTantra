package com.itantra.mesh.mesh

import android.bluetooth.BluetoothSocket
import android.util.Log
import com.itantra.mesh.model.MeshPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

class PeerConnection(
    val peerAddress: String,
    private val socket: BluetoothSocket,
    private val scope: CoroutineScope,
    private val onPacketReceived: (PeerConnection, MeshPacket) -> Unit,
    private val onDisconnected: (PeerConnection) -> Unit
) {
    var peerNodeId: String? = null
    var peerDisplayName: String? = null

    private val sendChannel = Channel<MeshPacket>(Channel.BUFFERED)
    private var readJob: Job? = null
    private var writeJob: Job? = null

    @Volatile
    private var isClosing = false

    fun start() {
        readJob = scope.launch(Dispatchers.IO) {
            readLoop()
        }
        writeJob = scope.launch(Dispatchers.IO) {
            writeLoop()
        }
    }

    fun send(packet: MeshPacket) {
        if (!isClosing) {
            sendChannel.trySend(packet)
        }
    }

    fun close() {
        if (isClosing) return
        isClosing = true
        sendChannel.close()
        readJob?.cancel()
        writeJob?.cancel()
        try {
            socket.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing socket to $peerAddress", e)
        }
        onDisconnected(this)
    }

    private fun readLoop() {
        var inputStream: DataInputStream? = null
        try {
            inputStream = DataInputStream(socket.inputStream)
            while (scope.isActive && !isClosing) {
                // Length-prefixed framing: read 4-byte payload size
                val length = inputStream.readInt()
                if (length <= 0 || length > MAX_PACKET_SIZE) {
                    Log.w(TAG, "Invalid packet length from $peerAddress: $length")
                    break
                }
                val buffer = ByteArray(length)
                inputStream.readFully(buffer)

                val packet = MeshPacket.fromBytes(buffer)
                if (packet != null) {
                    if (peerNodeId == null) {
                        peerNodeId = packet.senderId
                        peerDisplayName = packet.senderName
                    }
                    onPacketReceived(this, packet)
                } else {
                    Log.w(TAG, "Failed to parse mesh packet from $peerAddress")
                }
            }
        } catch (e: IOException) {
            if (!isClosing) {
                Log.d(TAG, "PeerConnection disconnected ($peerAddress): ${e.message}")
            }
        } finally {
            close()
        }
    }

    private suspend fun writeLoop() {
        var outputStream: DataOutputStream? = null
        try {
            outputStream = DataOutputStream(socket.outputStream)
            for (packet in sendChannel) {
                val bytes = packet.toBytes()
                outputStream.writeInt(bytes.size)
                outputStream.write(bytes)
                outputStream.flush()
            }
        } catch (e: IOException) {
            if (!isClosing) {
                Log.d(TAG, "Error writing to peer ($peerAddress): ${e.message}")
            }
        } finally {
            close()
        }
    }

    companion object {
        private const val TAG = "PeerConnection"
        private const val MAX_PACKET_SIZE = 1024 * 1024 // 1 MB limit
    }
}
