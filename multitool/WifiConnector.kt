package com.example.multitool

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.annotation.RequiresApi

// WifiConnector.kt
object WifiConnector {

    /**
     * Connect to a WPA2-PSK hotspot (API 29+).
     * Calls callback(true) on success, callback(false) on failure or unsupported API.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    fun connectToAp(
        context: Context,
        ssid: String,
        password: String,
        callback: (Boolean) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Not supported below Android 10
            callback(false)
            return
        }

        // Build a WifiNetworkSpecifier for WPA2-PSK
        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(password)
            .build()

        // Build a network request using that specifier
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Register a one-shot network callback
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                // Bind process to this network so HTTP traffic goes over it
                cm.bindProcessToNetwork(network)
                callback(true)
                cm.unregisterNetworkCallback(this)
            }

            override fun onUnavailable() {
                super.onUnavailable()
                callback(false)
                cm.unregisterNetworkCallback(this)
            }
        }
        cm.requestNetwork(request, cb)
    }
}
