package com.pulsetile.hr.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.pulsetile.hr.data.ConnectionState
import com.pulsetile.hr.data.HrReading
import java.util.UUID

/**
 * Connects to a WHOOP band that is broadcasting heart rate over the standard BLE
 * Heart Rate Service (0x180D) and streams decoded [HrReading]s (~1/sec).
 *
 * The WHOOP must have "Heart Rate Broadcast" enabled in the WHOOP app, and can only
 * broadcast to one receiver at a time.
 *
 * All BLE calls are annotated @SuppressLint("MissingPermission"); the caller
 * (service/activity) is responsible for holding BLUETOOTH_SCAN / BLUETOOTH_CONNECT
 * before calling [start].
 */
class WhoopHrManager(private val context: Context) {

    interface Listener {
        fun onState(state: ConnectionState)
        fun onReading(reading: HrReading)
        fun onDeviceFound(address: String, name: String?) {}
    }

    var listener: Listener? = null

    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    private val handler = Handler(Looper.getMainLooper())

    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null

    @Volatile private var desired = false
    @Volatile private var scanning = false
    @Volatile private var connecting = false
    private var preferredAddress: String? = null

    fun start(preferredAddress: String?) {
        this.preferredAddress = preferredAddress
        desired = true
        startScan()
    }

    fun stop() {
        desired = false
        stopScan()
        closeGatt()
        emitState(ConnectionState.DISCONNECTED)
    }

    fun isRunning(): Boolean = desired

    // ---- Scanning ----

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (!desired || connecting) return
        val a = adapter
        if (a == null || !a.isEnabled) {
            emitState(ConnectionState.DISCONNECTED)
            // Retry once Bluetooth is (hopefully) back.
            if (desired) handler.postDelayed({ startScan() }, RETRY_MS)
            return
        }
        val s = a.bluetoothLeScanner ?: run {
            emitState(ConnectionState.DISCONNECTED)
            return
        }
        scanner = s
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE)).build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            s.startScan(filters, settings, scanCallback)
            scanning = true
            emitState(ConnectionState.SCANNING)
        } catch (e: SecurityException) {
            Log.w(TAG, "startScan missing permission", e)
            emitState(ConnectionState.DISCONNECTED)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Log.w(TAG, "stopScan missing permission", e)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val name = try {
                device.name
            } catch (e: SecurityException) {
                null
            }
            listener?.onDeviceFound(device.address, name)
            if (connecting) return
            val pref = preferredAddress
            if (pref != null && !pref.equals(device.address, ignoreCase = true)) return
            stopScan()
            connect(device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed: $errorCode")
            scanning = false
            if (desired) handler.postDelayed({ startScan() }, RETRY_MS)
        }
    }

    // ---- Connecting ----

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        connecting = true
        emitState(ConnectionState.CONNECTING)
        try {
            gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.w(TAG, "connectGatt missing permission", e)
            connecting = false
            emitState(ConnectionState.DISCONNECTED)
        }
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        try {
            gatt?.close()
        } catch (e: SecurityException) {
            Log.w(TAG, "gatt close missing permission", e)
        }
        gatt = null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    try {
                        g.discoverServices()
                    } catch (e: SecurityException) {
                        Log.w(TAG, "discoverServices missing permission", e)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    connecting = false
                    closeGatt()
                    if (desired) {
                        emitState(ConnectionState.SCANNING)
                        handler.postDelayed({ startScan() }, RETRY_MS)
                    } else {
                        emitState(ConnectionState.DISCONNECTED)
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val characteristic = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT)
            if (characteristic == null) {
                Log.w(TAG, "HR characteristic not found; disconnecting")
                try {
                    g.disconnect()
                } catch (e: SecurityException) {
                    Log.w(TAG, "disconnect missing permission", e)
                }
                return
            }
            try {
                g.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic.getDescriptor(CCCD)
                if (descriptor != null) {
                    enableNotifications(g, descriptor)
                }
                connecting = false
                emitState(ConnectionState.CONNECTED)
            } catch (e: SecurityException) {
                Log.w(TAG, "enable notifications missing permission", e)
            }
        }

        // Android 13+ delivers the value directly.
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleValue(characteristic, value)
        }

        // Pre-Android 13 path.
        @Deprecated("Deprecated in API 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val value = characteristic.value ?: return
            handleValue(characteristic, value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(g: BluetoothGatt, descriptor: BluetoothGattDescriptor) {
        val enable = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(descriptor, enable)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = enable
            @Suppress("DEPRECATION")
            g.writeDescriptor(descriptor)
        }
    }

    private fun handleValue(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (characteristic.uuid != HR_MEASUREMENT) return
        val reading = parseHrMeasurement(value) ?: return
        listener?.onReading(reading)
    }

    private fun emitState(state: ConnectionState) {
        handler.post { listener?.onState(state) }
    }

    companion object {
        private const val TAG = "WhoopHrManager"
        private const val RETRY_MS = 2500L

        val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        /**
         * Decodes the BLE Heart Rate Measurement characteristic (0x2A37).
         * Layout: [flags][HR value (uint8 or uint16 LE)][energy expended?][RR intervals?]
         */
        fun parseHrMeasurement(data: ByteArray): HrReading? {
            if (data.isEmpty()) return null
            val flags = data[0].toInt() and 0xFF
            val hr16 = flags and 0x01 != 0
            val sensorContactSupported = flags and 0x04 != 0
            val sensorContact = if (sensorContactSupported) flags and 0x02 != 0 else null
            val energyPresent = flags and 0x08 != 0
            val rrPresent = flags and 0x10 != 0

            var index = 1
            val bpm: Int
            if (hr16) {
                if (data.size < 3) return null
                bpm = (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
                index = 3
            } else {
                if (data.size < 2) return null
                bpm = data[1].toInt() and 0xFF
                index = 2
            }
            if (energyPresent) index += 2

            val rr = mutableListOf<Int>()
            if (rrPresent) {
                while (index + 1 < data.size) {
                    val raw = (data[index].toInt() and 0xFF) or ((data[index + 1].toInt() and 0xFF) shl 8)
                    // Units of 1/1024 s -> milliseconds.
                    rr.add(raw * 1000 / 1024)
                    index += 2
                }
            }

            if (bpm <= 0) return null
            return HrReading(bpm = bpm, rrIntervalsMs = rr, sensorContact = sensorContact)
        }
    }
}
