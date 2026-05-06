// Smart Shield 작업자 앱의 메인 화면 흐름을 제어하는 Activity 파일
package com.example.hnu_ppe_control

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.hnu_ppe_control.alert.AlertManager
import com.example.hnu_ppe_control.ble.BleConstants
import com.example.hnu_ppe_control.ble.BleManager
import com.example.hnu_ppe_control.ble.BlePermissionHelper
import com.example.hnu_ppe_control.data.RiskLevel
import com.example.hnu_ppe_control.data.SensorData
import com.example.hnu_ppe_control.firebase.FirebaseStatusUploader
import com.example.hnu_ppe_control.firebase.RiskLogPolicy
import com.example.hnu_ppe_control.parser.SensorDataParser
import com.example.hnu_ppe_control.risk.HeatstrokeAnalyzer
import com.example.hnu_ppe_control.risk.RiskCommandMapper
import com.example.hnu_ppe_control.service.ForegroundServiceController
import com.example.hnu_ppe_control.test.FakeSensorDataProvider
import com.example.hnu_ppe_control.ui.MainUiController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), BleManager.Listener {

    companion object {
        private const val TAG = "SmartShieldBLE"
        private const val REQUEST_NOTIFICATION_PERMISSION = 2001
    }

    private lateinit var bleManager: BleManager
    private lateinit var alertManager: AlertManager
    private lateinit var ui: MainUiController
    private lateinit var riskLogPolicy: RiskLogPolicy
    private lateinit var foregroundServiceController: ForegroundServiceController

    private val foundDeviceList = ArrayList<BleManager.BleDeviceInfo>()

    private var appSessionActive = false
    private var lastSensorData: SensorData? = null
    private var lastRiskLevel: RiskLevel = RiskLevel.SAFE
    private var lastRiskCommand: String = "RISK:SAFE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initManagers()
        initUi()
        requestNotificationPermissionIfNeeded()
    }

    // 기능별 매니저를 Activity에서 조립만 하고, 실제 세부 구현은 각 파일로 분리
    private fun initManagers() {
        bleManager = BleManager(this, this)
        alertManager = AlertManager(this)
        riskLogPolicy = RiskLogPolicy()
        foregroundServiceController = ForegroundServiceController(this)
    }

    private fun initUi() {
        ui = MainUiController(this)
        ui.showDefault(bleManager.isBluetoothAvailable())
        ui.bindActions(
            onScanClicked = { handleScanClicked() },
            onDisconnectClicked = { handleDisconnectClicked() },
            onFakeDataClicked = { handleFakeDataClicked() },
            onDeviceClicked = { position -> handleDeviceClicked(position) }
        )
    }

    private fun handleScanClicked() {
        // BLE 권한과 블루투스 상태를 확인한 뒤 Smart Shield 장치 스캔
        if (!BlePermissionHelper.hasBlePermission(this)) {
            BlePermissionHelper.requestBlePermission(this)
            return
        }

        if (!bleManager.isBluetoothAvailable()) {
            Toast.makeText(this, "블루투스를 지원하지 않는 기기입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bleManager.isBluetoothEnabled()) {
            Toast.makeText(this, "블루투스를 먼저 켜주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        foundDeviceList.clear()
        ui.clearScanList()
        bleManager.startScan()
    }

    private fun handleDisconnectClicked() {
        // 작업자가 직접 연결 해제를 누르면 세션 종료 상태를 Firebase에 반영합니다.
        appSessionActive = false
        bleManager.disconnectManually()
        foregroundServiceController.stop()
    }

    private fun handleFakeDataClicked() {
        // 실제 ESP32 없이 Firebase 업로드 흐름을 검증용 Test코드
        appSessionActive = true
        foregroundServiceController.startIfAllowed()

        val fakeData = FakeSensorDataProvider.randomPayload()
        Log.d(TAG, "Fake data generated: $fakeData")
        handleReceivedData(fakeData)
    }

    private fun handleDeviceClicked(position: Int) {
        if (position < 0 || position >= foundDeviceList.size) return

        appSessionActive = true
        foregroundServiceController.startIfAllowed()
        bleManager.connect(foundDeviceList[position].device)
    }

    override fun onScanStarted() {
        ui.showBleStatus("BLE 상태: 스캔 중")
        ui.showReconnectStatus("재연결 상태: 대기")
        ui.showNoConnectedDevice()
    }

    override fun onScanStopped() {
        ui.showBleStatus("BLE 상태: 스캔 종료")
    }

    override fun onScanFailed(errorCode: Int) {
        ui.showBleStatus("BLE 상태: 스캔 실패($errorCode)")
    }

    override fun onDeviceFound(deviceInfo: BleManager.BleDeviceInfo) {
        // SS_XXXX와 UUID 필터를 통과한 ESP32만 목록에 추가
        runOnUiThread {
            if (foundDeviceList.any { it.address == deviceInfo.address }) return@runOnUiThread

            foundDeviceList.add(deviceInfo)
            ui.addDevice(deviceInfo)
        }
    }

    override fun onBleStatusChanged(message: String) {
        ui.showBleStatus(message)
    }

    override fun onReconnectStatusChanged(message: String) {
        ui.showReconnectStatus(message)
    }

    override fun onConnected(deviceName: String, address: String) {
        appSessionActive = true
        foregroundServiceController.startIfAllowed()

        ui.showBleStatus("BLE 상태: 연결 성공")
        ui.showReconnectStatus("재연결 상태: 대기")
        ui.showConnectedDevice(deviceName, address)
    }

    override fun onDisconnected(manual: Boolean) {
        // BLE만 끊긴 상태와 앱 세션 종료 상태를 Firebase에서 구분할 수 있게 저장
        appSessionActive = !manual
        uploadLastStatus(bleConnected = false, appSessionActive = appSessionActive)

        ui.showBleStatus(if (manual) "BLE 상태: 수동 연결 해제" else "BLE 상태: 연결 끊김")
        ui.showReconnectStatus(if (manual) "재연결 상태: 중지" else "재연결 상태: 시도 중")

        if (manual) {
            foregroundServiceController.stop()
        }
    }

    override fun onReconnectFailed() {
        appSessionActive = false
        uploadLastStatus(bleConnected = false, appSessionActive = false)

        ui.showBleStatus("BLE 상태: 재연결 실패")
        ui.showReconnectStatus("재연결 상태: 10분 초과, 세션 종료")
        ui.showNoConnectedDevice()
        foregroundServiceController.stop()
    }

    override fun onNotifyReady() {
        ui.showBleStatus("BLE 상태: Notify 수신 준비 완료")
        ui.showRiskCommand("Write 준비 완료")
    }

    override fun onDataReceived(rawData: String) {
        handleReceivedData(rawData)
    }

    override fun onWriteResult(command: String, started: Boolean, reason: String?) {
        ui.showWriteResult(command, started, reason)
    }

    private fun handleReceivedData(rawData: String) {
        // 수신 문자열을 정리한 뒤 파싱, 위험도 계산, ESP32 제어, Firebase 업로드 순서로 처리
        val cleanedRawData = rawData.trim()
        Log.d(TAG, "Raw sensor data: $cleanedRawData")

        if (cleanedRawData.isEmpty()) {
            showParseError(rawData)
            return
        }

        val sensorData = SensorDataParser.parse(cleanedRawData)
        if (sensorData == null) {
            showParseError(cleanedRawData)
            return
        }

        val riskLevel = HeatstrokeAnalyzer.analyze(sensorData)
        val command = RiskCommandMapper.toCommand(riskLevel)

        lastSensorData = sensorData
        lastRiskLevel = riskLevel
        lastRiskCommand = command

        ui.showSensorData(sensorData, riskLevel, formatNow())
        ui.showRisk(riskLevel)
        ui.showRiskCommand(command)

        bleManager.writeRiskCommand(command)
        uploadCurrentStatus(sensorData, riskLevel, command)
        uploadRiskLogIfNeeded(sensorData, riskLevel, command)
        alertManager.handleRisk(riskLevel)
    }

    private fun uploadCurrentStatus(
        sensorData: SensorData,
        riskLevel: RiskLevel,
        command: String
    ) {
        // currentStatus는 관리자 앱이 실시간 상태를 읽는 덮어쓰기 경로
        ui.showFirebaseState("Firebase 상태: currentStatus 업로드 중")

        FirebaseStatusUploader.uploadCurrentStatus(
            workerId = sensorData.id,
            deviceName = bleManager.connectedDeviceName,
            temp = sensorData.temp,
            hr = sensorData.hr,
            spo2 = sensorData.spo2,
            env = sensorData.env,
            hum = sensorData.hum.toDouble(),
            lux = sensorData.lux,
            posture = sensorData.posture,
            riskLevel = riskLevel.label,
            riskCommand = command,
            bleConnected = bleManager.isBleConnected,
            appSessionActive = appSessionActive
        )

        ui.showFirebaseState("Firebase 상태: currentStatus 호출 완료")
    }

    private fun uploadRiskLogIfNeeded(
        sensorData: SensorData,
        riskLevel: RiskLevel,
        command: String
    ) {
        // riskLogs는 위험/응급 이벤트만 중복 없이 누적 저장
        if (!riskLogPolicy.shouldUpload(riskLevel)) return

        FirebaseStatusUploader.uploadRiskLog(
            workerId = sensorData.id,
            riskLevel = riskLevel.label,
            riskCommand = command,
            temp = sensorData.temp,
            hr = sensorData.hr,
            spo2 = sensorData.spo2,
            env = sensorData.env,
            hum = sensorData.hum.toDouble(),
            lux = sensorData.lux,
            posture = sensorData.posture,
            message = riskLogPolicy.messageFor(riskLevel)
        )

        ui.showFirebaseState("Firebase 상태: riskLogs 호출 완료")
    }

    private fun uploadLastStatus(
        bleConnected: Boolean,
        appSessionActive: Boolean
    ) {
        // 연결 해제나 앱 종료 시 마지막 센서값을 기준으로 연결 상태만 갱신
        val sensorData = lastSensorData ?: return

        FirebaseStatusUploader.uploadCurrentStatus(
            workerId = sensorData.id,
            deviceName = bleManager.connectedDeviceName,
            temp = sensorData.temp,
            hr = sensorData.hr,
            spo2 = sensorData.spo2,
            env = sensorData.env,
            hum = sensorData.hum.toDouble(),
            lux = sensorData.lux,
            posture = sensorData.posture,
            riskLevel = lastRiskLevel.label,
            riskCommand = lastRiskCommand,
            bleConnected = bleConnected,
            appSessionActive = appSessionActive
        )
    }

    private fun showParseError(rawData: String) {
        Log.w(TAG, "Sensor parse failed. rawData=$rawData")
        ui.showParseError(rawData)
    }

    private fun requestNotificationPermissionIfNeeded() {
        // Android 13 이상은 Foreground Service 알림 표시를 위해 알림 권한을 요청
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                REQUEST_NOTIFICATION_PERMISSION
            )
        }
    }

    private fun formatNow(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA).format(Date())
    }

    override fun onDestroy() {
        super.onDestroy()

        // 앱 화면이 종료되면 세션 종료 상태를 Firebase에 남기고 BLE 리소스를 정리
        appSessionActive = false
        uploadLastStatus(bleConnected = false, appSessionActive = false)
        bleManager.release()
        foregroundServiceController.stop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            BleConstants.REQUEST_BLE_PERMISSION -> {
                val isGranted = grantResults.isNotEmpty() &&
                    grantResults.all { it == PackageManager.PERMISSION_GRANTED }

                Toast.makeText(
                    this,
                    if (isGranted) "BLE 권한이 허용되었습니다." else "BLE 권한이 필요합니다.",
                    Toast.LENGTH_SHORT
                ).show()
            }

            REQUEST_NOTIFICATION_PERMISSION -> {
                Log.d(TAG, "Notification permission result=${grantResults.toList()}")
            }
        }
    }
}
