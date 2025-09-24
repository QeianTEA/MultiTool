package com.example.multitool.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object BLERepository {
    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages.asStateFlow()

    private val listeners = mutableListOf<(String) -> Unit>()

    fun addMessage(msg: String) {
        _messages.value = _messages.value + msg

        // Notify all registered listeners
        listeners.forEach { it.invoke(msg) }
    }

    fun clear() {
        _messages.value = emptyList()
    }

    // Listener management
    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners.remove(listener)
    }
}
