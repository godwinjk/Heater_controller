#include <BLEDevice.h>
#include <Wire.h>
#include <Adafruit_AHTX0.h>
#include <Preferences.h>

#define RELAY_PIN 3                                          // Relay connected to GPIO 3
#define LED_PIN 8                                            // LED connected to GPIO 2
#define UUID_PATTERN "00001111-0000-1000-8000-00805f9b3400"  // First 35 characters of UUID (last byte is temperature)
#define UUID_PATTERN_ADVERT "00002222-0000-1000-8000-00805f9b3400"

Adafruit_AHTX0 aht;

int bleTemp = 18;
float currentTemp = 0;      // Sensor temperature
float currentHumidity = 0;  // Sensor humidity

int skipRelay = 0;

Preferences prefs;

void handleAdvertisedDevice(BLEAdvertisedDevice advertisedDevice, BLEUUID expectedUUID) {
  // Check if the device has a service UUID
  if (advertisedDevice.getServiceDataUUID().equals(expectedUUID)) {
    // Get the service UUID from the advertised device
    Serial.print("Received expected service UUID: ");
    Serial.println(expectedUUID.toString());
    // Compare the received UUID with the expected one

    // Check if the device contains service data
    if (advertisedDevice.haveServiceData()) {
      // Get the service data (returns a vector of bytes)
      String serviceData = advertisedDevice.getServiceData();

      // Ensure there is at least one byte of service data (checking length of string)
      if (serviceData.length() > 0) {
        int dataLength = serviceData.length();

        uint8_t serviceDataBytes[dataLength];  // Byte array to store raw data

        for (int i = 0; i < dataLength; i++) {
          serviceDataBytes[i] = (uint8_t)serviceData[i];  // Convert each character to a byte
        }

        // Now you have serviceDataBytes[] as a raw byte array
        Serial.println("Converted to uint8_t array:");
        for (int i = 0; i < dataLength; i++) {
          Serial.print("0x");
          Serial.print(serviceDataBytes[i], HEX);
          Serial.print(" ");
        }
        Serial.println();
        // Parse the first byte for command (assumed format in hex)
        uint8_t commandByte = serviceDataBytes[0];
        // Check the command byte value
        if (commandByte == 0) {
          byte tempByte = serviceDataBytes[1];  // Convert second byte (temperature) from hex
          int temperature = tempByte;           // Now it's an integer (temperature value)

          // Print the parsed temperature
          Serial.print("Parsed Temperature: ");
          Serial.println(temperature);

          bleTemp = temperature;  // Store the temperature for further use

          prefs.begin("settings", true);
          //Update the ble temp in preference
          prefs.putInt("setTemp", bleTemp);
          prefs.end();

          blinkLED(LED_PIN, 5, 50);
          skipRelay = 0;
        } else if (commandByte == 1) {
          // Handle command 1 (e.g., call send function)
          sendBLETemperature();
        }
      }
    }
  }
}


// MyAdvertisedDeviceCallbacks class with the onResult method calling handleAdvertisedDevice
class MyAdvertisedDeviceCallbacks : public BLEAdvertisedDeviceCallbacks {
  void onResult(BLEAdvertisedDevice advertisedDevice) {
    BLEUUID expectedUUID(UUID_PATTERN);

    // Call the function to handle the advertised device
    handleAdvertisedDevice(advertisedDevice, expectedUUID);
  }
};


