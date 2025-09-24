package com.example.multitool

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

object GlobalState {
    var isBluetoothConnected by mutableStateOf(false)
}
