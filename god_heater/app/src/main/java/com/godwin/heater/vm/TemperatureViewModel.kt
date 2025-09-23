package com.godwin.heater.vm

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.godwin.heater.R
import com.godwin.heater.util.toHex
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.sqrt

class TemperatureViewModel(private val application: Application) : AndroidViewModel(application) {


    init {
        startPeriodicUpdate()
    }

    // StateFlow to hold current and set temperatures
    private val _currentTemp = MutableStateFlow("0") // Default current temp
    val currentTemp: StateFlow<String> = _currentTemp

    // StateFlow to hold humidity
    private val _currentHumidity = MutableStateFlow("0") // Default current temp
    val currentHumidity: StateFlow<String> = _currentHumidity

    private val _feelsLike = MutableStateFlow("0") // Default current temp
    val feelsLike: StateFlow<String> = _feelsLike

    private val _setTemp = MutableStateFlow(0) // Default set temp
    val setTemp: StateFlow<Int> = _setTemp

    private val _isScanning = MutableStateFlow(false) // Default set temp
    val isScanning: StateFlow<Boolean> = _isScanning

    // StateFlow to hold current and set temperatures
    private val _lastUpdated = MutableStateFlow("-") // Default current temp
    val lastUpdated: StateFlow<String> = _lastUpdated

    // BLE Advertiser
    private val bluetoothAdapter: BluetoothAdapter? =
        (application.getSystemService(BluetoothManager::class.java))?.adapter
    private val advertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser

    private val bluetoothManager = application.getSystemService(BluetoothManager::class.java)
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val scanPeriod = 5000L // 10 seconds scanning

    private var lastFetchTime: Long = 0

    // Unique BLE UUID for advertising (Example UUID)
    private val TEMP_ADVERT_UUID_PART: String = "00001111-0000-1000-8000-00805f9b3400"
    private val TEMP_SCAN_UUID_PART: String = "00002222-0000-1000-8000-00805f9b3400"

    private val COMMAND_SENT_TEMP = 0
    private val COMMAND_SCAN_TEMP = 1

    // Update the set temperature and advertise it
    fun updateSetTemp(temp: Int) {
        _setTemp.update { temp }
    }

    fun sentTemp() {
        advertiseTemperature(COMMAND_SENT_TEMP, _setTemp.value)
    }

    private fun advertiseScanTemp() {
        advertiseTemperature(COMMAND_SCAN_TEMP, _setTemp.value)
    }

