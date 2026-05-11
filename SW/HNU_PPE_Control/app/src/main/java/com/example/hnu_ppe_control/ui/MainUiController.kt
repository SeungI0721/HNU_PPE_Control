// Smart Shield 작업자 앱의 MainActivity 화면 표시와 버튼 연결을 담당하는 파일
package com.example.hnu_ppe_control.ui

import android.app.Activity
import android.graphics.Color
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import com.example.hnu_ppe_control.R
import com.example.hnu_ppe_control.ble.BleManager
import com.example.hnu_ppe_control.data.RiskLevel
import com.example.hnu_ppe_control.data.SensorData

class MainUiController(
    private val activity: Activity
) {
    private val txtBleState: TextView = activity.findViewById(R.id.txtBleState)
    private val txtReconnectState: TextView = activity.findViewById(R.id.txtReconnectState)
    private val txtConnectedDevice: TextView = activity.findViewById(R.id.txtConnectedDevice)
    private val txtData: TextView = activity.findViewById(R.id.txtData)
    private val txtRiskState: TextView = activity.findViewById(R.id.txtRiskState)
    private val txtRiskCommand: TextView = activity.findViewById(R.id.txtRiskCommand)
    private val txtFirebaseState: TextView = activity.findViewById(R.id.txtFirebaseState)
    private val txtLastUpdate: TextView = activity.findViewById(R.id.txtLastUpdate)
    private val btnScan: Button = activity.findViewById(R.id.btnScan)
    private val btnDisconnect: Button = activity.findViewById(R.id.btnDisconnect)
    private val btnFakeData: Button = activity.findViewById(R.id.btnFakeData)
    private val listBle: ListView = activity.findViewById(R.id.listBle)

    private val deviceInfoList = ArrayList<String>()
    private val deviceAdapter = ArrayAdapter(
        activity,
        android.R.layout.simple_list_item_1,
        deviceInfoList
    )

    init {
        // BLE 검색 결과를 ListView에 보여주기 위한 기본 어댑터
        listBle.adapter = deviceAdapter
    }

    fun bindActions(
        onScanClicked: () -> Unit,
        onDisconnectClicked: () -> Unit,
        onFakeDataClicked: () -> Unit,
        onDeviceClicked: (position: Int) -> Unit
    ) {
        // 버튼과 리스트 클릭 이벤트는 Activity의 흐름 제어 함수로 넘깁니다.
        btnScan.setOnClickListener { onScanClicked() }
        btnDisconnect.setOnClickListener { onDisconnectClicked() }
        btnFakeData.setOnClickListener { onFakeDataClicked() }
        listBle.setOnItemClickListener { _, _, position, _ -> onDeviceClicked(position) }
    }

    fun showDefault(bleAvailable: Boolean) {
        txtBleState.text = if (bleAvailable) "BLE 상태: 준비 완료" else "BLE 상태: 사용 불가"
        txtReconnectState.text = "재연결 상태: 대기"
        txtConnectedDevice.text = "연결 장치: 없음"
        txtData.text = "센서 데이터 없음"
        txtRiskCommand.text = "ESP32 명령: 없음"
        txtFirebaseState.text = "Firebase 상태: 대기"
        txtLastUpdate.text = "마지막 업데이트: 없음"
        showRisk(RiskLevel.SAFE)
    }

    fun showBleStatus(message: String) {
        activity.runOnUiThread {
            txtBleState.text = message
        }
    }

    fun showReconnectStatus(message: String) {
        activity.runOnUiThread {
            txtReconnectState.text = message
        }
    }

    fun showConnectedDevice(deviceName: String, address: String) {
        activity.runOnUiThread {
            txtConnectedDevice.text = "연결 장치: $deviceName / $address"
        }
    }

    fun showNoConnectedDevice() {
        activity.runOnUiThread {
            txtConnectedDevice.text = "연결 장치: 없음"
        }
    }

    fun clearScanList() {
        deviceInfoList.clear()
        deviceAdapter.notifyDataSetChanged()
    }

    fun addDevice(deviceInfo: BleManager.BleDeviceInfo) {
        // BLE 스캔 결과는 작업자가 선택할 수 있도록 이름과 주소를 같이 표시합니다.
        deviceInfoList.add("이름: ${deviceInfo.name}\n주소: ${deviceInfo.address}")
        deviceAdapter.notifyDataSetChanged()
    }

    fun showSensorData(
        sensorData: SensorData,
        riskLevel: RiskLevel,
        formattedTime: String
    ) {
        val display = """
            workerId: ${sensorData.id}
            TEMP 피부 온도: ${sensorData.temp}
            HR 심박수: ${sensorData.hr}
            SPO2 산소포화도: ${sensorData.spo2?.toString() ?: "없음"}
            ENV 주변 온도: ${sensorData.env}
            HUM 습도: ${sensorData.hum}
            LUX 조도: ${sensorData.lux}
            직사광선 추정: ${if (sensorData.directSunlight) "예" else "아니오"}
            POSTURE 자세: ${sensorData.posture}
            위험 단계: ${riskLevel.label}
        """.trimIndent()

        activity.runOnUiThread {
            txtData.text = display
            txtLastUpdate.text = "마지막 업데이트: $formattedTime"
        }
    }

    fun showRisk(riskLevel: RiskLevel) {
        activity.runOnUiThread {
            txtRiskState.text = "상태: ${riskLevel.label}"
            txtRiskState.setBackgroundColor(Color.parseColor(colorForRisk(riskLevel)))
        }
    }

    fun showRiskCommand(command: String) {
        activity.runOnUiThread {
            txtRiskCommand.text = "ESP32 명령: $command"
        }
    }

    fun showWriteResult(command: String, started: Boolean, reason: String?) {
        activity.runOnUiThread {
            txtRiskCommand.text = when {
                started -> "ESP32 명령: $command 전송 시작"
                command.isBlank() -> "ESP32 명령: Write 특성 없음"
                reason == "duplicate" -> "ESP32 명령: $command 중복 생략"
                else -> "ESP32 명령: $command 전송 실패($reason)"
            }
        }
    }

    fun showFirebaseState(message: String) {
        activity.runOnUiThread {
            txtFirebaseState.text = message
        }
    }

    fun showParseError(rawData: String) {
        activity.runOnUiThread {
            txtData.text = """
                데이터 파싱 오류

                수신 원본:
                $rawData
            """.trimIndent()
            txtRiskState.text = "상태: 오류"
            txtRiskState.setBackgroundColor(Color.parseColor("#777777"))
            txtFirebaseState.text = "Firebase 상태: 파싱 실패로 업로드 안 함"
        }
    }

    private fun colorForRisk(riskLevel: RiskLevel): String {
        return when (riskLevel) {
            RiskLevel.SAFE -> "#2E7D32"
            RiskLevel.CAUTION -> "#F9A825"
            RiskLevel.DANGER -> "#D32F2F"
            RiskLevel.EMERGENCY -> "#B71C1C"
            RiskLevel.ERROR -> "#777777"
        }
    }
}
