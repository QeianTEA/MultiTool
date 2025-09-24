package com.example.multitool.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface IrDao {
    @Insert
    suspend fun insert(entry: IREntry)

    @Query("SELECT * FROM ir_entries ORDER BY timestamp DESC")
    fun allEntries(): kotlinx.coroutines.flow.Flow<List<IREntry>>

    @Query("SELECT * FROM ir_entries ORDER BY timestamp DESC")
    suspend fun getAll(): List<IREntry>

    @Query("DELETE FROM ir_entries")
    suspend fun clearAll()
}
