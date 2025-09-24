// MultiToolDatabase.kt
package com.example.multitool.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RFIDEntry::class, IREntry::class],
    version = 1,
    exportSchema = false
)
abstract class MultiToolDatabase: RoomDatabase() {
    abstract fun rfidDao(): RfidDao
    abstract fun irDao(): IrDao
}
