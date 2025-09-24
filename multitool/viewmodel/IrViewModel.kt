package com.example.multitool.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.multitool.data.AppSettings
import com.example.multitool.data.DatabaseProvider
import com.example.multitool.data.IREntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

class IrViewModel : ViewModel() {
    private val dao = DatabaseProvider.irDao

    // History of all scans
    val entries = dao.allEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // IR slots (favorites)
    private val _slotContents = MutableStateFlow<List<String>>(listOf("", "", ""))
    val slotContents: StateFlow<List<String>> = _slotContents.asStateFlow()

    fun fetchSlotContents(context: Context) {
        viewModelScope.launch {
            val slots = listOf(
                AppSettings.irSlotValue(context, 1).first(),
                AppSettings.irSlotValue(context, 2).first(),
                AppSettings.irSlotValue(context, 3).first()
            )
            _slotContents.value = slots
        }
    }

    // Provide the IRScreen call compatibility
    fun fetchIrSlots(context: Context) {
        fetchSlotContents(context)
    }

    fun saveToSlot(context: Context, slot: Int, code: String) {
        viewModelScope.launch {
            AppSettings.setIrSlotValue(context, slot, code)
            fetchSlotContents(context)
        }
    }

    fun clearSlot(context: Context, slot: Int) {
        saveToSlot(context, slot, "")
    }

    fun add(code: String) {
        viewModelScope.launch {
            dao.insert(IREntry(code = code))
        }
    }

    // Provide the IRScreen call compatibility
    fun addIr(code: String) {
        add(code)
    }

    fun clearAll() {
        viewModelScope.launch {
            dao.clearAll()
        }
    }
}
