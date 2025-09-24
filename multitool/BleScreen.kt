package com.example.multitool

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.*
import android.util.Log
import java.util.UUID


private val YOUR_SERVICE_UUID: UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleScreen() {
    val context = LocalContext.current

    // Global state
    val isConnected by remember { derivedStateOf { BluetoothManager.isConnected } }
    val currentDevice = BluetoothManager.device

    var isScanning by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf(emptyList<BluetoothDevice>()) }
    var lastMessage by remember { mutableStateOf("") }

    // Permissions
    val requiredPermissions = arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
    var hasPermissions by remember {
        mutableStateOf(requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        if (!hasPermissions) {
            Toast.makeText(context, "BLE permissions required", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) permissionLauncher.launch(requiredPermissions)
    }

    DisposableEffect(Unit) {
        onDispose {
            BLEManager.stopScan()
            isScanning = false
        }
    }

    fun startScanning() {
        if (!hasPermissions) {
            permissionLauncher.launch(requiredPermissions)
            return
        }
        devices = emptyList()
        BLEManager.startScan(context, serviceUuids = listOf(YOUR_SERVICE_UUID)) { list ->
            devices = list
        }
        isScanning = true
    }

    fun stopScanning() {
        BLEManager.stopScan()
        isScanning = false
    }

    fun connectTo(device: BluetoothDevice) {
        if (!hasPermissions) {
            permissionLauncher.launch(requiredPermissions)
            return
        }

        BLEManager.connect(
            context,
            device,
            onConnected = {
                BluetoothManager.device = device
                BluetoothManager.isConnected = true
                Toast.makeText(context, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
            },
            onDataReceived = { msg ->
                lastMessage = msg
            }
        )
    }

    fun disconnectBle() {
        BLEManager.disconnect()
        BluetoothManager.disconnect()
        Toast.makeText(context, "Disconnected", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bluetooth Settings") }) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Show status
            if (isConnected) {
                Text("Connected to: ${currentDevice?.name ?: currentDevice?.address}")
                Text("Last Message: $lastMessage")
                Button(onClick = {
                    BLEManager.send("Loud and clear!")
                }) {
                    Text("Send Test Message")
                }
                Button(onClick = { disconnectBle() }) {
                    Text("Disconnect")
                }
            } else {
                Button(
                    onClick = { if (isScanning) stopScanning() else startScanning() },
                    enabled = hasPermissions
                ) {
                    Text(if (isScanning) "Stop Scan" else "Scan for Devices")
                }

                LazyColumn {
                    items(devices) { device ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(device.name ?: device.address)
                            Button(
                                onClick = { connectTo(device) },
                                enabled = !BluetoothManager.isConnected
                            ) {
                                Text("Connect")
                            }
                        }
                    }
                }
            }
        }
    }
}
