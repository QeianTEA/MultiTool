package com.example.multitool

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.io.File
import com.example.multitool.BluetoothManager
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.database.Cursor
import android.provider.OpenableColumns
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.multitool.data.DatabaseProvider
import com.example.multitool.data.AppSettings
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.io.InputStream
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.LinearProgressIndicator
import kotlinx.coroutines.withContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.multitool.ble.BLERepository
import com.example.multitool.BLEManager
import com.example.multitool.viewmodel.RfidViewModel
import kotlin.collections.lastOrNull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import com.example.multitool.data.RFIDEntry
import android.util.Log
import java.net.HttpURLConnection
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.flow.filter
import java.net.URL

// Screens.kt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val isConnected by remember { derivedStateOf { BluetoothManager.isConnected } }

    LaunchedEffect(Unit) {
        if (BluetoothManager.isConnected) {
            BLEManager.send("SET_MODE:IDLE")
        }
    }

    LaunchedEffect(isConnected) {
        if (isConnected) {
            // single‐line command
            BLEManager.send("SET_MODE:RFID|SUBMODE: ")
        }
    }


    Scaffold(
        topBar = {
            TopBarWithStatus("MultiTool Control Center", isBluetoothConnected = isConnected)
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BluetoothStatus()

            FunctionCard("RFID", Icons.Default.Nfc, enabled = isConnected) {
                navController.navigate("rfid")
            }
            FunctionCard("Infrared (IR)", Icons.Default.Tv, enabled = isConnected) {
                navController.navigate("ir")
            }
            FunctionCard("Wi‑Fi Transfer", Icons.Default.Wifi, enabled = isConnected) {
                navController.navigate("transfer")
            }
            FunctionCard("BLE Communication", Icons.Default.Bluetooth, enabled = true) {
                navController.navigate("ble")
            }
            FunctionCard("BLE Log", Icons.Default.Bluetooth, enabled = true) {
                navController.navigate("ble_log")
            }
            FunctionCard("General Settings", Icons.Default.Settings, enabled = true) {
                navController.navigate("settings")
            }
        }
    }
}

