package com.example.hnu_ppe_control.ble

import java.util.UUID

object BleConstants {

    const val SCAN_PERIOD = 10000L
    const val REQUEST_BLE_PERMISSION = 1001

    // Smart Shield 전용 BLE Service UUID
    val TARGET_SERVICE_UUID: UUID =
        UUID.fromString("089fca17-755f-4578-b8af-ee5e32526b0f")

    // ESP32 → App 센서 데이터 Notify
    val DATA_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB")

    // App → ESP32 위험도 제어 명령 Write
    val CONTROL_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB")

    // Notify 활성화용 CCCD
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
}