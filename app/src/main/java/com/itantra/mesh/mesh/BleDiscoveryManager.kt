package com.itantra.mesh.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.ParcelUuid
import android.util.Log
import java.nio.charset.StandardCharsets
import java.util.UUID

@SuppressLint("MissingPermission")
class BleDiscoveryManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val localNodeId: String,
    private val onPeerDiscovered: (BluetoothDevice, String?, Int?) -> Unit
) {
    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null
    private var isAdvertising = false
    private var isScanning = false

    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    if (device != null) {
                        onPeerDiscovered(device, null, if (rssi != Short.MIN_VALUE.toInt()) rssi else null)
                    }
                }
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            Log.i(TAG, "BLE Advertising started successfully.")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            Log.w(TAG, "BLE Advertising failed with error code: $errorCode")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { handleScanResult(it) }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "BLE Scan failed with error code: $errorCode")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val record = result.scanRecord
        var discoveredNodeId: String? = null
        if (record != null) {
            val serviceData = record.getServiceData(ParcelUuid(MESH_SERVICE_UUID))
            if (serviceData != null) {
                discoveredNodeId = String(serviceData, StandardCharsets.UTF_8)
            }
        }
        onPeerDiscovered(device, discoveredNodeId, result.rssi)
    }

    fun start() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Log.w(TAG, "Bluetooth not available or not enabled")
            return
        }

        // Register Classic discovery receiver as fallback
        try {
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            context.registerReceiver(classicReceiver, filter)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register classic BT receiver", e)
        }

        startBleAdvertising()
        startBleScanning()
        startClassicDiscovery()
    }

    fun stop() {
        stopBleAdvertising()
        stopBleScanning()
        stopClassicDiscovery()
        try {
            context.unregisterReceiver(classicReceiver)
        } catch (e: Exception) {
            // Ignored if not registered
        }
    }

    private fun startBleAdvertising() {
        try {
            advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
            if (advertiser == null) {
                Log.d(TAG, "BLE Advertiser not supported on this device")
                return
            }

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .build()

            // Generic advertisement — no service UUID/data identifying this as a mesh node
            val data = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .build()

            advertiser?.startAdvertising(settings, data, advertiseCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start BLE advertising", e)
        }
    }

    private fun stopBleAdvertising() {
        if (isAdvertising) {
            try {
                advertiser?.stopAdvertising(advertiseCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping BLE advertiser", e)
            }
            isAdvertising = false
        }
    }

    private fun startBleScanning() {
        try {
            scanner = bluetoothAdapter?.bluetoothLeScanner
            if (scanner == null) {
                Log.d(TAG, "BLE Scanner not available")
                return
            }

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()

            // No filter — see every nearby BLE advertiser, not just mesh peers
            scanner?.startScan(emptyList(), settings, scanCallback)
            isScanning = true
            Log.d(TAG, "BLE Scanner started (unfiltered)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start BLE scan", e)
        }
    }

    private fun stopBleScanning() {
        if (isScanning) {
            try {
                scanner?.stopScan(scanCallback)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping BLE scanner", e)
            }
            isScanning = false
        }
    }

    fun startClassicDiscovery() {
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
            bluetoothAdapter?.startDiscovery()
        } catch (e: Exception) {
            Log.w(TAG, "Error starting classic discovery", e)
        }
    }

    fun stopClassicDiscovery() {
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter.cancelDiscovery()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error canceling classic discovery", e)
        }
    }

    companion object {
        private const val TAG = "BleDiscoveryManager"
        val MESH_SERVICE_UUID: UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66")
    }
}
