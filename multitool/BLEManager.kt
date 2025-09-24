package com.example.multitool

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.util.*
import com.example.multitool.ble.BLERepository
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


object BLEManager {
    private const val TAG = "BLEManager"

    // Nordic UART Service UUIDs
    private val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
    private val RX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E") // Write
    private val TX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E") // Notify

    private var bluetoothGatt: BluetoothGatt? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    var isConnected: Boolean = false
        private set

    internal val scanResults = mutableListOf<BluetoothDevice>()
    private var scanCallback: ScanCallback? = null

    private var recvBuffer = StringBuilder()

    @SuppressLint("MissingPermission")
    fun startScan(
        context: Context,
        serviceUuids: List<UUID> = emptyList(),
        onResult: (List<BluetoothDevice>) -> Unit
    ) {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val scanner = adapter.bluetoothLeScanner ?: return

        // build filters if you want to target only your service UUID
        val filters = serviceUuids.map {
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(it))
                .build()
        }
        val settings = ScanSettings.Builder().build()

        // clear old results
        scanResults.clear()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                result.device?.let { device ->
                    if (!scanResults.contains(device)) {
                        scanResults += device
                        onResult(scanResults)
                    }
                }
            }
        }

        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }



    @SuppressLint("MissingPermission")
    fun stopScan() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        val scanner = adapter.bluetoothLeScanner ?: return
        scanCallback?.let { scanner.stopScan(it) }
        scanCallback = null
    }

    fun getScanResults() = scanResults.toList()

    @SuppressLint("MissingPermission")
    fun connect(context: Context, device: BluetoothDevice, onConnected: () -> Unit, onDataReceived: (String) -> Unit) {
        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to GATT server")
                    isConnected = true
                    BluetoothManager.device = device
                    BluetoothManager.isConnected = true
                    gatt.discoverServices()
                }
                else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "Disconnected from GATT server")
                isConnected = false
                bluetoothGatt = null
                BluetoothManager.disconnect() // ← also clear global state
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                val service = gatt.getService(SERVICE_UUID)
                if (service != null) {
                    rxCharacteristic = service.getCharacteristic(RX_UUID)
                    txCharacteristic = service.getCharacteristic(TX_UUID)

                    txCharacteristic?.let { tx ->
                        gatt.setCharacteristicNotification(tx, true)
                        val descriptor = tx.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))
                        descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }

                    onConnected()
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (characteristic.uuid == TX_UUID) {
                    // you can either call getStringValue(0) if the entire line is delivered in one chunk,
                    // or buffer it and split on '\n' if you expect longer packets.
                    val chunk = characteristic.value.toString(Charsets.UTF_8)
                    recvBuffer.append(chunk)
                    var nl: Int
                    while (true) {
                        nl = recvBuffer.indexOf("\n")
                        if (nl < 0) break
                        val line = recvBuffer.substring(0, nl).trimEnd('\r')
                        recvBuffer.delete(0, nl + 1)
                        BLERepository.addMessage(line)
                        onDataReceived(line)
                    }
                }
            }


        })
    }

    @SuppressLint("MissingPermission")
    fun send(message: String) {
        rxCharacteristic?.let {
            val toSend = (message + "\n").toByteArray(Charsets.UTF_8)
            it.value = toSend
            bluetoothGatt?.writeCharacteristic(it)
            Log.d(TAG, "Sent: $message")
        }
    }


    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isConnected = false
        txCharacteristic = null
        rxCharacteristic = null
        Log.d(TAG, "Disconnected BLE")
    }

    suspend fun sendAndReceiveSuspend(message: String, timeoutMillis: Long = 5000): String? =
        suspendCancellableCoroutine { continuation ->
            lateinit var listener: (String) -> Unit

            listener = { response ->
                if (continuation.isActive) {
                    continuation.resume(response)
                }
                BLERepository.removeListener(listener)
            }

            BLERepository.addListener(listener)
            send(message)

            continuation.invokeOnCancellation {
                BLERepository.removeListener(listener)
            }

            CoroutineScope(Dispatchers.IO).launch {
                delay(timeoutMillis)
                if (continuation.isActive) {
                    continuation.resume(null)
                    BLERepository.removeListener(listener)
                }
            }
        }
}
