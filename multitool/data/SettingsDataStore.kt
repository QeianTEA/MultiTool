package com.example.multitool.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore by preferencesDataStore(name = "settings")

object AppSettings {
    // Slot names (labels for UI)
    private val SLOT1 = stringPreferencesKey("slot1_name")
    private val SLOT2 = stringPreferencesKey("slot2_name")
    private val SLOT3 = stringPreferencesKey("slot3_name")

    // Slot values (actual saved data: UID or IR)
    private val RFID_SLOT1 = stringPreferencesKey("rfid_slot1_value")
    private val RFID_SLOT2 = stringPreferencesKey("rfid_slot2_value")
    private val RFID_SLOT3 = stringPreferencesKey("rfid_slot3_value")

    private val IR_SLOT1 = stringPreferencesKey("ir_slot1_value")
    private val IR_SLOT2 = stringPreferencesKey("ir_slot2_value")
    private val IR_SLOT3 = stringPreferencesKey("ir_slot3_value")

    private val BOOT_MESSAGE = stringPreferencesKey("boot_message")
    private val DEFAULT_METHOD = stringPreferencesKey("default_method")

    // ---- READ ----

    fun slot1Name(context: Context): Flow<String> =
        context.dataStore.data.map { it[SLOT1] ?: "Slot 1" }

    fun slot2Name(context: Context): Flow<String> =
        context.dataStore.data.map { it[SLOT2] ?: "Slot 2" }

    fun slot3Name(context: Context): Flow<String> =
        context.dataStore.data.map { it[SLOT3] ?: "Slot 3" }

    fun rfidSlotValue(context: Context, slot: Int): Flow<String> =
        context.dataStore.data.map {
            when (slot) {
                1 -> it[RFID_SLOT1]
                2 -> it[RFID_SLOT2]
                3 -> it[RFID_SLOT3]
                else -> null
            } ?: "Empty"
        }

    fun irSlotValue(context: Context, slot: Int): Flow<String> =
        context.dataStore.data.map {
            when (slot) {
                1 -> it[IR_SLOT1]
                2 -> it[IR_SLOT2]
                3 -> it[IR_SLOT3]
                else -> null
            } ?: "Empty"
        }

    fun bootMessage(context: Context): Flow<String> =
        context.dataStore.data.map { it[BOOT_MESSAGE] ?: "Welcome" }

    fun defaultMethod(context: Context): Flow<String> =
        context.dataStore.data.map { it[DEFAULT_METHOD] ?: "Bluetooth" }

    // ---- WRITE ----

    suspend fun setSlotName(context: Context, slot: Int, name: String) {
        context.dataStore.edit {
            when (slot) {
                1 -> it[SLOT1] = name
                2 -> it[SLOT2] = name
                3 -> it[SLOT3] = name
            }
        }
    }

    suspend fun setRfidSlotValue(context: Context, slot: Int, value: String) {
        context.dataStore.edit {
            when (slot) {
                1 -> it[RFID_SLOT1] = value
                2 -> it[RFID_SLOT2] = value
                3 -> it[RFID_SLOT3] = value
            }
        }
    }

    suspend fun setIrSlotValue(context: Context, slot: Int, value: String) {
        context.dataStore.edit {
            when (slot) {
                1 -> it[IR_SLOT1] = value
                2 -> it[IR_SLOT2] = value
                3 -> it[IR_SLOT3] = value
            }
        }
    }

    suspend fun setBootMessage(context: Context, message: String) {
        context.dataStore.edit { it[BOOT_MESSAGE] = message }
    }

    suspend fun setDefaultMethod(context: Context, method: String) {
        context.dataStore.edit { it[DEFAULT_METHOD] = method }
    }

}

