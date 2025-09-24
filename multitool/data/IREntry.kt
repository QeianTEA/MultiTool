// IREntry.kt
package com.example.multitool.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ir_entries")
data class IREntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val timestamp: Long = System.currentTimeMillis()
)