void sendBLETemperature() {
  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->stop();  // Stop current advertisements before changing

  // Convert float temperature to integer (scaled by 100)
  int tempToSend = (int)(currentTemp * 100);  // e.g., 24.04 -> 2404

  // Convert float humidity to integer (scaled by 100)
  int humidityToSend = (int)(currentHumidity * 100);  // e.g., 24.04 -> 2404

  // Convert tempToSend to 2 bytes (16-bit integer)
  byte tempBytes[5];
  tempBytes[0] = tempToSend >> 8;    // Most significant byte (MSB)
  tempBytes[1] = tempToSend & 0xFF;  // Least significant byte (LSB)
  tempBytes[2] = bleTemp;
  tempBytes[3] = humidityToSend >> 8;    //MSB
  tempBytes[4] = humidityToSend & 0xFF;  //LSB

  Serial.print("📡 Advertising Temp: ");
  Serial.print(tempToSend);
  Serial.println(" (scaled)");

  // Create BLE Advertisement Data
  BLEAdvertisementData advData;
  // advData.setCompleteServices(BLEUUID(UUID_PATTERN_ADVERT));                          // Add Service UUID
  advData.setServiceData(BLEUUID(UUID_PATTERN_ADVERT), String((char*)tempBytes, 5));  // Send temperature as service data (3 bytes)

  pAdvertising->setAdvertisementData(advData);
  pAdvertising->setScanResponse(true);
  pAdvertising->setMinPreferred(0x06);
  pAdvertising->setMinPreferred(0x12);
  pAdvertising->setMinInterval(160);  // 100ms
  pAdvertising->setMaxInterval(160);  // 100ms

  pAdvertising->start();
  Serial.println("📡 Sending BLE Temperature Response...");
  // Stop advertisement after 1 secon
  Serial.println("📡 Advertising stopped after 1 second.");
  blinkLED(LED_PIN, 2, 100);
}

void blinkLED(int pin, int times, int delayMs) {
  for (int i = 0; i < times; i++) {
    digitalWrite(pin, LOW);
    delay(delayMs);
    digitalWrite(pin, HIGH);
    delay(delayMs);
  }
}

void stopAdvertising() {
  BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->stop();  // Stop current advertisements before changing
}

void setup() {
  Serial.begin(115200);
  Serial.println("Initializing...");

  prefs.begin("settings", false);
  // Get or set ble temp
  bleTemp = prefs.getInt("setTemp", 18);
  prefs.end();

  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, LOW);  // Ensure relay is OFF initially

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, HIGH);  // Ensure LED is OFF initially

  Wire.begin(6, 7);  // Try GPIO 6 (SDA) & 7 (SCL)
  delay(1000);       // Give sensor time to start

  Serial.println("Initializing AHT10...");
  if (!aht.begin()) {
    Serial.println("❌ AHT10 sensor NOT found! Check wiring.");
    return;  // Stop execution if sensor not found
  }
  Serial.println("✅ AHT10 sensor detected.");

  Serial.println("Starting BLE Scanner...");
  BLEDevice::init("God_H");
  BLEScan* pBLEScan = BLEDevice::getScan();
  pBLEScan->setAdvertisedDeviceCallbacks(new MyAdvertisedDeviceCallbacks());
  pBLEScan->setActiveScan(true);
}

void loop() {
  Serial.println("BLE Scanning...");

  BLEScan* pBLEScan = BLEDevice::getScan();
  pBLEScan->start(5, false);

  Serial.println("Reading AHT10 Sensor...");
  sensors_event_t humidity, temp;
  aht.getEvent(&humidity, &temp);

  Serial.print("AHT10 Sensor Temperature: ");
  Serial.println(temp.temperature);

  currentTemp = temp.temperature;
  currentHumidity = humidity.relative_humidity;
  if (currentTemp < -40 || currentTemp > 100) {
    Serial.println("⚠️ Invalid temperature reading! Check sensor.");
    blinkLED(LED_PIN, 2, 1000);
    return;
  }
  if (skipRelay >= 10) {
    skipRelay = 0;

    int current = (int)currentTemp;
    //The logic behind this when temprature go down turn off relay and wiring
    // will be NO so it will natually open, why becuase if the battery or relay or some issue with this, then heater will work
    if (current <= bleTemp) {
      digitalWrite(RELAY_PIN, LOW);
      Serial.println("Relay OFF (AHT10 < BLE Temp)");
    } else {
      digitalWrite(RELAY_PIN, HIGH);
      Serial.println("Relay ON (AHT10 >= BLE Temp)");
    }
  }
  skipRelay += 1;

  Serial.print("Sensor Temp: ");
  Serial.print(currentTemp);
  Serial.print("°C | Set Temp: ");
  Serial.print(bleTemp);
  Serial.print("°C | Humidity: ");
  Serial.print(currentHumidity);
  Serial.print("| Skip:");
  Serial.println(skipRelay);
}