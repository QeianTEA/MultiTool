package com.example.multitool.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.multitool.data.AppSettings
import com.example.multitool.data.DatabaseProvider
import com.example.multitool.data.RFIDEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RfidViewModel : ViewModel() {
    private val dao = DatabaseProvider.rfidDao

    val entries: StateFlow<List<RFIDEntry>> = dao
        .allEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _slotContents = MutableStateFlow(listOf("Empty", "Empty", "Empty"))
    val slotContents: StateFlow<List<String>> = _slotContents.asStateFlow()

    fun add(uid: String, details: String) {
        viewModelScope.launch {
            dao.insert(RFIDEntry(uid = uid, details = details))
        }
    }

    fun clearAll() {
        viewModelScope.launch { dao.clearAll() }
    }

    fun fetchSlotContents(context: Context) {
        viewModelScope.launch {
            val s1 = AppSettings.rfidSlotValue(context, 1).firstOrNull() ?: "Empty"
            val s2 = AppSettings.rfidSlotValue(context, 2).firstOrNull() ?: "Empty"
            val s3 = AppSettings.rfidSlotValue(context, 3).firstOrNull() ?: "Empty"
            _slotContents.value = listOf(s1, s2, s3)
        }
    }

    fun saveToSlot(context: Context, slot: Int, uid: String) {
        viewModelScope.launch {
            AppSettings.setRfidSlotValue(context, slot, uid)
            fetchSlotContents(context)
        }
    }

    fun clearSlot(context: Context, slot: Int) {
        viewModelScope.launch {
            AppSettings.setRfidSlotValue(context, slot, "")
            fetchSlotContents(context)
        }
    }
}


