package com.godwin.heater

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.godwin.heater.vm.TemperatureViewModel

@Composable
fun PermissionScreen(viewModel: TemperatureViewModel = viewModel(), modifier: Modifier) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(checkBLEPermissions(context)) }

    // List of required BLE permissions based on Android version
    val blePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Permission launcher
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissionsResult ->
            hasPermissions = permissionsResult.values.all { it }
            if (hasPermissions) {
                Toast.makeText(context, "✅ Permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "❌ Bluetooth permissions required!", Toast.LENGTH_LONG)
                    .show()
            }
        }
    )

    // UI Layout
    Column(
        modifier = Modifier
            .safeContentPadding()
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        if (hasPermissions) {
            // If permissions are granted, show the temperature screen
            TemperatureScreen(viewModel)
        } else {
            // If permissions are missing, show the request button
            Button(onClick = { requestPermissionLauncher.launch(blePermissions) }) {
                Text("Request Bluetooth Permissions")
            }
        }
    }
}

/**
 * Helper function to check if all required BLE permissions are granted.
 */
fun checkBLEPermissions(context: android.content.Context): Boolean {
    val permissions =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
        }
    return permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}
