package com.example.multitool.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface RfidDao {
    @Insert
    suspend fun insert(entry: RFIDEntry)

    @Query("SELECT * FROM rfid_entries ORDER BY timestamp DESC")
    fun allEntries(): kotlinx.coroutines.flow.Flow<List<RFIDEntry>>

    @Query("SELECT * FROM rfid_entries ORDER BY timestamp DESC")
    suspend fun getAll(): List<RFIDEntry>

    @Query("DELETE FROM rfid_entries")
    suspend fun clearAll()
}