    // BLE Advertisement with temperature
    private fun advertiseTemperature(command: Int, temp: Int) {
        advertiser?.let { adv ->
            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM).setConnectable(false)
                .build()

            val tempBytes = byteArrayOf(command.toByte(), temp.toByte())

            Log.d("SENT SERVICE DATA", tempBytes.toHex())
            val uuid: UUID = UUID.fromString(TEMP_ADVERT_UUID_PART)
            // Create Advertise Data with service UUID and temperature data
            val advertiseData = AdvertiseData.Builder().addServiceData(
                ParcelUuid(uuid), tempBytes
            )  // Attach temperature as service data
                .setIncludeDeviceName(true).setIncludeTxPowerLevel(false).build()

            if (ActivityCompat.checkSelfPermission(
                    application, Manifest.permission.BLUETOOTH_ADVERTISE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(application, R.string.no_permission, Toast.LENGTH_LONG).show()
                return
            }

            viewModelScope.launch {
                adv.stopAdvertising(advertiseCallback) // Stop any previous advertising
                adv.startAdvertising(settings, advertiseData, advertiseCallback)
                delay(100)
                adv.stopAdvertising(advertiseCallback)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (_isScanning.value) return

        viewModelScope.launch {
            _isScanning.emit(true)

            advertiseScanTemp()
            delay(200)
            val scanFilter = ScanFilter.Builder()
//                .setServiceUuid(ParcelUuid(UUID.fromString(TEMP_SCAN_UUID_PART)))
                .build()

            val scanSettings =
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

            bluetoothLeScanner?.startScan(
                listOf(scanFilter), scanSettings, scanCallback
            )
            delay(scanPeriod)
            // Stop scanning after `scanPeriod` milliseconds
            stopScan()
        }


    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        viewModelScope.launch {
            _isScanning.emit(false)
        }
        bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val serviceData =
                result.scanRecord?.serviceData?.get(ParcelUuid(UUID.fromString(TEMP_SCAN_UUID_PART)))
            if (serviceData != null) {
                // Parse the service data to extract the temperature
                Log.d("RECEIVED SERVICE DATA", serviceData.toHex())
                if (serviceData.size >= 5) {
                    stopScan()

                    val temperature = parseTemperature(serviceData)
                    val bleTemp = parseBleTemp(serviceData)
                    val humidity = parseHumidity(serviceData)

                    val feelsLike = calculateFeelsLike(temperature, humidity)

                    // Print the parsed temperature values
                    Log.d("BLE", "Parsed Temperature: $temperature")
                    Log.d("BLE", "Parsed BLE Temp: $bleTemp")
                    Log.d("BLE", "Parsed Humidity: $humidity")

                    lastFetchTime = System.currentTimeMillis()

                    viewModelScope.launch {
                        _currentTemp.emit(
                            String.format(
                                Locale.getDefault(), "%.2f", temperature
                            )
                        )
                        _currentHumidity.emit(
                            String.format(
                                Locale.getDefault(), "%.2f", humidity
                            )
                        )
                        _feelsLike.emit(
                            String.format(
                                Locale.getDefault(), "%.2f", feelsLike
                            )
                        )

                        _setTemp.emit(
                            bleTemp
                        )

                        _lastUpdated.emit(millisToTime())
                    }
                    // Use these values as needed, e.g., update the UI or ViewModel
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLEScanner", "Scan failed with error code: $errorCode")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            Toast.makeText(application, R.string.sent_failed, Toast.LENGTH_LONG).show()

            Log.d(this::class.java.name, "advertiseCallback: onStartFailure || $errorCode")
        }

        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(this::class.java.name, "advertiseCallback: onStartSuccess || $settingsInEffect")

        }
    }

    private fun startPeriodicUpdate() {
        viewModelScope.launch {
            while (isActive) {
                delay(10000)  // Update every 1 min// Ensures loop stops if ViewModel is cleared
                startScan()
            }
        }
    }

    // Function to parse the temperature (scaled by 100)
    private fun parseTemperature(tempBytes: ByteArray): Float {
        // Reconstruct the scaled temperature from the first two bytes
        val tempToSend = ((tempBytes[0].toInt() and 0xFF) shl 8) or (tempBytes[1].toInt() and 0xFF)

        // Convert back to float (e.g., 2404 -> 24.04)
        return tempToSend / 100.0f
    }

    private fun parseHumidity(tempBytes: ByteArray): Float {
        // Reconstruct the scaled temperature from the first two bytes
        val humidity = ((tempBytes[3].toInt() and 0xFF) shl 8) or (tempBytes[4].toInt() and 0xFF)

        // Convert back to float (e.g., 2404 -> 24.04)
        return humidity / 100.0f
    }

    // Function to extract the BLE temperature (last byte)
    private fun parseBleTemp(tempBytes: ByteArray): Int {
        return tempBytes[2].toInt() and 0xFF
    }

    private fun millisToTime(): String {
        if (lastFetchTime == 0L) return "-"
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(lastFetchTime))
    }

    /**
     * Calculate "feels like" temperature (Heat Index) in Celsius
     * based on actual temperature (°C) and relative humidity (%).
     */
    fun calculateFeelsLike(tempC: Float, humidity: Float): Float {
        // Convert Celsius to Fahrenheit
        val T = (tempC * 9 / 5) + 32
        val R = humidity

        // NOAA Heat Index formula (in Fahrenheit)
        var HI = -42.379 +
                2.04901523 * T +
                10.14333127 * R +
                -0.22475541 * T * R +
                -0.00683783 * T * T +
                -0.05481717 * R * R +
                0.00122874 * T * T * R +
                0.00085282 * T * R * R +
                -0.00000199 * T * T * R * R

        // Adjustments per NOAA
        if (R < 13 && T >= 80 && T <= 112) {
            HI -= ((13 - R) / 4) * sqrt((17 - abs(T - 95)) / 17.0)
        } else if (R > 85 && T >= 80 && T <= 87) {
            HI += ((R - 85) / 10) * ((87 - T) / 5)
        }

        // Convert back to Celsius
        return ((HI - 32) * 5 / 9).toFloat()
    }

}
