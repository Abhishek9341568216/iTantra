package com.itantra.mesh

import com.itantra.mesh.model.MeshPacket
import com.itantra.mesh.model.PacketType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeshPacketTest {

    @Test
    fun testSerializationAndDeserialization() {
        val original = MeshPacket(
            type = PacketType.BROADCAST,
            senderId = "NODE-1111",
            senderName = "Alice",
            destinationId = MeshPacket.BROADCAST_DESTINATION,
            payload = "Hello Offline Mesh!",
            ttl = 5,
            hopCount = 0,
            routeHops = listOf("NODE-1111")
        )

        val bytes = original.toBytes()
        val restored = MeshPacket.fromBytes(bytes)

        assertNotNull(restored)
        assertEquals(original.packetId, restored?.packetId)
        assertEquals(original.type, restored?.type)
        assertEquals(original.senderId, restored?.senderId)
        assertEquals(original.senderName, restored?.senderName)
        assertEquals(original.payload, restored?.payload)
        assertEquals(5, restored?.ttl)
        assertEquals(0, restored?.hopCount)
        assertEquals(listOf("NODE-1111"), restored?.routeHops)
    }

    @Test
    fun testForwardedPacketHopAndTtl() {
        val packet = MeshPacket(
            senderId = "NODE-A",
            senderName = "Alice",
            destinationId = "NODE-C",
            payload = "Multi-hop message",
            ttl = 4,
            hopCount = 0,
            routeHops = listOf("NODE-A")
        )

        val relayed1 = packet.createForwardedPacket("NODE-B")
        assertEquals(3, relayed1.ttl)
        assertEquals(1, relayed1.hopCount)
        assertEquals(listOf("NODE-A", "NODE-B"), relayed1.routeHops)

        val relayed2 = relayed1.createForwardedPacket("NODE-X")
        assertEquals(2, relayed2.ttl)
        assertEquals(2, relayed2.hopCount)
        assertEquals(listOf("NODE-A", "NODE-B", "NODE-X"), relayed2.routeHops)
    }
}
