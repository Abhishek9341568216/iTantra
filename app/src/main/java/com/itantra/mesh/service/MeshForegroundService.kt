package com.itantra.mesh.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.itantra.mesh.ITantraApp
import com.itantra.mesh.MainActivity
import com.itantra.mesh.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class MeshForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var statusObserverJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val app = ITantraApp.instance

        startForegroundNotification()

        app.routingEngine.start()

        // Bluetooth peer count and Serial connection are independent and both worth surfacing,
        // so the notification is rebuilt whenever either one changes.
        statusObserverJob = serviceScope.launch {
            combine(
                app.compositeTransport.bluetoothPeersCount,
                app.compositeTransport.isSerialConnected
            ) { btPeers, serialConnected -> btPeers to serialConnected }
                .collectLatest { updateNotification() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startForegroundNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val app = ITantraApp.instance
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isSerialConnected = app.compositeTransport.isSerialConnected.value
        val btPeers = app.compositeTransport.bluetoothPeersCount.value
        val peerStatus = when {
            btPeers > 0 && isSerialConnected ->
                "$btPeers Bluetooth peer${if (btPeers > 1) "s" else ""} + Serial link"
            btPeers > 0 -> "Connected to $btPeers Bluetooth peer${if (btPeers > 1) "s" else ""}"
            isSerialConnected -> "Connected via Serial link"
            else -> "Listening for nearby nodes..."
        }

        return NotificationCompat.Builder(this, ITantraApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("iTantra Mesh Active")
            .setContentText(peerStatus)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        statusObserverJob?.cancel()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
