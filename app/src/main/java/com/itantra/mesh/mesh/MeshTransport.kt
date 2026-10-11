package com.itantra.mesh.mesh

import com.itantra.mesh.model.MeshNode
import com.itantra.mesh.model.MeshPacket
import kotlinx.coroutines.flow.StateFlow

/**
 * Common contract for whatever link carries [MeshPacket]s between this device and the outside
 * world. [MeshRoutingEngine] only ever talks to this interface, so it doesn't need to know or
 * care which physical transport is active.
 *
 * Two implementations exist:
 *  - [BluetoothMeshManager]: many simultaneous Bluetooth peers, forming a real multi-hop mesh
 *    with discovery, flooding and relaying handled on-phone.
 *  - [SerialLinkManager]: exactly one wired USB-serial peer (e.g. a Raspberry Pi Pico W). There
 *    is no mesh here from the phone's perspective — the phone just hands packets to the single
 *    connected device and receives whatever comes back. Any actual mesh/radio routing beyond
 *    that single hop is handled entirely by the firmware on the connected device.
 */
interface MeshTransport {

    /** Number of directly connected peers (0 or 1 for [SerialLinkManager], 0+ for Bluetooth). */
    val connectedPeersCount: StateFlow<Int>

    /** True once the transport is actively listening/connected and able to carry traffic. */
    val isReady: StateFlow<Boolean>

    /**
     * Fired whenever a [MeshPacket] arrives from a peer. The second argument is an opaque
     * "peer address" identifying which link it came in on (a Bluetooth MAC address, or the
     * fixed serial link id) — used only for loop avoidance (excluding the ingress link when
     * relaying), never displayed to the user directly.
     */
    var onPacketReceivedFromPeer: ((MeshPacket, String) -> Unit)?

    /** Fired when a peer becomes known/connected, so it can be recorded in the node database. */
    var onPeerConnected: ((MeshNode) -> Unit)?

    /** Fired when a peer disconnects, identified by its nodeId (if known). */
    var onPeerDisconnected: ((String) -> Unit)?

    /** Begin listening/connecting. Safe to call multiple times. */
    fun start()

    /** Stop and release all resources. Safe to call multiple times. */
    fun stop()

    /**
     * Send [packet] to every connected peer except [excludeAddress] (used to avoid bouncing a
     * packet back the way it came). For [SerialLinkManager] this simply writes to the single
     * serial peer, unless that peer is the excluded one.
     */
    fun broadcastPacket(packet: MeshPacket, excludeAddress: String? = null)

    /**
     * Attempt to deliver [packet] directly to [targetNodeId]. Returns true if a direct link to
     * that node was found and used, false if it had to fall back to a broadcast/flood.
     */
    fun sendPacketToNode(targetNodeId: String, packet: MeshPacket): Boolean

    /** Opaque addresses of all currently connected peers. */
    fun getActivePeerAddresses(): List<String>
}
