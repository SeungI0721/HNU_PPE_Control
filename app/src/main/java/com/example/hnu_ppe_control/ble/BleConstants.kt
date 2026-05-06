// Smart Shield ESP32와 Android 앱이 공유하는 BLE UUID와 요청 코드를 관리하는 파일
package com.example.hnu_ppe_control.ble

import java.util.UUID

object BleConstants {

    // BLE 스캔은 배터리 보호를 위해 10초만 진행
    const val SCAN_PERIOD = 10000L
    const val REQUEST_BLE_PERMISSION = 1001

    // Smart Shield 전용 BLE Service UUID
    val TARGET_SERVICE_UUID: UUID =
        UUID.fromString("089fca17-755f-4578-b8af-ee5e32526b0f")

    // ESP32에서 앱으로 센서 데이터를 Notify하는 Characteristic
    val DATA_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB")

    // 앱에서 ESP32로 위험 제어 명령을 Write하는 Characteristic
    val CONTROL_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB")

    // Notify 활성화를 위한 CCCD Descriptor
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
}
