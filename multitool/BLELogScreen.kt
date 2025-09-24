@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.multitool

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.multitool.BLEManager
import com.example.multitool.ble.BLERepository

@Composable
fun BLELogScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val messages by BLERepository.messages.collectAsState()

    val isConnected by remember { derivedStateOf { BLEManager.isConnected } }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopBarWithStatus("BLE Live Log", isBluetoothConnected = isConnected)
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = {
                    BLERepository.clear()
                }) {
                    Text("Clear Log")
                }

                Button(onClick = {
                    val combined = messages.joinToString("\n")
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("BLE Log", combined))
                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy All")
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(12.dp)
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            messages.forEachIndexed { index, message ->
                Text("$index: $message", style = MaterialTheme.typography.bodySmall)
                Divider()
            }
        }
    }
}
