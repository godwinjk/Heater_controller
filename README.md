# Heater Controller
Turn On/Off the Heater based on ambient temperature. This repo contains ESP32 and Android codes for controlling the device through Android devices. 


Arduino Code
I am using the ESP32-C3 Mini as the microcontroller because of its compact size and support for both BLE and Wi-Fi. The device will continuously scan for service data associated with a specific UUID.

When it detects data containing a command ID, it will process and parse the temperature accordingly. Additionally, the device will listen for another command that requests the current temperature and will send it back over BLE.

The system will also send both the user-set temperature and the current temperature as BLE service data.

Android Code
This is an Android UI application designed to scan and control the ESP32-C3 device. The app performs BLE scans at 10-second intervals and has the ability to manually request data from the device.

A slider control allows the user to set the desired temperature. When the slider is moved, the app will send the set temperature as BLE service data using a specific UUID.

The Android app is developed using Jetpack Compose for a modern and efficient UI.
