package com.example.multitool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavType
import androidx.navigation.compose.*
import com.example.multitool.ui.theme.MultiToolAppTheme
import com.example.multitool.data.DatabaseProvider
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.multitool.viewmodel.RfidViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController





class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ★ Initialize your Room database here ★
        DatabaseProvider.init(this)

        setContent {
            MultiToolAppTheme {
                AppNavigator()
            }
        }
    }
}


// Temporary flag - in real app, update based on actual connection status
val isUsingBluetooth = true // or false for Wi-Fi

@Composable
fun AppNavigator() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "home") {

        composable("home") { HomeScreen(navController) }
        composable("rfid") { RFIDScreen() }
        composable("ir") { IRScreen() }
        composable("transfer") { TransferScreen() }
        composable("ble") { BleScreen() }
        composable("ble_log") { BLELogScreen() }
        composable("settings") { SettingsScreen() }

    }
}
