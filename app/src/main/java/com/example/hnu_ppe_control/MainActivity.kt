package com.example.hnu_ppe_control

import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.hnu_ppe_control.alert.AlertManager
import com.example.hnu_ppe_control.ble.BleConstants
import com.example.hnu_ppe_control.ble.BlePermissionHelper
import com.example.hnu_ppe_control.data.RiskLevel
import com.example.hnu_ppe_control.data.SensorData
import com.example.hnu_ppe_control.parser.SensorDataParser
import com.example.hnu_ppe_control.risk.HeatstrokeAnalyzer
import com.example.hnu_ppe_control.risk.RiskCommandMapper
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SmartShieldBLE"
    }

    private lateinit var txtBleState: TextView
    private lateinit var txtConnectedDevice: TextView
    private lateinit var txtData: TextView
    private lateinit var txtRiskState: TextView
    private lateinit var btnScan: Button
    private lateinit var listBle: ListView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null

    private var isBleConnected = false
    private var isServiceDiscovered = false
    private var isNotifyReady = false
    private var connectedDeviceAddress: String? = null

    private var lastConnectedDevice: BluetoothDevice? = null

    private var isReconnecting = false
    private var reconnectStartTime = 0L
    private val reconnectIntervalMs = 3000L
    private val reconnectMaxDurationMs = 10 * 60 * 1000L

    private var lastDataReceivedTime = 0L
    private val unstableTimeoutMs = 10 * 1000L
    private val offlineTimeoutMs = 30 * 1000L
    private var isOfflineCheckerRunning = false

    private var lastSentRiskCommand: String? = null
    private var lastAlertRiskLevel: RiskLevel? = null

    private lateinit var alertManager: AlertManager

    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    private val deviceInfoList = ArrayList<String>()
    private val foundDeviceList = ArrayList<BluetoothDevice>()
    private lateinit var deviceAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initManagers()
        initDeviceList()
        initDefaultUi()
        initBluetooth()
        initListeners()
    }

    private fun initViews() {
        txtBleState = findViewById(R.id.txtBleState)
        txtConnectedDevice = findViewById(R.id.txtConnectedDevice)
        txtData = findViewById(R.id.txtData)
        txtRiskState = findViewById(R.id.txtRiskState)
        btnScan = findViewById(R.id.btnScan)
        listBle = findViewById(R.id.listBle)
    }

    private fun initManagers() {
        alertManager = AlertManager(this)
    }

    private fun initDeviceList() {
        deviceAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            deviceInfoList
        )
        listBle.adapter = deviceAdapter
    }

    private fun initDefaultUi() {
        txtRiskState.text = "상태 : 대기중"
        txtRiskState.setBackgroundColor(Color.parseColor("#777777"))
        txtConnectedDevice.text = "연결 장치 : 없음"
        txtData.text = "데이터 없음"
    }

    private fun initBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        txtBleState.text = if (bluetoothAdapter == null) {
            "BLE 사용 불가"
        } else {
            "BLE 준비 완료"
        }
    }

    private fun initListeners() {
        btnScan.setOnClickListener {
            if (!BlePermissionHelper.hasBlePermission(this)) {
                BlePermissionHelper.requestBlePermission(this)
                return@setOnClickListener
            }

            if (bluetoothAdapter == null) {
                Toast.makeText(this, "블루투스를 지원하지 않는 기기입니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (bluetoothAdapter?.isEnabled == false) {
                Toast.makeText(this, "블루투스를 먼저 켜주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            startBleScan()
        }

        listBle.setOnItemClickListener { _, _, position, _ ->
            if (position < 0 || position >= foundDeviceList.size) return@setOnItemClickListener
            connectToDevice(foundDeviceList[position])
        }
    }

    private fun startBleScan() {
        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BLE scanner is null")
            Toast.makeText(this, "BLE 스캐너를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (isScanning) {
            Log.w(TAG, "Scan already running")
            Toast.makeText(this, "이미 스캔 중입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!BlePermissionHelper.hasScanPermission(this)) {
            Log.e(TAG, "Missing BLUETOOTH_SCAN permission")
            Toast.makeText(this, "BLE 스캔 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        deviceInfoList.clear()
        foundDeviceList.clear()
        deviceAdapter.notifyDataSetChanged()

        txtBleState.text = "BLE 스캔 중..."
        txtConnectedDevice.text = "연결 장치 : 없음"

        isScanning = true

        Log.d(TAG, "BLE scan started")

        handler.postDelayed({
            if (isScanning) {
                Log.d(TAG, "BLE scan timeout reached")
                stopBleScan()
            }
        }, BleConstants.SCAN_PERIOD)

        bluetoothLeScanner?.startScan(scanCallback)
    }

    private fun stopBleScan() {
        if (
            bluetoothLeScanner != null &&
            isScanning &&
            BlePermissionHelper.hasScanPermission(this)
        ) {
            try {
                bluetoothLeScanner?.stopScan(scanCallback)
                Log.d(TAG, "BLE scan stopped")
            } catch (e: SecurityException) {
                Log.e(TAG, "stopScan failed by permission", e)
            }
        }

        isScanning = false
        txtBleState.text = "스캔 종료"
    }

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val device = result.device ?: return
            val scanRecord: ScanRecord = result.scanRecord ?: return

            if (!hasTargetServiceUuid(scanRecord)) {
                Log.d(TAG, "Ignored device without target UUID")
                return
            }

            if (!BlePermissionHelper.hasConnectPermission(this@MainActivity)) {
                Log.e(TAG, "Missing BLUETOOTH_CONNECT permission while reading device info")
                return
            }

            val deviceName = try {
                device.name?.takeIf { it.isNotBlank() } ?: "이름 없는 기기"
            } catch (e: SecurityException) {
                Log.e(TAG, "Cannot read device name", e)
                "이름 없는 기기"
            }

            val deviceAddress = try {
                device.address
            } catch (e: SecurityException) {
                Log.e(TAG, "Cannot read device address", e)
                return
            }

            if (foundDeviceList.any { it.address == deviceAddress }) {
                Log.d(TAG, "Duplicate device ignored: $deviceName / $deviceAddress")
                return
            }

            val deviceInfo = "이름 : $deviceName\n주소 : $deviceAddress"

            foundDeviceList.add(device)
            deviceInfoList.add(deviceInfo)
            deviceAdapter.notifyDataSetChanged()

            Log.d(TAG, "Target BLE device found: $deviceName / $deviceAddress")
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)

            isScanning = false

            runOnUiThread {
                txtBleState.text = "스캔 실패: $errorCode"
            }

            Log.e(TAG, "BLE scan failed. errorCode=$errorCode")
        }
    }

    private fun hasTargetServiceUuid(scanRecord: ScanRecord): Boolean {
        val serviceUuids = scanRecord.serviceUuids ?: return false
        return serviceUuids.any { it.uuid == BleConstants.TARGET_SERVICE_UUID }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        stopBleScan()

        if (!BlePermissionHelper.hasConnectPermission(this)) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission. Cannot connect.")
            Toast.makeText(this, "BLE 연결 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val deviceAddress = try {
            device.address
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot read device address", e)
            Toast.makeText(this, "BLE 장치 정보를 읽을 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        resetBleStateFlags()

        lastConnectedDevice = device
        connectedDeviceAddress = deviceAddress

        txtBleState.text = "연결 시도 중..."
        txtConnectedDevice.text = "연결 장치 : $deviceAddress"

        Log.d(TAG, "Connecting to device: $deviceAddress")

        try {
            bluetoothGatt?.close()
            bluetoothGatt = null
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "connectGatt failed by permission", e)
            Toast.makeText(this, "BLE 연결 권한 오류", Toast.LENGTH_SHORT).show()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            super.onConnectionStateChange(gatt, status, newState)

            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isBleConnected = true
                    isServiceDiscovered = false
                    isNotifyReady = false

                    stopReconnect()
                    startOfflineChecker()

                    runOnUiThread {
                        txtBleState.text = "BLE 연결 성공"
                        txtConnectedDevice.text =
                            "연결 장치 : ${connectedDeviceAddress ?: gatt.device.address}"

                        Toast.makeText(
                            this@MainActivity,
                            "장치 연결 성공",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    Log.d(TAG, "BLE connected. Starting service discovery.")

                    if (!BlePermissionHelper.hasConnectPermission(this@MainActivity)) {
                        Log.e(TAG, "Missing BLUETOOTH_CONNECT permission. Cannot discover services.")
                        return
                    }

                    val discoverStarted = gatt.discoverServices()
                    Log.d(TAG, "discoverServices started=$discoverStarted")
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.w(TAG, "BLE disconnected. status=$status")

                    isBleConnected = false
                    isServiceDiscovered = false
                    isNotifyReady = false

                    lastSentRiskCommand = null
                    lastAlertRiskLevel = null

                    runOnUiThread {
                        txtBleState.text = "BLE 연결 끊김 - 재연결 시도 중"
                        txtConnectedDevice.text =
                            "마지막 장치 : ${connectedDeviceAddress ?: "없음"}"
                    }

                    startReconnect()
                }
            }
        }

        override fun onServicesDiscovered(
            gatt: BluetoothGatt,
            status: Int
        ) {
            super.onServicesDiscovered(gatt, status)

            Log.d(TAG, "onServicesDiscovered: status=$status")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                isServiceDiscovered = false
                isNotifyReady = false

                runOnUiThread {
                    txtBleState.text = "서비스 탐색 실패"
                }

                Log.e(TAG, "Service discovery failed. status=$status")
                return
            }

            val targetService = gatt.getService(BleConstants.TARGET_SERVICE_UUID)

            if (targetService == null) {
                isServiceDiscovered = false
                isNotifyReady = false

                runOnUiThread {
                    txtBleState.text = "서비스 없음"
                }

                Log.e(TAG, "Target service not found: ${BleConstants.TARGET_SERVICE_UUID}")
                return
            }

            isServiceDiscovered = true

            Log.d(TAG, "Target service found: ${BleConstants.TARGET_SERVICE_UUID}")

            val dataCharacteristic =
                targetService.getCharacteristic(BleConstants.DATA_CHARACTERISTIC_UUID)

            if (dataCharacteristic == null) {
                isNotifyReady = false

                runOnUiThread {
                    txtBleState.text = "데이터 Characteristic 없음"
                }

                Log.e(TAG, "Data characteristic not found: ${BleConstants.DATA_CHARACTERISTIC_UUID}")
                return
            }

            Log.d(TAG, "Data characteristic found: ${BleConstants.DATA_CHARACTERISTIC_UUID}")

            if (!BlePermissionHelper.hasConnectPermission(this@MainActivity)) {
                isNotifyReady = false
                Log.e(TAG, "Missing BLUETOOTH_CONNECT permission. Cannot enable notify.")
                return
            }

            val notifySuccess = gatt.setCharacteristicNotification(dataCharacteristic, true)
            Log.d(TAG, "setCharacteristicNotification result=$notifySuccess")

            val descriptor =
                dataCharacteristic.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)

            if (descriptor == null) {
                isNotifyReady = false

                runOnUiThread {
                    txtBleState.text = "CCCD 없음"
                }

                Log.e(TAG, "CCCD descriptor not found: ${BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID}")
                return
            }

            val descriptorWriteStarted: Boolean =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val result = gatt.writeDescriptor(
                        descriptor,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    )

                    Log.d(TAG, "writeDescriptor Android 13+ result=$result")

                    result == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                    @Suppress("DEPRECATION")
                    val result = gatt.writeDescriptor(descriptor)

                    Log.d(TAG, "writeDescriptor legacy result=$result")

                    result
                }

            isNotifyReady = notifySuccess && descriptorWriteStarted

            runOnUiThread {
                txtBleState.text = if (isNotifyReady) {
                    "데이터 수신 준비 완료"
                } else {
                    "Notify 설정 실패"
                }

                if (isNotifyReady) {
                    Toast.makeText(
                        this@MainActivity,
                        "서비스 탐색 완료",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            Log.d(
                TAG,
                "BLE ready states: connected=$isBleConnected, serviceDiscovered=$isServiceDiscovered, notifyReady=$isNotifyReady"
            )
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)

            if (characteristic.uuid == BleConstants.DATA_CHARACTERISTIC_UUID) {
                val data = characteristic.value ?: return
                val received = String(data, StandardCharsets.UTF_8)

                lastDataReceivedTime = System.currentTimeMillis()

                Log.d(TAG, "Notify received: $received")

                handleReceivedData(received)
            }
        }
    }

    private fun handleReceivedData(rawData: String) {
        if (!isBleConnected || !isNotifyReady) {
            Log.w(
                TAG,
                "Received data while BLE is not fully ready. connected=$isBleConnected, notifyReady=$isNotifyReady"
            )
        }

        val cleanedRawData = rawData.trim()

        Log.d(TAG, "Raw sensor data: $cleanedRawData")

        if (cleanedRawData.isEmpty()) {
            Log.w(TAG, "Received empty sensor data")
            showParseError(rawData)
            return
        }

        val sensorData = SensorDataParser.parse(cleanedRawData)

        if (sensorData == null) {
            Log.w(TAG, "Sensor data parse failed: $cleanedRawData")
            showParseError(cleanedRawData)
            return
        }

        Log.d(TAG, "Parsed sensor data: $sensorData")

        val riskLevel = HeatstrokeAnalyzer.analyze(sensorData)
        val command = RiskCommandMapper.toCommand(riskLevel)

        Log.d(TAG, "Risk calculated: ${riskLevel.label}, command=$command")

        updateSensorUI(sensorData, riskLevel)
        updateRiskUI(riskLevel)

        sendRiskCommandToEsp32(command)

        if (riskLevel != lastAlertRiskLevel) {
            Log.d(
                TAG,
                "Risk level changed for alert: previous=${lastAlertRiskLevel?.label}, current=${riskLevel.label}"
            )

            alertManager.handleRisk(riskLevel)
            lastAlertRiskLevel = riskLevel
        } else {
            Log.d(TAG, "Alert skipped. Same risk level: ${riskLevel.label}")
        }
    }

    private fun updateSensorUI(
        sensorData: SensorData,
        riskLevel: RiskLevel
    ) {
        val display = """
            ID : ${sensorData.id}
            체온 : ${sensorData.temp}
            심박수 : ${sensorData.hr}
            환경온도 : ${sensorData.env}
            습도 : ${sensorData.hum}
            자세 : ${sensorData.posture}
            위험도 : ${riskLevel.label}
        """.trimIndent()

        runOnUiThread {
            txtData.text = display
        }
    }

    private fun updateRiskUI(riskLevel: RiskLevel) {
        runOnUiThread {
            txtRiskState.text = "상태 : ${riskLevel.label}"

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

    private fun showParseError(rawData: String) {
        Log.w(TAG, "showParseError called. rawData=$rawData")

        runOnUiThread {
            txtData.text = """
                데이터 파싱 오류
                
                수신 원본:
                $rawData
            """.trimIndent()

            txtRiskState.text = "상태 : 오류"
            txtRiskState.setBackgroundColor(Color.parseColor("#777777"))
        }
    }

    private fun sendRiskCommandToEsp32(command: String) {
        if (lastSentRiskCommand == command) {
            Log.d(TAG, "BLE write skipped. Duplicate command=$command")
            return
        }

        if (!isBleConnected || !isServiceDiscovered) {
            Log.w(
                TAG,
                "BLE write skipped. BLE not ready. connected=$isBleConnected, serviceDiscovered=$isServiceDiscovered"
            )
            return
        }

        val gatt = bluetoothGatt

        if (gatt == null) {
            Log.e(TAG, "BLE write failed. bluetoothGatt is null")
            return
        }

        if (!BlePermissionHelper.hasConnectPermission(this)) {
            Log.e(TAG, "BLE write failed. Missing BLUETOOTH_CONNECT permission")
            return
        }

        val targetService = gatt.getService(BleConstants.TARGET_SERVICE_UUID)

        if (targetService == null) {
            Log.e(TAG, "BLE write failed. Target service is null")
            return
        }

        val controlCharacteristic =
            targetService.getCharacteristic(BleConstants.CONTROL_CHARACTERISTIC_UUID)

        if (controlCharacteristic == null) {
            Log.e(TAG, "BLE write failed. Control characteristic is null")
            return
        }

        val sendData = command.toByteArray(StandardCharsets.UTF_8)

        val writeStarted: Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = gatt.writeCharacteristic(
                    controlCharacteristic,
                    sendData,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )

                Log.d(TAG, "writeCharacteristic Android 13+ result=$result, command=$command")

                result == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                controlCharacteristic.value = sendData

                @Suppress("DEPRECATION")
                val result = gatt.writeCharacteristic(controlCharacteristic)

                Log.d(TAG, "writeCharacteristic legacy result=$result, command=$command")

                result
            }

        if (writeStarted) {
            lastSentRiskCommand = command
            Log.d(TAG, "BLE write started successfully. lastSentRiskCommand=$command")
        } else {
            Log.e(TAG, "BLE write failed to start. command=$command")
        }
    }

    private fun startReconnect() {
        val device = lastConnectedDevice

        if (device == null) {
            Log.w(TAG, "Reconnect skipped. lastConnectedDevice is null")
            return
        }

        if (isReconnecting) {
            Log.d(TAG, "Reconnect already running")
            return
        }

        isReconnecting = true
        reconnectStartTime = System.currentTimeMillis()

        Log.w(TAG, "Reconnect started")

        handler.post(reconnectRunnable)
    }

    private fun stopReconnect() {
        if (!isReconnecting) return

        isReconnecting = false
        handler.removeCallbacks(reconnectRunnable)

        Log.d(TAG, "Reconnect stopped")
    }

    private val reconnectRunnable = object : Runnable {
        override fun run() {
            if (!isReconnecting) return

            val elapsed = System.currentTimeMillis() - reconnectStartTime

            if (elapsed >= reconnectMaxDurationMs) {
                Log.e(TAG, "Reconnect failed. Max duration reached.")

                isReconnecting = false

                runOnUiThread {
                    txtBleState.text = "재연결 실패 - 세션 종료"
                    txtConnectedDevice.text = "연결 장치 : 없음"
                }

                resetBleStateFlags()
                stopOfflineChecker()
                return
            }

            val device = lastConnectedDevice

            if (device == null) {
                Log.e(TAG, "Reconnect failed. Device is null.")
                isReconnecting = false
                return
            }

            if (!BlePermissionHelper.hasConnectPermission(this@MainActivity)) {
                Log.e(TAG, "Reconnect failed. Missing BLUETOOTH_CONNECT permission.")
                isReconnecting = false
                return
            }

            Log.d(TAG, "Trying reconnect... elapsed=${elapsed / 1000}s")

            bluetoothGatt?.close()
            bluetoothGatt = null

            bluetoothGatt = device.connectGatt(this@MainActivity, false, gattCallback)

            handler.postDelayed(this, reconnectIntervalMs)
        }
    }

    private fun startOfflineChecker() {
        if (isOfflineCheckerRunning) {
            return
        }

        isOfflineCheckerRunning = true
        lastDataReceivedTime = System.currentTimeMillis()

        Log.d(TAG, "Offline checker started")

        handler.post(offlineCheckRunnable)
    }

    private fun stopOfflineChecker() {
        if (!isOfflineCheckerRunning) {
            return
        }

        isOfflineCheckerRunning = false
        handler.removeCallbacks(offlineCheckRunnable)

        Log.d(TAG, "Offline checker stopped")
    }

    private val offlineCheckRunnable = object : Runnable {
        override fun run() {
            if (!isOfflineCheckerRunning) return

            checkOfflineState()

            handler.postDelayed(this, 1000L)
        }
    }

    private fun checkOfflineState() {
        if (!isBleConnected) {
            return
        }

        val now = System.currentTimeMillis()
        val elapsed = now - lastDataReceivedTime

        when {
            elapsed >= offlineTimeoutMs -> {
                runOnUiThread {
                    txtBleState.text = "데이터 오프라인"
                }

                Log.w(TAG, "Data offline. elapsed=${elapsed / 1000}s")
            }

            elapsed >= unstableTimeoutMs -> {
                runOnUiThread {
                    txtBleState.text = "데이터 수신 불안정"
                }

                Log.w(TAG, "Data unstable. elapsed=${elapsed / 1000}s")
            }

            else -> {
                if (isNotifyReady) {
                    runOnUiThread {
                        txtBleState.text = "데이터 수신 중"
                    }
                }
            }
        }
    }

    private fun resetBleStateFlags() {
        isBleConnected = false
        isServiceDiscovered = false
        isNotifyReady = false
        connectedDeviceAddress = null

        lastSentRiskCommand = null
        lastAlertRiskLevel = null

        lastDataReceivedTime = 0L

        Log.d(TAG, "BLE state flags reset")
    }

    override fun onDestroy() {
        super.onDestroy()

        Log.d(TAG, "onDestroy called. Closing BLE resources.")

        stopBleScan()
        stopReconnect()
        stopOfflineChecker()

        bluetoothGatt?.let { gatt ->
            if (BlePermissionHelper.hasConnectPermission(this)) {
                gatt.close()
                Log.d(TAG, "BluetoothGatt closed")
            } else {
                Log.w(TAG, "BluetoothGatt close skipped. Missing permission.")
            }
        }

        bluetoothGatt = null
        resetBleStateFlags()
        lastConnectedDevice = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == BleConstants.REQUEST_BLE_PERMISSION) {
            val isGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            val message = if (isGranted) {
                "BLE 권한이 허용되었습니다."
            } else {
                "BLE 권한이 필요합니다."
            }

            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }
}