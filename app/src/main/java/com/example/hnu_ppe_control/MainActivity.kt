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

class MainActivity : AppCompatActivity() {

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
    // 같은 명령을 반복 전송하지 않기 위해 사용
    private var lastSentRiskCommand: String? = null

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
            Toast.makeText(this, "BLE 스캐너를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (isScanning) {
            Toast.makeText(this, "이미 스캔 중입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (!BlePermissionHelper.hasScanPermission(this)) {
            Toast.makeText(this, "BLE 스캔 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        deviceInfoList.clear()
        foundDeviceList.clear()
        deviceAdapter.notifyDataSetChanged()

        txtBleState.text = "BLE 스캔 중..."
        txtConnectedDevice.text = "연결 장치 : 없음"

        isScanning = true

        handler.postDelayed({
            if (isScanning) stopBleScan()
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
        }

        isScanning = false
        txtBleState.text = "스캔 종료"
    }

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val device = result.device ?: return
            val scanRecord: ScanRecord = result.scanRecord ?: return

            if (!hasTargetServiceUuid(scanRecord)) return
            if (!BlePermissionHelper.hasConnectPermission(this@MainActivity)) return

            val deviceName = device.name?.takeIf { it.isNotBlank() } ?: "이름 없는 기기"

            if (foundDeviceList.any { it.address == device.address }) return

            val deviceInfo = "이름 : $deviceName\n주소 : ${device.address}"

            foundDeviceList.add(device)
            deviceInfoList.add(deviceInfo)
            deviceAdapter.notifyDataSetChanged()
        }
    }

    private fun hasTargetServiceUuid(scanRecord: ScanRecord): Boolean {
        val serviceUuids = scanRecord.serviceUuids ?: return false
        return serviceUuids.any { it.uuid == BleConstants.TARGET_SERVICE_UUID }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        stopBleScan()

        if (!BlePermissionHelper.hasConnectPermission(this)) {
            Toast.makeText(this, "BLE 연결 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        txtBleState.text = "연결 시도 중..."
        txtConnectedDevice.text = "연결 장치 : ${device.address}"

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

            runOnUiThread {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        txtBleState.text = "BLE 연결 성공"
                        Toast.makeText(this@MainActivity, "장치 연결 성공", Toast.LENGTH_SHORT).show()
                    }

                    BluetoothProfile.STATE_DISCONNECTED -> {
                        txtBleState.text = "BLE 연결 해제"
                        txtConnectedDevice.text = "연결 장치 : 없음"

                        // 연결이 끊기면 마지막 전송 명령 초기화
                        // 재연결 후 같은 위험 단계라도 다시 ESP32에 명령을 보낼 수 있게 함
                        lastSentRiskCommand = null

                        Toast.makeText(this@MainActivity, "장치 연결 해제", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (!BlePermissionHelper.hasConnectPermission(this@MainActivity)) return
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(
            gatt: BluetoothGatt,
            status: Int
        ) {
            super.onServicesDiscovered(gatt, status)

            val targetService = gatt.getService(BleConstants.TARGET_SERVICE_UUID)

            if (targetService == null) {
                runOnUiThread { txtBleState.text = "서비스 없음" }
                return
            }

            val dataCharacteristic =
                targetService.getCharacteristic(BleConstants.DATA_CHARACTERISTIC_UUID)

            if (dataCharacteristic == null) {
                runOnUiThread { txtBleState.text = "데이터 Characteristic 없음" }
                return
            }

            if (!BlePermissionHelper.hasConnectPermission(this@MainActivity)) return

            val notifySuccess = gatt.setCharacteristicNotification(dataCharacteristic, true)

            val descriptor =
                dataCharacteristic.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)

            descriptor?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(
                        it,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    )
                } else {
                    @Suppress("DEPRECATION")
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE

                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(it)
                }
            }

            runOnUiThread {
                txtBleState.text = if (notifySuccess) {
                    "데이터 수신 준비 완료"
                } else {
                    "Notify 설정 실패"
                }

                if (notifySuccess) {
                    Toast.makeText(this@MainActivity, "서비스 탐색 완료", Toast.LENGTH_SHORT).show()
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
                handleReceivedData(received)
            }
        }
    }

    private fun handleReceivedData(rawData: String) {
        val sensorData = SensorDataParser.parse(rawData)

        if (sensorData == null) {
            showParseError(rawData)
            return
        }

        val riskLevel = HeatstrokeAnalyzer.analyze(sensorData)
        val command = RiskCommandMapper.toCommand(riskLevel)

        updateSensorUI(sensorData, riskLevel)
        updateRiskUI(riskLevel)

        sendRiskCommandToEsp32(command)
        alertManager.handleRisk(riskLevel)
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
        runOnUiThread {
            txtData.text = "데이터 파싱 오류\n$rawData"
            txtRiskState.text = "상태 : 오류"
            txtRiskState.setBackgroundColor(Color.parseColor("#777777"))
        }
    }

    private fun sendRiskCommandToEsp32(command: String) {
        // 같은 명령이면 ESP32에 반복 전송하지 않음
        if (lastSentRiskCommand == command) {
            return
        }

        val gatt = bluetoothGatt ?: return

        if (!BlePermissionHelper.hasConnectPermission(this)) {
            return
        }

        val targetService = gatt.getService(BleConstants.TARGET_SERVICE_UUID) ?: return

        val controlCharacteristic =
            targetService.getCharacteristic(BleConstants.CONTROL_CHARACTERISTIC_UUID) ?: return

        val sendData = command.toByteArray(StandardCharsets.UTF_8)

        val writeResult: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val result = gatt.writeCharacteristic(
                controlCharacteristic,
                sendData,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )

            result == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            controlCharacteristic.value = sendData

            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(controlCharacteristic)
        }

        // 실제 write 요청이 성공적으로 시작된 경우에만 마지막 명령 갱신
        if (writeResult) {
            lastSentRiskCommand = command
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        stopBleScan()

        bluetoothGatt?.let { gatt ->
            if (BlePermissionHelper.hasConnectPermission(this)) {
                gatt.close()
            }
        }

        bluetoothGatt = null
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