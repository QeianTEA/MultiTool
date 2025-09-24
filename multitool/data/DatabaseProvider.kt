package com.example.multitool.data

import android.content.Context
import androidx.room.Room
import com.example.multitool.data.RFIDEntry
import com.example.multitool.data.IREntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DatabaseProvider {
    private var _db: MultiToolDatabase? = null

    /** Call once (e.g. in MainActivity.onCreate) **/
    fun init(context: Context) {
        if (_db == null) {
            _db = Room.databaseBuilder(
                context.applicationContext,
                MultiToolDatabase::class.java,
                "multitool-db"
            ).build()
        }
    }

    /** Exposed DAOs **/
    val rfidDao: RfidDao
        get() = _db!!.rfidDao()

    val irDao: IrDao
        get() = _db!!.irDao()

    // ─── Mock Helpers ───────────────────────────────────────────────

    /** Insert a mock RFID entry on a background thread **/
    fun insertMockRFIDData(uid: String, details: String) {
        CoroutineScope(Dispatchers.IO).launch {
            rfidDao.insert(RFIDEntry(uid = uid, details = details))
        }
    }

    /** Insert a mock IR entry on a background thread **/
    fun insertMockIRData(signal: String) {
        CoroutineScope(Dispatchers.IO).launch {
            irDao.insert(IREntry(code = signal))
        }
    }
}