@Composable
fun FunctionCard(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(enabled = enabled, onClick = onClick),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
fun BluetoothStatus() {
    val isConnected by remember { derivedStateOf { BluetoothManager.isConnected } }
    Row(
        Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Bluetooth: ${if (isConnected) "Connected" else "Disconnected"}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBarWithStatus(title: String, isBluetoothConnected: Boolean) {
    TopAppBar(
        title = { Text(title) },
        actions = {
            Icon(
                imageVector = if (isBluetoothConnected)
                    Icons.Default.BluetoothConnected
                else
                    Icons.Default.BluetoothDisabled,
                contentDescription = null
            )
        }
    )
}



// -- RFIDScreen --
enum class RFIDMode { READ, WRITE, EMIT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RFIDScreen(
    viewModel: RfidViewModel = viewModel()
) {
    val context = LocalContext.current
    val isConnected   by remember { derivedStateOf { BLEManager.isConnected } }
    val messages      by BLERepository.messages.collectAsState()
    val entries       by viewModel.entries.collectAsStateWithLifecycle()
    val slotContents  by viewModel.slotContents.collectAsState()

    var mode        by remember { mutableStateOf(RFIDMode.READ) }
    var currentRead by remember { mutableStateOf("None") }
    var rfidDetails by remember { mutableStateOf("Waiting…") }
    var customInput by remember { mutableStateOf("") }
    var showDialog  by remember { mutableStateOf(false) }

    // 1️⃣ When mode or connection changes, tell the Pico
    LaunchedEffect(mode, isConnected) {
        if (isConnected) {
            BLEManager.send("SET_MODE:RFID|SUBMODE:${mode.name}")
            viewModel.fetchSlotContents(context)
        }
    }

    // 2️⃣ Handle **all** incoming messages
    LaunchedEffect(messages) {

        messages.lastOrNull()?.let { msg ->
            Log.d("RFID", "Message: $msg")
            when {
                // Case: UID with data embedded (our new format)
                msg.startsWith("UID:") && msg.contains("|") -> {
                    val uidData = msg.removePrefix("UID:").split("|")
                    if (uidData.size == 2) {
                        val uid = uidData[0]
                        val data = uidData[1]
                        currentRead = uid
                        rfidDetails = data
                        viewModel.add(uid, data)
                    }
                }

                // Old format (if ever needed again)
                msg.startsWith("UID:") -> {
                    currentRead = msg.removePrefix("UID:")
                    rfidDetails = ""
                }

                msg.startsWith("DATA:") -> {
                    val data = msg.removePrefix("DATA:")
                    rfidDetails = data
                    viewModel.add(currentRead, data)
                }

                msg == "WRITE_OK" -> Toast.makeText(context, "Write succeeded", Toast.LENGTH_SHORT).show()
                msg == "WRITE_FAIL" -> Toast.makeText(context, "Write failed", Toast.LENGTH_SHORT).show()
                msg.startsWith("ERR:") -> {
                    rfidDetails = msg.removePrefix("ERR:")
                }
            }
        }
    }



    Scaffold(topBar = {
        TopAppBar(
            title   = { Text("RFID Function") },
            actions = {
                Icon(
                    imageVector     = if (isConnected) Icons.Default.BluetoothConnected
                    else Icons.Default.BluetoothDisabled,
                    contentDescription = null
                )
            }
        )
    }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── Mode selector ────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RFIDMode.values().forEach { m ->
                    Button(
                        onClick   = { mode = m },
                        enabled   = isConnected,
                        colors    = ButtonDefaults.buttonColors(
                            containerColor = if (mode == m)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) { Text(m.name) }
                }
            }

            Divider()

            // ─── READ ──────────────────────────────────────────────────
            if (mode == RFIDMode.READ) {
                Text("Current UID:", style = MaterialTheme.typography.titleMedium)
                Text(currentRead)
                Text("Data Block:", style = MaterialTheme.typography.titleMedium)
                Text(rfidDetails)

                Button(onClick = { showDialog = true }, enabled = currentRead != "None") {
                    Text("Save to Slot…")
                }

                if (showDialog) {
                    AlertDialog(
                        onDismissRequest = { showDialog = false },
                        title            = { Text("Save UID to…") },
                        text             = {
                            Column {
                                (1..3).forEach { slot ->
                                    val content = slotContents
                                        .getOrNull(slot - 1)
                                        ?.takeUnless { it.isBlank() }
                                        ?: "Empty"
                                    TextButton(onClick = {
                                        viewModel.saveToSlot(context, slot, currentRead)
                                        showDialog = false
                                    }) {
                                        Text("Slot $slot: $content")
                                    }
                                }
                            }
                        },
                        confirmButton   = {
                            TextButton(onClick = { showDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }

            // ─── WRITE & EMIT modes omitted for brevity… ──────────────

            Divider()

            // ─── HISTORY ──────────────────────────────────────────────
            Text("Saved Reads:", style = MaterialTheme.typography.titleMedium)
            LazyColumn {
                items(entries) { e: RFIDEntry ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("UID: ${e.uid}")
                        Text("Details: ${e.details}", style = MaterialTheme.typography.bodySmall)
                    }
                    Divider()
                }
            }
        }
    }
}

// Screens.kt
enum class IRMode { READ, TRANSMIT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IRScreen(
    viewModel: com.example.multitool.viewmodel.IrViewModel = viewModel()
) {

    val modeOptions = listOf("READ", "TRANSMIT")
    var submode by remember { mutableStateOf("READ") }
    var currentIr by remember { mutableStateOf("") }

    val context = LocalContext.current
    val isConnected by remember { derivedStateOf { BLEManager.isConnected } }
    val messages by BLERepository.messages.collectAsState()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val slotContents by viewModel.slotContents.collectAsState()

    var mode by remember { mutableStateOf(IRMode.READ) }
    var currentCode by remember { mutableStateOf("No signal") }
    var customInput by remember { mutableStateOf("") }
    var showSlotDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // When connected or submode changes:
    LaunchedEffect(mode, isConnected) {
        if (isConnected) {
            BLEManager.send("SET_MODE:IR|SUBMODE:${mode.name}")
            viewModel.fetchSlotContents(context)  // your IR slots
        }
    }

    // Handle incoming BLE messages once
    LaunchedEffect(messages) {
        messages.lastOrNull()?.let { msg ->
            when {
                msg.startsWith("IR_CODE:") -> {
                    val code = msg.removePrefix("IR_CODE:")
                    currentCode = code
                    viewModel.add(code)
                }
                msg == "IR_OK"   -> Toast.makeText(context, "Sent!",    Toast.LENGTH_SHORT).show()
                msg == "IR_FAIL" -> Toast.makeText(context, "Failed!",  Toast.LENGTH_SHORT).show()
                msg.startsWith("ERR:") -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }
    }
    // Pull slot names
    val slotNames = listOf(
        AppSettings.slot1Name(context).collectAsState("Slot 1").value,
        AppSettings.slot2Name(context).collectAsState("Slot 2").value,
        AppSettings.slot3Name(context).collectAsState("Slot 3").value,
    )

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Infrared") },
            actions = {
                Icon(
                    imageVector = if (isConnected) Icons.Default.BluetoothConnected
                    else Icons.Default.BluetoothDisabled,
                    contentDescription = null
                )
            }
        )
    }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Mode selector
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IRMode.values().forEach { m ->
                    Button(
                        onClick = {
                            mode = m
                            submode = m.name
                        },
                        enabled = isConnected,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (mode == m)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) { Text(m.name) }
                }
            }
            Divider()

            when (mode) {
                IRMode.READ -> {
                    Text("Last code:", style = MaterialTheme.typography.titleMedium)
                    Text(currentCode, style = MaterialTheme.typography.bodyLarge)

                    Button(
                        onClick = { showSlotDialog = true },
                        enabled = currentCode != "No signal"
                    ) {
                        Text("Save to Slot…")
                    }
                    if (showSlotDialog) {
                        AlertDialog(
                            onDismissRequest = { showSlotDialog = false },
                            title = { Text("Save code to…") },
                            text = {
                                Column {
                                    (1..3).forEach { slot ->
                                        val content = slotContents.getOrNull(slot - 1)?.takeUnless { it.isBlank() } ?: "Empty"
                                        TextButton(onClick = {
                                            viewModel.saveToSlot(context, slot, currentCode)
                                            showSlotDialog = false
                                        }) {
                                            Text("Slot $slot: $content")
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { showSlotDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }

                IRMode.TRANSMIT -> {
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it },
                        label = { Text("Custom code to send") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            BLEManager.send("IR_SEND:$customInput")
                        },
                        enabled = customInput.isNotBlank()
                    ) {
                        Text("Send Custom")
                    }

                    Divider()
                    Text("Slots:", style = MaterialTheme.typography.titleMedium)
                    slotContents.forEachIndexed { idx, content ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Slot ${idx + 1}: ${content.ifBlank { "Empty" }}")
                            Row {
                                Button(
                                    onClick = {
                                        BLEManager.send("IR_SEND:$content")
                                    },
                                    enabled = content.isNotBlank()
                                ) {
                                    Text("Send")
                                }
                                IconButton(onClick = {
                                    viewModel.clearSlot(context, idx + 1)
                                }) {
                                    Icon(Icons.Default.Delete, "Clear")
                                }
                            }
                        }
                    }
                }
            }

            Divider()

            // History
            Text("Saved signals:", style = MaterialTheme.typography.titleMedium)
            LazyColumn {
                items(entries) { e ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(e.code)
                    }
                    Divider()
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen() {
    val context = LocalContext.current
    val isConnected by remember { derivedStateOf { BLEManager.isConnected } }
    val messages by BLERepository.messages.collectAsState()
    var step by remember { mutableStateOf(0) }
    var fileUri by remember { mutableStateOf<Uri?>(null) }
    var uploadProgress by remember { mutableStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    val toast = Toast.makeText(context, "", Toast.LENGTH_SHORT)

    // 1) On entry or reconnect, tell Pico TRANSFER mode
    LaunchedEffect(isConnected) {
        if (isConnected) {
            BLEManager.send("SET_MODE:TRANSFER")
        }
    }

    // 2) React to Pico USB/Wi-Fi events
    LaunchedEffect(messages) {
        messages.lastOrNull()?.let { msg ->
            when {
                msg == "USB:CONNECTED"    -> {
                    step = 1
                    toast.setText("USB OK — ready for Wi-Fi…")
                    toast.show()
                }
                msg == "USB:DISCONNECTED" -> {
                    step = 0
                    toast.setText("USB not connected")
                    toast.show()
                }
                msg == "WIFI_STARTED"     -> {
                    step = 2
                    toast.setText("Pico AP is up")
                    toast.show()
                }
                msg.startsWith("ERR:")    -> {
                    toast.setText(msg.removePrefix("ERR:"))
                    toast.show()
                }
            }
        }
    }

    // File picker
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> fileUri = uri }

    Scaffold(topBar = {
        TopBarWithStatus("File Transfer", isBluetoothConnected = isConnected)
    }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Step 0: Check USB ──────────────────────────────
            Button(
                onClick = { BLEManager.send("CHECK_USB") },
                enabled = isConnected && step == 0
            ) {
                Text("Check USB Connection")
            }

            // ── Step 1: Open Wi-Fi Settings ────────────────────
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_WIFI_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                    // **DO NOT** set step = 2 here
                },
                enabled = step == 1
            ) {
                Text("Open Wi-Fi Settings")
            }

            // ── Step 2: Pick file ──────────────────────────────
            Button(
                onClick = { launcher.launch(arrayOf("*/*")) },
                enabled = step == 2
            ) {
                Text("Choose File")
            }
            fileUri?.let { uri ->
                Text("Selected: ${uri.lastPathSegment}")
            }

            // ── Step 3: Upload ──────────────────────────────────
            val canUpload = (step == 2 && fileUri != null)
            Button(
                onClick = {
                    fileUri?.let { uri ->
                        step = 3
                        coroutineScope.launch {
                            val url = URL("http://192.168.4.1/upload")
                            val conn = withContext(Dispatchers.IO) {
                                (url.openConnection() as HttpURLConnection).apply {
                                    requestMethod = "POST"
                                    doOutput = true
                                    setRequestProperty("Content-Type", "application/octet-stream")
                                    connectTimeout = 5000
                                    readTimeout = 5000
                                    connect()
                                }
                            }
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                conn.outputStream.use { out ->
                                    val buf = ByteArray(4096)
                                    var read: Int
                                    var sent = 0L
                                    val total = input.available().toLong().coerceAtLeast(1L)
                                    while (input.read(buf).also { read = it } > 0) {
                                        out.write(buf, 0, read)
                                        sent += read
                                        uploadProgress = (sent.toFloat() / total).coerceIn(0f, 1f)
                                    }
                                    out.flush()
                                }
                            }
                            val code = conn.responseCode
                            conn.disconnect()
                            if (code == 200) {
                                toast.setText("Upload succeeded")
                                toast.show()
                                step = 4
                            } else {
                                toast.setText("Upload failed: $code")
                                toast.show()
                                step = 2
                            }
                        }
                    }
                },
                enabled = canUpload
            ) {
                if (step == 3) {
                    CircularProgressIndicator(progress = uploadProgress, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Uploading…")
                } else Text("Send File")
            }

            // ── Step 4: Done ────────────────────────────────────
            if (step == 4) {
                Text("Transfer complete!", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}



// SettingsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val slot1 by AppSettings.slot1Name(context).collectAsState("Slot 1")
    val slot2 by AppSettings.slot2Name(context).collectAsState("Slot 2")
    val slot3 by AppSettings.slot3Name(context).collectAsState("Slot 3")
    val defMethod by AppSettings.defaultMethod(context).collectAsState("Bluetooth")

    var e1 by remember { mutableStateOf(slot1) }
    var e2 by remember { mutableStateOf(slot2) }
    var e3 by remember { mutableStateOf(slot3) }
    var em by remember { mutableStateOf(defMethod) }

    Scaffold(topBar = {
        TopBarWithStatus("General Settings", isBluetoothConnected = BluetoothManager.isConnected)
    }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize(), Arrangement.spacedBy(16.dp)) {
            Text("Rename Slots", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(e1, { e1 = it }, label={Text("Slot 1")})
            OutlinedTextField(e2, { e2 = it }, label={Text("Slot 2")})
            OutlinedTextField(e3, { e3 = it }, label={Text("Slot 3")})
            Button(onClick={
                scope.launch {
                    AppSettings.setSlotName(context,1,e1)
                    AppSettings.setSlotName(context,2,e2)
                    AppSettings.setSlotName(context,3,e3)
                }
            }) { Text("Save Slots") }

            Divider()
            Text("Default Transfer Method", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(em=="Bluetooth",{em="Bluetooth"})
                Text("Bluetooth", Modifier.clickable{em="Bluetooth"})
                Spacer(Modifier.width(16.dp))
                RadioButton(em=="Wi-Fi",{em="Wi-Fi"})
                Text("Wi-Fi", Modifier.clickable{em="Wi-Fi"})
            }
            Button(onClick={
                scope.launch { AppSettings.setDefaultMethod(context,em) }
            }) { Text("Save Default: $em") }
        }
    }
}
