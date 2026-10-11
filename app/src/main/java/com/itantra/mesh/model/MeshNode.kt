package com.itantra.mesh.model

data class MeshNode(
    val nodeId: String, // Bluetooth MAC Address (e.g. AA:BB:CC:DD:EE:FF)
    val displayName: String,
    val isDirectNeighbor: Boolean = false,
    val bluetoothAddress: String? = null,
    val hopDistance: Int = 1,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val rssi: Int? = null,
    val isVirtual: Boolean = false,
    val isMeshPeer: Boolean = false, // True if verified running iTantra Mesh
    val isBonded: Boolean = false    // True if paired via Android Bluetooth Settings
) {
    val isOnline: Boolean
        get() = (System.currentTimeMillis() - lastSeenTimestamp) < ONLINE_THRESHOLD_MS

    val macAddress: String
        get() = bluetoothAddress ?: nodeId

    companion object {
        const val ONLINE_THRESHOLD_MS = 90_000L // 90 seconds
    }
}
