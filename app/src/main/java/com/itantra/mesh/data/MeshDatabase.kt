package com.itantra.mesh.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.itantra.mesh.model.MeshNode
import com.itantra.mesh.model.MessageItem
import com.itantra.mesh.model.MessageStatus
import com.itantra.mesh.model.PacketAction
import com.itantra.mesh.model.PacketLogItem
import com.itantra.mesh.model.PacketType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class MeshDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    private val dbLock = ReentrantLock()
    private val gson = Gson()

    // Reactive StateFlow streams for UI updates
    private val _messagesFlow = MutableStateFlow<List<MessageItem>>(emptyList())
    val messagesFlow: StateFlow<List<MessageItem>> = _messagesFlow.asStateFlow()

    private val _nodesFlow = MutableStateFlow<List<MeshNode>>(emptyList())
    val nodesFlow: StateFlow<List<MeshNode>> = _nodesFlow.asStateFlow()

    private val _packetLogsFlow = MutableStateFlow<List<PacketLogItem>>(emptyList())
    val packetLogsFlow: StateFlow<List<PacketLogItem>> = _packetLogsFlow.asStateFlow()

    init {
        reloadAll()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_MESSAGES (
                $COL_MSG_ID TEXT PRIMARY KEY,
                $COL_MSG_CONV_ID TEXT,
                $COL_MSG_SENDER_ID TEXT,
                $COL_MSG_SENDER_NAME TEXT,
                $COL_MSG_DEST_ID TEXT,
                $COL_MSG_CONTENT TEXT,
                $COL_MSG_TIMESTAMP INTEGER,
                $COL_MSG_STATUS TEXT,
                $COL_MSG_HOP_COUNT INTEGER,
                $COL_MSG_ROUTE TEXT,
                $COL_MSG_IS_FROM_SELF INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_NODES (
                $COL_NODE_ID TEXT PRIMARY KEY,
                $COL_NODE_NAME TEXT,
                $COL_NODE_IS_DIRECT INTEGER,
                $COL_NODE_BT_ADDR TEXT,
                $COL_NODE_HOP_DIST INTEGER,
                $COL_NODE_LAST_SEEN INTEGER,
                $COL_NODE_RSSI INTEGER,
                $COL_NODE_IS_VIRTUAL INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_PACKET_LOGS (
                $COL_LOG_ID TEXT PRIMARY KEY,
                $COL_LOG_TIMESTAMP INTEGER,
                $COL_LOG_PACKET_ID TEXT,
                $COL_LOG_TYPE TEXT,
                $COL_LOG_SENDER_ID TEXT,
                $COL_LOG_SENDER_NAME TEXT,
                $COL_LOG_DEST_ID TEXT,
                $COL_LOG_ACTION TEXT,
                $COL_LOG_HOP_COUNT INTEGER,
                $COL_LOG_TTL_REMAINING INTEGER,
                $COL_LOG_DETAILS TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NODES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PACKET_LOGS")
        onCreate(db)
    }

    // --- Message Operations ---

    fun insertMessage(message: MessageItem) = dbLock.withLock {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_MSG_ID, message.messageId)
            put(COL_MSG_CONV_ID, message.conversationId)
            put(COL_MSG_SENDER_ID, message.senderId)
            put(COL_MSG_SENDER_NAME, message.senderName)
            put(COL_MSG_DEST_ID, message.destinationId)
            put(COL_MSG_CONTENT, message.content)
            put(COL_MSG_TIMESTAMP, message.timestamp)
            put(COL_MSG_STATUS, message.status.name)
            put(COL_MSG_HOP_COUNT, message.hopCount)
            put(COL_MSG_ROUTE, gson.toJson(message.routeHops))
            put(COL_MSG_IS_FROM_SELF, if (message.isFromSelf) 1 else 0)
        }
        db.insertWithOnConflict(TABLE_MESSAGES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        refreshMessages()
    }

    fun updateMessageStatus(messageId: String, status: MessageStatus) = dbLock.withLock {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_MSG_STATUS, status.name)
        }
        db.update(TABLE_MESSAGES, cv, "$COL_MSG_ID = ?", arrayOf(messageId))
        refreshMessages()
    }

    private fun refreshMessages() {
        val list = mutableListOf<MessageItem>()
        val db = readableDatabase
        val cursor = db.query(TABLE_MESSAGES, null, null, null, null, null, "$COL_MSG_TIMESTAMP ASC")
        cursor.use {
            val listType = object : TypeToken<List<String>>() {}.type
            while (it.moveToNext()) {
                val routeJson = it.getString(it.getColumnIndexOrThrow(COL_MSG_ROUTE))
                val routeHops: List<String> = try {
                    gson.fromJson(routeJson, listType) ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
                list.add(
                    MessageItem(
                        messageId = it.getString(it.getColumnIndexOrThrow(COL_MSG_ID)),
                        conversationId = it.getString(it.getColumnIndexOrThrow(COL_MSG_CONV_ID)),
                        senderId = it.getString(it.getColumnIndexOrThrow(COL_MSG_SENDER_ID)),
                        senderName = it.getString(it.getColumnIndexOrThrow(COL_MSG_SENDER_NAME)),
                        destinationId = it.getString(it.getColumnIndexOrThrow(COL_MSG_DEST_ID)),
                        content = it.getString(it.getColumnIndexOrThrow(COL_MSG_CONTENT)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COL_MSG_TIMESTAMP)),
                        status = try {
                            MessageStatus.valueOf(it.getString(it.getColumnIndexOrThrow(COL_MSG_STATUS)))
                        } catch (e: Exception) {
                            MessageStatus.SENT
                        },
                        hopCount = it.getInt(it.getColumnIndexOrThrow(COL_MSG_HOP_COUNT)),
                        routeHops = routeHops,
                        isFromSelf = it.getInt(it.getColumnIndexOrThrow(COL_MSG_IS_FROM_SELF)) == 1
                    )
                )
            }
        }
        _messagesFlow.value = list
    }

    // --- Node Operations ---

    fun upsertNode(node: MeshNode) = dbLock.withLock {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_NODE_ID, node.nodeId)
            put(COL_NODE_NAME, node.displayName)
            put(COL_NODE_IS_DIRECT, if (node.isDirectNeighbor) 1 else 0)
            put(COL_NODE_BT_ADDR, node.bluetoothAddress)
            put(COL_NODE_HOP_DIST, node.hopDistance)
            put(COL_NODE_LAST_SEEN, node.lastSeenTimestamp)
            if (node.rssi != null) put(COL_NODE_RSSI, node.rssi)
            put(COL_NODE_IS_VIRTUAL, if (node.isVirtual) 1 else 0)
        }
        db.insertWithOnConflict(TABLE_NODES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        refreshNodes()
    }

    private fun refreshNodes() {
        val list = mutableListOf<MeshNode>()
        val db = readableDatabase
        val cursor = db.query(TABLE_NODES, null, null, null, null, null, "$COL_NODE_LAST_SEEN DESC")
        cursor.use {
            while (it.moveToNext()) {
                val rssiIndex = it.getColumnIndexOrThrow(COL_NODE_RSSI)
                val rssi = if (it.isNull(rssiIndex)) null else it.getInt(rssiIndex)
                list.add(
                    MeshNode(
                        nodeId = it.getString(it.getColumnIndexOrThrow(COL_NODE_ID)),
                        displayName = it.getString(it.getColumnIndexOrThrow(COL_NODE_NAME)),
                        isDirectNeighbor = it.getInt(it.getColumnIndexOrThrow(COL_NODE_IS_DIRECT)) == 1,
                        bluetoothAddress = it.getString(it.getColumnIndexOrThrow(COL_NODE_BT_ADDR)),
                        hopDistance = it.getInt(it.getColumnIndexOrThrow(COL_NODE_HOP_DIST)),
                        lastSeenTimestamp = it.getLong(it.getColumnIndexOrThrow(COL_NODE_LAST_SEEN)),
                        rssi = rssi,
                        isVirtual = it.getInt(it.getColumnIndexOrThrow(COL_NODE_IS_VIRTUAL)) == 1
                    )
                )
            }
        }
        _nodesFlow.value = list
    }

    // --- Packet Log Operations ---

    fun insertPacketLog(log: PacketLogItem) = dbLock.withLock {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_LOG_ID, log.id)
            put(COL_LOG_TIMESTAMP, log.timestamp)
            put(COL_LOG_PACKET_ID, log.packetId)
            put(COL_LOG_TYPE, log.type.name)
            put(COL_LOG_SENDER_ID, log.senderId)
            put(COL_LOG_SENDER_NAME, log.senderName)
            put(COL_LOG_DEST_ID, log.destinationId)
            put(COL_LOG_ACTION, log.action.name)
            put(COL_LOG_HOP_COUNT, log.hopCount)
            put(COL_LOG_TTL_REMAINING, log.ttlRemaining)
            put(COL_LOG_DETAILS, log.details)
        }
        db.insertWithOnConflict(TABLE_PACKET_LOGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        // Keep only the most recent 200 logs to prevent memory/disk bloat
        db.execSQL(
            """
            DELETE FROM $TABLE_PACKET_LOGS WHERE $COL_LOG_ID NOT IN (
                SELECT $COL_LOG_ID FROM $TABLE_PACKET_LOGS ORDER BY $COL_LOG_TIMESTAMP DESC LIMIT 200
            )
            """.trimIndent()
        )
        refreshPacketLogs()
    }

    private fun refreshPacketLogs() {
        val list = mutableListOf<PacketLogItem>()
        val db = readableDatabase
        val cursor = db.query(TABLE_PACKET_LOGS, null, null, null, null, null, "$COL_LOG_TIMESTAMP DESC", "100")
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    PacketLogItem(
                        id = it.getString(it.getColumnIndexOrThrow(COL_LOG_ID)),
                        timestamp = it.getLong(it.getColumnIndexOrThrow(COL_LOG_TIMESTAMP)),
                        packetId = it.getString(it.getColumnIndexOrThrow(COL_LOG_PACKET_ID)),
                        type = try {
                            PacketType.valueOf(it.getString(it.getColumnIndexOrThrow(COL_LOG_TYPE)))
                        } catch (e: Exception) {
                            PacketType.BROADCAST
                        },
                        senderId = it.getString(it.getColumnIndexOrThrow(COL_LOG_SENDER_ID)),
                        senderName = it.getString(it.getColumnIndexOrThrow(COL_LOG_SENDER_NAME)),
                        destinationId = it.getString(it.getColumnIndexOrThrow(COL_LOG_DEST_ID)),
                        action = try {
                            PacketAction.valueOf(it.getString(it.getColumnIndexOrThrow(COL_LOG_ACTION)))
                        } catch (e: Exception) {
                            PacketAction.RECEIVED
                        },
                        hopCount = it.getInt(it.getColumnIndexOrThrow(COL_LOG_HOP_COUNT)),
                        ttlRemaining = it.getInt(it.getColumnIndexOrThrow(COL_LOG_TTL_REMAINING)),
                        details = it.getString(it.getColumnIndexOrThrow(COL_LOG_DETAILS))
                    )
                )
            }
        }
        _packetLogsFlow.value = list
    }

    fun clearAllLogs() = dbLock.withLock {
        writableDatabase.execSQL("DELETE FROM $TABLE_PACKET_LOGS")
        refreshPacketLogs()
    }

    fun reloadAll() {
        refreshMessages()
        refreshNodes()
        refreshPacketLogs()
    }

    companion object {
        const val DATABASE_NAME = "itantra_mesh.db"
        const val DATABASE_VERSION = 1

        private const val TABLE_MESSAGES = "messages"
        private const val COL_MSG_ID = "message_id"
        private const val COL_MSG_CONV_ID = "conversation_id"
        private const val COL_MSG_SENDER_ID = "sender_id"
        private const val COL_MSG_SENDER_NAME = "sender_name"
        private const val COL_MSG_DEST_ID = "destination_id"
        private const val COL_MSG_CONTENT = "content"
        private const val COL_MSG_TIMESTAMP = "timestamp"
        private const val COL_MSG_STATUS = "status"
        private const val COL_MSG_HOP_COUNT = "hop_count"
        private const val COL_MSG_ROUTE = "route_hops"
        private const val COL_MSG_IS_FROM_SELF = "is_from_self"

        private const val TABLE_NODES = "nodes"
        private const val COL_NODE_ID = "node_id"
        private const val COL_NODE_NAME = "display_name"
        private const val COL_NODE_IS_DIRECT = "is_direct"
        private const val COL_NODE_BT_ADDR = "bluetooth_address"
        private const val COL_NODE_HOP_DIST = "hop_distance"
        private const val COL_NODE_LAST_SEEN = "last_seen_timestamp"
        private const val COL_NODE_RSSI = "rssi"
        private const val COL_NODE_IS_VIRTUAL = "is_virtual"

        private const val TABLE_PACKET_LOGS = "packet_logs"
        private const val COL_LOG_ID = "id"
        private const val COL_LOG_TIMESTAMP = "timestamp"
        private const val COL_LOG_PACKET_ID = "packet_id"
        private const val COL_LOG_TYPE = "type"
        private const val COL_LOG_SENDER_ID = "sender_id"
        private const val COL_LOG_SENDER_NAME = "sender_name"
        private const val COL_LOG_DEST_ID = "destination_id"
        private const val COL_LOG_ACTION = "action"
        private const val COL_LOG_HOP_COUNT = "hop_count"
        private const val COL_LOG_TTL_REMAINING = "ttl_remaining"
        private const val COL_LOG_DETAILS = "details"
    }
}
