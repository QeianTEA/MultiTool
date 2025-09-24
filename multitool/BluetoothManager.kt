package com.example.multitool

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import com.example.multitool.crypto.CryptoUtils
import java.util.UUID
import android.util.Base64

object BluetoothManager {
    var socket: BluetoothSocket? = null

    /** Compose‐observable connection state */
    var isConnected by mutableStateOf(false)

    var device: BluetoothDevice? = null

    /** Attempts an RFCOMM socket connect. Returns true on success. */
    fun connectToDevice(device: BluetoothDevice): Boolean {
        return try {
            val uuid: UUID = try {
                device.uuids?.firstOrNull()?.uuid
                    ?: UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
            } catch (e: SecurityException) {
                e.printStackTrace()
                return false
            }

            val tmpSocket = try {
                device.createRfcommSocketToServiceRecord(uuid)
            } catch (e: SecurityException) {
                e.printStackTrace()
                return false
            }

            tmpSocket.connect()
            socket = tmpSocket
            this.device = device
            isConnected = true
            true
        } catch (e: IOException) {
            e.printStackTrace()
            disconnect()
            false
        } catch (e: SecurityException) {
            e.printStackTrace()
            false
        }
    }

    /** Cleanly close and reset connection */
    fun disconnect() {
        try { socket?.close() } catch (_: IOException) {}
        socket = null
        device = null
        isConnected = false
    }

    /** Send a UTF-8 string + newline */
    fun sendMessage(message: String): Boolean {
        return try {
            val out = socket?.outputStream ?: return false
            out.write((message + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    /** Read one line (blocks until `\n`) */
    fun receiveMessage(): String? {
        return try {
            val input = socket?.inputStream ?: return null
            val reader = BufferedReader(InputStreamReader(input))
            reader.readLine()
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /** Encrypt-then-send via Base64 */
    fun sendMessageSecure(command: String): Boolean {
        val cipherTextB64 = CryptoUtils.encrypt(command.toByteArray(Charsets.UTF_8))
        return sendMessage(cipherTextB64)
    }

    /** Receive, Base64-decode, decrypt, return UTF-8 string */
    fun receiveMessageSecure(): String? {
        val cipherTextB64 = receiveMessage() ?: return null
        val plainBytes = CryptoUtils.decrypt(cipherTextB64) ?: return null
        return String(plainBytes, Charsets.UTF_8)
    }

    /**
     * Sends a command + newline, then awaits a single-line reply with timeout.
     */
    suspend fun sendAndReceiveSuspend(command: String, timeoutMs: Long = 2000L): String? =
        withContext(Dispatchers.IO) {
            try {
                socket?.outputStream?.let { out ->
                    out.write("$command\n".toByteArray(Charsets.UTF_8))
                    out.flush()
                } ?: return@withContext null

                val reader = BufferedReader(InputStreamReader(socket!!.inputStream))
                withTimeout(timeoutMs) {
                    reader.readLine()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
}
