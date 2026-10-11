package com.itantra.mesh.mesh

import com.itantra.mesh.data.MeshDatabase
import com.itantra.mesh.data.PreferencesRepository
import com.itantra.mesh.model.MeshNode
import com.itantra.mesh.model.MeshPacket
import com.itantra.mesh.model.PacketType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MeshSimulator(
    private val routingEngine: MeshRoutingEngine,
    private val database: MeshDatabase,
    private val prefs: PreferencesRepository,
    private val scope: CoroutineScope
) {
    private var simulationJob: Job? = null

    val virtualNodes = listOf(
        MeshNode(
            nodeId = VIRTUAL_RELAY_1_ID,
            displayName = "Relay-Alpha (Direct)",
            isDirectNeighbor = true,
            hopDistance = 1,
            isVirtual = true
        ),
        MeshNode(
            nodeId = VIRTUAL_RELAY_2_ID,
            displayName = "Relay-Beta (Multi-hop)",
            isDirectNeighbor = false,
            hopDistance = 2,
            isVirtual = true
        ),
        MeshNode(
            nodeId = VIRTUAL_ECHO_ID,
            displayName = "Echo-Node (3 Hops)",
            isDirectNeighbor = false,
            hopDistance = 3,
            isVirtual = true
        )
    )

    fun start() {
        // Register virtual nodes in database
        virtualNodes.forEach { database.upsertNode(it) }
    }

    fun stop() {
        simulationJob?.cancel()
    }

    fun onPacketOriginated(packet: MeshPacket) {
        if (!prefs.isSimulatorEnabled) return

        // If the packet is broadcast or addressed to the virtual echo node
        if (packet.destinationId == MeshPacket.BROADCAST_DESTINATION || packet.destinationId == VIRTUAL_ECHO_ID) {
            scope.launch(Dispatchers.Default) {
                // Simulate hop 1: Relay-Alpha receives and forwards
                delay(300)
                
                // Simulate hop 2: Relay-Beta forwards
                delay(300)

                // Simulate hop 3: Echo-Node receives and responds
                if (packet.type == PacketType.DIRECT_CHAT || packet.type == PacketType.BROADCAST) {
                    delay(400)
                    val responseText = if (packet.destinationId == VIRTUAL_ECHO_ID) {
                        "Echo reply: \"${packet.payload}\" received at Echo-Node via 3 hops!"
                    } else {
                        "Hello from Echo-Node! Received your broadcast via Relay-Alpha & Beta."
                    }

                    val replyPacket = MeshPacket(
                        type = if (packet.destinationId == VIRTUAL_ECHO_ID) PacketType.DIRECT_CHAT else PacketType.BROADCAST,
                        senderId = VIRTUAL_ECHO_ID,
                        senderName = "Echo-Node",
                        destinationId = if (packet.destinationId == VIRTUAL_ECHO_ID) prefs.nodeId else MeshPacket.BROADCAST_DESTINATION,
                        payload = responseText,
                        ttl = 3,
                        hopCount = 2,
                        routeHops = listOf(VIRTUAL_ECHO_ID, VIRTUAL_RELAY_2_ID, VIRTUAL_RELAY_1_ID)
                    )

                    // Deliver to local routing engine as an incoming packet
                    routingEngine.processIncomingPacket(replyPacket)
                }
            }
        }
    }

    fun onMeshPacketRelayed(packet: MeshPacket) {
        // Additional simulated behaviors if needed
    }

    companion object {
        const val VIRTUAL_RELAY_1_ID = "SIM-RELAY-1"
        const val VIRTUAL_RELAY_2_ID = "SIM-RELAY-2"
        const val VIRTUAL_ECHO_ID = "SIM-ECHO-BOT"
    }
}
