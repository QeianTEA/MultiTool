package com.example.multitool

import android.bluetooth.BluetoothSocket
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import com.example.multitool.sendFileModeCommandBluetooth
import com.example.multitool.sendFileOverBluetooth
import com.example.multitool.sendFileModeCommandWifi
import com.example.multitool.sendFileOverWifi


fun sendFileModeCommandBluetooth(bluetoothSocket: BluetoothSocket): Boolean {
    return try {
        val output = bluetoothSocket.outputStream
        output.write("ENTER_FILE_MODE\n".toByteArray())
        output.flush()

        val input = bluetoothSocket.inputStream
        val buffer = ByteArray(1024)
        val length = input.read(buffer)
        val response = String(buffer, 0, length).trim()

        response == "READY"
    } catch (e: IOException) {
        false
    }
}



fun sendFileOverBluetooth(socket: BluetoothSocket, data: ByteArray): Boolean {
    val out = socket.outputStream
    out.write(data.size.toString().toByteArray() + "\n".toByteArray())
    out.write(data)
    out.flush()
    return true
}


fun sendFileModeCommandWifi(): Boolean {
    return try {
        val url = URL("http://192.168.4.1/mode/file-transfer")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 3000

        connection.responseCode == 200 &&
                connection.inputStream.bufferedReader().readLine().trim() == "READY"
    } catch (e: Exception) {
        false
    }
}


fun sendFileOverWifi(data: ByteArray): Boolean {
    val url = URL("http://192.168.4.1/upload")
    val conn = url.openConnection() as HttpURLConnection
    conn.doOutput = true
    conn.requestMethod = "POST"
    conn.setRequestProperty("Content-Type", "application/octet-stream")
    conn.outputStream.use { it.write(data) }
    conn.inputStream.close()
    return conn.responseCode == 200
}


