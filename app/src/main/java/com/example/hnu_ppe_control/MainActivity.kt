package com.example.hnu_ppe_control

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
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
import com.example.hnu_ppe_control.parser.SensorDataParser
import com.example.hnu_ppe_control.risk.HeatstrokeAnalyzer
import com.example.hnu_ppe_control.risk.RiskCommandMapper
import com.example.hnu_ppe_control.service.SmartShieldForegroundService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), BleManager.Listener {

    companion object {
        private const val TAG = "SmartShieldBLE"
        private const val FIREBASE_TAG = "SmartShieldFirebase"
        private const val REQUEST_NOTIFICATION_PERMISSION = 2001
    }

    private lateinit var txtBleState: TextView
    private lateinit var txtReconnectState: TextView
    private lateinit var txtConnectedDevice: TextView
    private lateinit var txtData: TextView
    private lateinit var txtRiskState: TextView
    private lateinit var txtRiskCommand: TextView
    private lateinit var txtFirebaseState: TextView
    private lateinit var txtLastUpdate: TextView
    private lateinit var btnScan: Button
    private lateinit var btnDisconnect: Button
    private lateinit var btnFakeData: Button
    private lateinit var listBle: ListView

    private lateinit var bleManager: BleManager
    private lateinit var alertManager: AlertManager
    private lateinit var deviceAdapter: ArrayAdapter<String>

    private val deviceInfoList = ArrayList<String>()
    private val foundDeviceList = ArrayList<BleManager.BleDeviceInfo>()

    private var appSessionActive = false
    private var lastLoggedRiskLevel: RiskLevel? = null
    private var lastSensorData: SensorData? = null
    private var lastRiskLevel: RiskLevel = RiskLevel.SAFE
    private var lastRiskCommand: String = "RISK:SAFE"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initManagers()
        initDeviceList()
        initDefaultUi()
        initListeners()
        requestNotificationPermissionIfNeeded()
    }

    private fun initViews() {
        txtBleState = findViewById(R.id.txtBleState)
        txtReconnectState = findViewById(R.id.txtReconnectState)
        txtConnectedDevice = findViewById(R.id.txtConnectedDevice)
        txtData = findViewById(R.id.txtData)
        txtRiskState = findViewById(R.id.txtRiskState)
        txtRiskCommand = findViewById(R.id.txtRiskCommand)
        txtFirebaseState = findViewById(R.id.txtFirebaseState)
        txtLastUpdate = findViewById(R.id.txtLastUpdate)
        btnScan = findViewById(R.id.btnScan)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        btnFakeData = findViewById(R.id.btnFakeData)
        listBle = findViewById(R.id.listBle)
    }

    private fun initManagers() {
        bleManager = BleManager(this, this)
        alertManager = AlertManager(this)
    }

    private fun initDeviceList() {
        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceInfoList)
        listBle.adapter = deviceAdapter
    }

    private fun initDefaultUi() {
        txtBleState.text = "BLE 상태: 준비 중"
        txtReconnectState.text = "재연결 상태: 대기"
        txtConnectedDevice.text = "연결 장치: 없음"
        txtData.text = "센서 데이터 없음"
        txtRiskCommand.text = "ESP32 명령: 없음"
        txtFirebaseState.text = "Firebase 상태: 대기"
        txtLastUpdate.text = "마지막 업데이트: 없음"

        txtBleState.text = if (bleManager.isBluetoothAvailable()) {
            "BLE 상태: 준비 완료"
        } else {
            "BLE 상태: 사용 불가"
        }

        updateRiskUI(RiskLevel.SAFE)
    }

    private fun initListeners() {
        btnScan.setOnClickListener {
            if (!BlePermissionHelper.hasBlePermission(this)) {
                BlePermissionHelper.requestBlePermission(this)
                return@setOnClickListener
            }

            if (!bleManager.isBluetoothAvailable()) {
                Toast.makeText(this, "블루투스를 지원하지 않는 기기입니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!bleManager.isBluetoothEnabled()) {
                Toast.makeText(this, "블루투스를 먼저 켜주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            clearScanList()
            bleManager.startScan()
        }

        btnDisconnect.setOnClickListener {
            appSessionActive = false
            bleManager.disconnectManually()
            stopForegroundService()
        }

        btnFakeData.setOnClickListener {
            appSessionActive = true
            startForegroundService()

            val fakeData = generateFakeSensorData()
            Log.d(TAG, "Fake data generated: $fakeData")
            handleReceivedData(fakeData)
        }

        listBle.setOnItemClickListener { _, _, position, _ ->
            if (position < 0 || position >= foundDeviceList.size) return@setOnItemClickListener

            appSessionActive = true
            startForegroundService()
            bleManager.connect(foundDeviceList[position].device)
        }
    }

    override fun onScanStarted() {
        runOnUiThread {
            txtBleState.text = "BLE 상태: 스캔 중"
            txtReconnectState.text = "재연결 상태: 대기"
            txtConnectedDevice.text = "연결 장치: 없음"
        }
    }

    override fun onScanStopped() {
        runOnUiThread {
            txtBleState.text = "BLE 상태: 스캔 종료"
        }
    }

    override fun onScanFailed(errorCode: Int) {
        runOnUiThread {
            txtBleState.text = "BLE 상태: 스캔 실패($errorCode)"
        }
    }

    override fun onDeviceFound(deviceInfo: BleManager.BleDeviceInfo) {
        runOnUiThread {
            if (foundDeviceList.any { it.address == deviceInfo.address }) {
                return@runOnUiThread
            }

            foundDeviceList.add(deviceInfo)
            deviceInfoList.add("이름: ${deviceInfo.name}\n주소: ${deviceInfo.address}")
            deviceAdapter.notifyDataSetChanged()
        }
    }

    override fun onBleStatusChanged(message: String) {
        runOnUiThread {
            txtBleState.text = message
        }
    }

    override fun onReconnectStatusChanged(message: String) {
        runOnUiThread {
            txtReconnectState.text = message
        }
    }

    override fun onConnected(deviceName: String, address: String) {
        appSessionActive = true
        startForegroundService()

        runOnUiThread {
            txtBleState.text = "BLE 상태: 연결 성공"
            txtReconnectState.text = "재연결 상태: 대기"
            txtConnectedDevice.text = "연결 장치: $deviceName / $address"
        }
    }

    override fun onDisconnected(manual: Boolean) {
        appSessionActive = !manual
        uploadLastStatus(bleConnected = false, appSessionActive = appSessionActive)

        runOnUiThread {
            txtBleState.text = if (manual) {
                "BLE 상태: 수동 연결 해제"
            } else {
                "BLE 상태: 연결 끊김"
            }
            txtReconnectState.text = if (manual) {
                "재연결 상태: 중지"
            } else {
                "재연결 상태: 시도 중"
            }
        }

        if (manual) {
            stopForegroundService()
        }
    }

    override fun onReconnectFailed() {
        appSessionActive = false
        uploadLastStatus(bleConnected = false, appSessionActive = false)

        runOnUiThread {
            txtBleState.text = "BLE 상태: 재연결 실패"
            txtReconnectState.text = "재연결 상태: 10분 초과, 세션 종료"
            txtConnectedDevice.text = "연결 장치: 없음"
        }

        stopForegroundService()
    }

    override fun onNotifyReady() {
        runOnUiThread {
            txtBleState.text = "BLE 상태: Notify 수신 준비 완료"
            txtRiskCommand.text = "ESP32 명령: Write 준비 완료"
        }
    }

    override fun onDataReceived(rawData: String) {
        handleReceivedData(rawData)
    }

    override fun onWriteResult(command: String, started: Boolean, reason: String?) {
        runOnUiThread {
            txtRiskCommand.text = when {
                started -> "ESP32 명령: $command 전송 시작"
                command.isBlank() -> "ESP32 명령: Write 특성 없음"
                reason == "duplicate" -> "ESP32 명령: $command 중복 생략"
                else -> "ESP32 명령: $command 전송 실패($reason)"
            }
        }
    }

    private fun handleReceivedData(rawData: String) {
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

        updateSensorUI(sensorData, riskLevel)
        updateRiskUI(riskLevel)
        updateCommandUI(command)

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
        txtFirebaseState.text = "Firebase 상태: currentStatus 업로드 중"

        FirebaseStatusUploader.uploadCurrentStatus(
            workerId = sensorData.id,
            deviceName = bleManager.connectedDeviceName,
            temp = sensorData.temp,
            hr = sensorData.hr,
            env = sensorData.env,
            hum = sensorData.hum.toDouble(),
            posture = sensorData.posture,
            riskLevel = riskLevel.label,
            riskCommand = command,
            bleConnected = bleManager.isBleConnected,
            appSessionActive = appSessionActive
        )

        txtFirebaseState.text = "Firebase 상태: currentStatus 호출 완료"
    }

    private fun uploadRiskLogIfNeeded(
        sensorData: SensorData,
        riskLevel: RiskLevel,
        command: String
    ) {
        val shouldLog = riskLevel == RiskLevel.DANGER || riskLevel == RiskLevel.EMERGENCY

        if (!shouldLog) {
            if (lastLoggedRiskLevel != null) {
                Log.d(FIREBASE_TAG, "riskLog duplicate guard reset")
            }
            lastLoggedRiskLevel = null
            return
        }

        if (lastLoggedRiskLevel == riskLevel) {
            Log.d(FIREBASE_TAG, "riskLog skipped. Duplicate riskLevel=${riskLevel.name}")
            return
        }

        lastLoggedRiskLevel = riskLevel

        FirebaseStatusUploader.uploadRiskLog(
            workerId = sensorData.id,
            riskLevel = riskLevel.label,
            riskCommand = command,
            temp = sensorData.temp,
            hr = sensorData.hr,
            env = sensorData.env,
            hum = sensorData.hum.toDouble(),
            posture = sensorData.posture,
            message = when (riskLevel) {
                RiskLevel.DANGER -> "위험 상태 감지"
                RiskLevel.EMERGENCY -> "응급 상태 감지"
                else -> "상태 감지"
            }
        )

        txtFirebaseState.text = "Firebase 상태: riskLogs 호출 완료"
    }

    private fun uploadLastStatus(
        bleConnected: Boolean,
        appSessionActive: Boolean
    ) {
        val sensorData = lastSensorData ?: return

        FirebaseStatusUploader.uploadCurrentStatus(
            workerId = sensorData.id,
            deviceName = bleManager.connectedDeviceName,
            temp = sensorData.temp,
            hr = sensorData.hr,
            env = sensorData.env,
            hum = sensorData.hum.toDouble(),
            posture = sensorData.posture,
            riskLevel = lastRiskLevel.label,
            riskCommand = lastRiskCommand,
            bleConnected = bleConnected,
            appSessionActive = appSessionActive
        )
    }

    private fun updateSensorUI(sensorData: SensorData, riskLevel: RiskLevel) {
        val display = """
            workerId: ${sensorData.id}
            TEMP 피부 온도: ${sensorData.temp}
            HR 심박수: ${sensorData.hr}
            ENV 주변 온도: ${sensorData.env}
            HUM 습도: ${sensorData.hum}
            POSTURE 자세: ${sensorData.posture}
            위험 단계: ${riskLevel.label}
        """.trimIndent()

        runOnUiThread {
            txtData.text = display
            txtLastUpdate.text = "마지막 업데이트: ${formatNow()}"
        }
    }

    private fun updateRiskUI(riskLevel: RiskLevel) {
        runOnUiThread {
            txtRiskState.text = "상태: ${riskLevel.label}"

            val color = when (riskLevel) {
                RiskLevel.SAFE -> "#2E7D32"
                RiskLevel.CAUTION -> "#F9A825"
                RiskLevel.DANGER -> "#D32F2F"
                RiskLevel.EMERGENCY -> "#B71C1C"
                RiskLevel.ERROR -> "#777777"
            }

            txtRiskState.setBackgroundColor(Color.parseColor(color))
        }
    }

    private fun updateCommandUI(command: String) {
        runOnUiThread {
            txtRiskCommand.text = "ESP32 명령: $command"
        }
    }

    private fun showParseError(rawData: String) {
        Log.w(TAG, "Sensor parse failed. rawData=$rawData")

        runOnUiThread {
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

    private fun clearScanList() {
        foundDeviceList.clear()
        deviceInfoList.clear()
        deviceAdapter.notifyDataSetChanged()
    }

    private fun generateFakeSensorData(): String {
        val fakeSamples = listOf(
            "ID:0001,TEMP:36.5,HR:82,ENV:28.5,HUM:55,POSTURE:NORMAL",
            "ID:0001,TEMP:37.6,HR:105,ENV:31.5,HUM:72,POSTURE:NORMAL",
            "ID:0001,TEMP:38.1,HR:125,ENV:34.2,HUM:81,POSTURE:WARNING",
            "ID:0001,TEMP:37.8,HR:130,ENV:35.5,HUM:85,POSTURE:FALL",
            "ID:0001,TEMP:38.5,HR:140,ENV:36.0,HUM:88,POSTURE:EMERGENCY"
        )
        return fakeSamples.random()
    }

    private fun startForegroundService() {
        if (!BlePermissionHelper.hasConnectPermission(this)) {
            Log.w(TAG, "Foreground service start skipped. BLUETOOTH_CONNECT permission is missing.")
            return
        }

        val intent = Intent(this, SmartShieldForegroundService::class.java).apply {
            action = SmartShieldForegroundService.ACTION_START
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, intent)
            } else {
                startService(intent)
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Foreground service start failed", e)
        }
    }

    private fun stopForegroundService() {
        val intent = Intent(this, SmartShieldForegroundService::class.java).apply {
            action = SmartShieldForegroundService.ACTION_STOP
        }
        try {
            startService(intent)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Foreground service stop failed", e)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

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

        appSessionActive = false
        uploadLastStatus(bleConnected = false, appSessionActive = false)
        bleManager.release()
        stopForegroundService()
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
