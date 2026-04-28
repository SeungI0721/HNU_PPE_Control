package com.example.hnu_ppe_control

import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothStatusCodes
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.widget.*
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
import android.util.Log

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

    // 마지막으로 ESP32에 전송한 위험도 명령
    private var lastSentRiskCommand: String? = null

    // 마지막으로 Alert를 띄운 위험 단계
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
            bluetoothLeScanner?.stopScan(scanCallback)
            Log.d(TAG, "BLE scan stopped")
        }

        isScanning = false
        txtBleState.text = "스캔 종료"
    }

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val device = result.device ?: return
            val scanRecord: ScanRecord = result.scanRecord ?: return

            val hasTargetUuid = hasTargetServiceUuid(scanRecord)

            if (!hasTargetUuid) {
                Log.d(TAG, "Ignored device without target UUID")
                return
            }

            if (!BlePermissionHelper.hasConnectPermission(this@MainActivity)) {
                Log.e(TAG, "Missing BLUETOOTH_CONNECT permission while reading scan result")
                return
            }

            val deviceName = device.name?.takeIf { it.isNotBlank() } ?: "이름 없는 기기"

            if (foundDeviceList.any { it.address == device.address }) {
                Log.d(TAG, "Duplicate device ignored: $deviceName / ${device.address}")
                return
            }

            val deviceInfo = "이름 : $deviceName\n주소 : ${device.address}"

            foundDeviceList.add(device)
            deviceInfoList.add(deviceInfo)
            deviceAdapter.notifyDataSetChanged()

            Log.d(TAG, "Target BLE device found: $deviceName / ${device.address}")
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

        txtBleState.text = "연결 시도 중..."
        txtConnectedDevice.text = "연결 장치 : ${device.address}"

        Log.d(TAG, "Connecting to device: ${device.address}")

        bluetoothGatt?.close()
        bluetoothGatt = null

        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            super.onConnectionStateChange(gatt, status, newState)

            Log.d(
                TAG,
                "onConnectionStateChange: status=$status, newState=$newState"
            )

            runOnUiThread {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        txtBleState.text = "BLE 연결 성공"
                        Toast.makeText(
                            this@MainActivity,
                            "장치 연결 성공",
                            Toast.LENGTH_SHORT
                        ).show()

                        Log.d(TAG, "BLE connected. Starting service discovery.")
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        txtBleState.text = "BLE 연결 해제"
                        txtConnectedDevice.text = "연결 장치 : 없음"

                        lastSentRiskCommand = null
                        lastAlertRiskLevel = null

                        Toast.makeText(
                            this@MainActivity,
                            "장치 연결 해제",
                            Toast.LENGTH_SHORT
                        ).show()

                        Log.w(TAG, "BLE disconnected. Duplicate prevention states reset.")
                    }
                }
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (!BlePermissionHelper.hasConnectPermission(this@MainActivity)) {
                    Log.e(TAG, "Missing BLUETOOTH_CONNECT permission. Cannot discover services.")
                    return
                }

                val discoverStarted = gatt.discoverServices()
                Log.d(TAG, "discoverServices started=$discoverStarted")
            }
        }

        override fun onServicesDiscovered(
            gatt: BluetoothGatt,
            status: Int
        ) {
            super.onServicesDiscovered(gatt, status)

            Log.d(TAG, "onServicesDiscovered: status=$status")

            if (status != BluetoothGatt.GATT_SUCCESS) {
                runOnUiThread {
                    txtBleState.text = "서비스 탐색 실패"
                }

                Log.e(TAG, "Service discovery failed. status=$status")
                return
            }

            val targetService = gatt.getService(BleConstants.TARGET_SERVICE_UUID)

            if (targetService == null) {
                runOnUiThread { txtBleState.text = "서비스 없음" }
                Log.e(TAG, "Target service not found: ${BleConstants.TARGET_SERVICE_UUID}")
                return
            }

            Log.d(TAG, "Target service found: ${BleConstants.TARGET_SERVICE_UUID}")

            val dataCharacteristic =
                targetService.getCharacteristic(BleConstants.DATA_CHARACTERISTIC_UUID)

            if (dataCharacteristic == null) {
                runOnUiThread { txtBleState.text = "데이터 Characteristic 없음" }
                Log.e(TAG, "Data characteristic not found: ${BleConstants.DATA_CHARACTERISTIC_UUID}")
                return
            }

            Log.d(TAG, "Data characteristic found: ${BleConstants.DATA_CHARACTERISTIC_UUID}")

            if (!BlePermissionHelper.hasConnectPermission(this@MainActivity)) {
                Log.e(TAG, "Missing BLUETOOTH_CONNECT permission. Cannot enable notify.")
                return
            }

            val notifySuccess = gatt.setCharacteristicNotification(dataCharacteristic, true)
            Log.d(TAG, "setCharacteristicNotification result=$notifySuccess")

            val descriptor =
                dataCharacteristic.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)

            if (descriptor == null) {
                runOnUiThread {
                    txtBleState.text = "CCCD 없음"
                }

                Log.e(TAG, "CCCD descriptor not found: ${BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID}")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = gatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                )

                Log.d(TAG, "writeDescriptor Android 13+ result=$result")
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                @Suppress("DEPRECATION")
                val result = gatt.writeDescriptor(descriptor)

                Log.d(TAG, "writeDescriptor legacy result=$result")
            }

            runOnUiThread {
                txtBleState.text = if (notifySuccess) {
                    "데이터 수신 준비 완료"
                } else {
                    "Notify 설정 실패"
                }

                if (notifySuccess) {
                    Toast.makeText(
                        this@MainActivity,
                        "서비스 탐색 완료",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
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

                Log.d(TAG, "Notify received: $received")

                handleReceivedData(received)
            }
        }
    }

    private fun handleReceivedData(rawData: String) {
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

        val writeStarted: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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

    override fun onDestroy() {
        super.onDestroy()

        Log.d(TAG, "onDestroy called. Closing BLE resources.")

        stopBleScan()

        bluetoothGatt?.let { gatt ->
            if (BlePermissionHelper.hasConnectPermission(this)) {
                gatt.close()
                Log.d(TAG, "BluetoothGatt closed")
            } else {
                Log.w(TAG, "BluetoothGatt close skipped. Missing permission.")
            }
        }

        bluetoothGatt = null
        lastSentRiskCommand = null
        lastAlertRiskLevel = null
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