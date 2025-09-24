// RFIDEntry.kt
package com.example.multitool.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "rfid_entries")
data class RFIDEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uid: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)
