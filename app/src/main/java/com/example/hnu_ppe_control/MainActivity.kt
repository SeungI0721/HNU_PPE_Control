package com.example.hnu_ppe_control

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.nio.charset.StandardCharsets
import java.util.UUID

class MainActivity : AppCompatActivity() {

    // ===== UI =====
    private lateinit var txtBleState: TextView
    private lateinit var txtConnectedDevice: TextView
    private lateinit var txtData: TextView
    private lateinit var txtRiskState: TextView
    private lateinit var btnScan: Button
    private lateinit var listBle: ListView

    // ===== BLE =====
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null

    // ===== 진동 / 경고 =====
    private var vibrator: Vibrator? = null
    private var isWarningShowing = false

    // ===== 스캔 제어 =====
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false

    companion object {
        private const val SCAN_PERIOD = 10000L
        private const val REQUEST_BLE_PERMISSION = 1001

        // 우리 장치 식별용 서비스 UUID
        private val TARGET_SERVICE_UUID: UUID =
            UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB")

        // 데이터 수신용 Characteristic UUID
        private val DATA_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB")

        // Notify 활성화용 CCCD UUID
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        // 앱 → ESP32 제어 명령 전송용 Characteristic UUID
        private val CONTROL_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB")
    }

    // ===== 검색된 장치 리스트 =====
    private val deviceInfoList = ArrayList<String>()
    private val foundDeviceList = ArrayList<BluetoothDevice>()
    private lateinit var deviceAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // UI 연결
        txtBleState = findViewById(R.id.txtBleState)
        txtConnectedDevice = findViewById(R.id.txtConnectedDevice)
        txtData = findViewById(R.id.txtData)
        txtRiskState = findViewById(R.id.txtRiskState)
        btnScan = findViewById(R.id.btnScan)
        listBle = findViewById(R.id.listBle)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // 장치 리스트 초기화
        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceInfoList)
        listBle.adapter = deviceAdapter

        // 초기 상태 표시
        txtRiskState.text = "상태 : 대기중"
        txtRiskState.setBackgroundColor(Color.parseColor("#777777"))
        txtConnectedDevice.text = "연결 장치 : 없음"
        txtData.text = "데이터 없음"

        // 블루투스 초기화
        initBluetooth()

        // 스캔 버튼 클릭
        btnScan.setOnClickListener {
            if (!checkBlePermission()) {
                requestBlePermission()
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

        // 목록에서 장치 선택 시 연결
        listBle.setOnItemClickListener { _, _, position, _ ->
            if (position < 0 || position >= foundDeviceList.size) return@setOnItemClickListener
            val selectedDevice = foundDeviceList[position]
            connectToDevice(selectedDevice)
        }
    }

    // 블루투스 초기화
    private fun initBluetooth() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        txtBleState.text = if (bluetoothAdapter == null) "BLE 사용 불가" else "BLE 준비 완료"
    }

    // BLE 권한 확인
    private fun checkBlePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    // BLE 권한 요청
    private fun requestBlePermission() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        ActivityCompat.requestPermissions(this, permissions, REQUEST_BLE_PERMISSION)
    }

    // BLE 스캔 시작
    private fun startBleScan() {
        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "BLE 스캐너를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (isScanning) {
            Toast.makeText(this, "이미 스캔 중입니다.", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "BLE 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        // 이전 검색 결과 초기화
        deviceInfoList.clear()
        foundDeviceList.clear()
        deviceAdapter.notifyDataSetChanged()

        txtBleState.text = "BLE 스캔 중..."
        txtConnectedDevice.text = "연결 장치 : 없음"
        isScanning = true

        // 일정 시간 후 자동 종료
        handler.postDelayed({
            if (isScanning) stopBleScan()
        }, SCAN_PERIOD)

        bluetoothLeScanner?.startScan(scanCallback)
    }

    // BLE 스캔 종료
    private fun stopBleScan() {
        if (bluetoothLeScanner != null && isScanning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }
            bluetoothLeScanner?.stopScan(scanCallback)
        }
        isScanning = false
        txtBleState.text = "스캔 종료"
    }

    // 스캔 결과 처리
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val device = result.device ?: return
            val scanRecord: ScanRecord = result.scanRecord ?: return

            if (!hasTargetServiceUuid(scanRecord)) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }

            val deviceName = device.name?.takeIf { it.isNotBlank() } ?: "이름 없는 기기"

            // MAC 주소 기준 중복 제거
            if (foundDeviceList.any { it.address == device.address }) return

            val deviceInfo = "이름 : $deviceName\n주소 : ${device.address}"
            foundDeviceList.add(device)
            deviceInfoList.add(deviceInfo)
            deviceAdapter.notifyDataSetChanged()
        }
    }

    // 광고 패킷에서 서비스 UUID 확인
    private fun hasTargetServiceUuid(scanRecord: ScanRecord): Boolean {
        val serviceUuids: List<ParcelUuid> = scanRecord.serviceUuids ?: return false
        return serviceUuids.any { it.uuid == TARGET_SERVICE_UUID }
    }

    // 선택한 장치와 연결
    private fun connectToDevice(device: BluetoothDevice) {
        stopBleScan()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "BLE 연결 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                return
            }
        }

        txtBleState.text = "연결 시도 중..."
        txtConnectedDevice.text = "연결 장치 : ${device.address}"

        bluetoothGatt?.close()
        bluetoothGatt = null

        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    // GATT 콜백
    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
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
                        Toast.makeText(this@MainActivity, "장치 연결 해제", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        return
                    }
                }
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)

            val targetService = gatt.getService(TARGET_SERVICE_UUID) ?: run {
                runOnUiThread { txtBleState.text = "서비스 없음" }
                return
            }

            val characteristic = targetService.getCharacteristic(DATA_CHARACTERISTIC_UUID) ?: run {
                runOnUiThread { txtBleState.text = "Characteristic 없음" }
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }

            val notifySuccess = gatt.setCharacteristicNotification(characteristic, true)

            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            descriptor?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(it, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    it.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(it)
                }
            }

            runOnUiThread {
                txtBleState.text = if (notifySuccess) "데이터 수신 준비 완료" else "Notify 설정 실패"
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

            if (characteristic.uuid == DATA_CHARACTERISTIC_UUID) {
                val data = characteristic.value ?: return
                val received = String(data, StandardCharsets.UTF_8)
                parseAndDisplayData(received)
            }
        }
    }

    // 위험도 계산
    private fun calculateRiskLevel(temp: Double, hr: Int, env: Double, hum: Int, posture: String): String {
        if (posture.equals("FALL", ignoreCase = true) || posture.equals("EMERGENCY", ignoreCase = true)) {
            return "응급"
        }

        var score = 0

        // 체온 점수
        score += when {
            temp >= 38.0 -> 2
            temp >= 37.5 -> 1
            else -> 0
        }

        // 심박수 점수
        score += when {
            hr >= 120 -> 2
            hr >= 100 -> 1
            else -> 0
        }

        // 환경온도 점수
        score += when {
            env >= 35.0 -> 3
            env >= 33.0 -> 2
            env >= 31.0 -> 1
            else -> 0
        }

        // 습도 점수
        score += when {
            hum >= 80 -> 2
            hum >= 70 -> 1
            else -> 0
        }

        // 자세 상태 점수
        if (posture.equals("WARNING", ignoreCase = true) || posture.equals("UNSTABLE", ignoreCase = true)) {
            score += 2
        }

        return when {
            score >= 6 -> "응급"
            score >= 4 -> "위험"
            score >= 2 -> "주의"
            else -> "정상"
        }
    }

    // 앱 내부 위험 단계를 ESP32 전송용 코드로 변환
    private fun convertRiskToCommand(riskLevel: String): String {
        return when (riskLevel) {
            "정상" -> "RISK:SAFE"
            "주의" -> "RISK:CAUTION"
            "위험" -> "RISK:DANGER"
            "응급" -> "RISK:EMERGENCY"
            else -> "RISK:SAFE"
        }
    }

    // 상태 색상 UI 갱신
    private fun updateRiskUI(riskLevel: String) {
        runOnUiThread {
            txtRiskState.text = "상태 : $riskLevel"
            txtRiskState.setBackgroundColor(
                Color.parseColor(
                    when (riskLevel) {
                        "정상" -> "#2E7D32"
                        "주의" -> "#F9A825"
                        "위험" -> "#D32F2F"
                        "응급" -> "#B71C1C"
                        else  -> "#777777"
                    }
                )
            )
        }
    }

    // 위험 이상이면 경고 다이얼로그와 진동 실행
    private fun showRiskWarning(riskLevel: String, message: String) {
        if (riskLevel == "정상" || riskLevel == "주의") return
        if (isWarningShowing) return

        isWarningShowing = true

        vibrator?.let {
            if (it.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createOneShot(700, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(700)
                }
            }
        }

        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("위험 알림")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("확인") { dialog, _ ->
                    isWarningShowing = false
                    dialog.dismiss()
                }
                .show()
        }
    }

    // 앱에서 계산한 위험도를 ESP32로 전송
    private fun sendRiskCommandToEsp32(command: String) {
        val gatt = bluetoothGatt ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val targetService = gatt.getService(TARGET_SERVICE_UUID) ?: return
        val controlCharacteristic = targetService.getCharacteristic(CONTROL_CHARACTERISTIC_UUID) ?: return

        val sendData = command.toByteArray(StandardCharsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                controlCharacteristic,
                sendData,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            )
        } else {
            @Suppress("DEPRECATION")
            controlCharacteristic.value = sendData
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(controlCharacteristic)
        }
    }

    // 수신 문자열 파싱 + 화면 표시 + 위험도 계산
    private fun parseAndDisplayData(data: String) {
        try {
            val parts = data.split(",")

            var id = ""
            var tempStr = ""
            var hrStr = ""
            var envStr = ""
            var humStr = ""
            var posture = ""

            for (part in parts) {
                val keyValue = part.split(":")
                if (keyValue.size < 2) continue

                when (keyValue[0]) {
                    "ID"      -> id      = keyValue[1]
                    "TEMP"    -> tempStr = keyValue[1]
                    "HR"      -> hrStr   = keyValue[1]
                    "ENV"     -> envStr  = keyValue[1]
                    "HUM"     -> humStr  = keyValue[1]
                    "POSTURE" -> posture = keyValue[1]
                }
            }

            val temp   = tempStr.toDoubleOrNull() ?: 0.0
            val hr     = hrStr.toIntOrNull()    ?: 0
            val env    = envStr.toDoubleOrNull() ?: 0.0
            val hum    = humStr.toIntOrNull()   ?: 0

            val riskLevel = calculateRiskLevel(temp, hr, env, hum, posture)

            val display = """
                ID : $id
                체온 : $temp
                심박수 : $hr
                환경온도 : $env
                습도 : $hum
                자세 : $posture
                위험도 : $riskLevel
            """.trimIndent()

            runOnUiThread { txtData.text = display }

            updateRiskUI(riskLevel)

            val riskCommand = convertRiskToCommand(riskLevel)
            sendRiskCommandToEsp32(riskCommand)

            when (riskLevel) {
                "위험" -> showRiskWarning("위험", "열사병 위험이 감지되었습니다.\n즉시 휴식하세요.")
                "응급" -> showRiskWarning("응급", "응급 상태가 감지되었습니다.\n즉시 작업을 중단하세요.")
            }

        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                txtData.text = "데이터 파싱 오류\n$data"
                txtRiskState.text = "상태 : 오류"
                txtRiskState.setBackgroundColor(Color.parseColor("#777777"))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopBleScan()

        bluetoothGatt?.let { gatt ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    bluetoothGatt = null
                    return
                }
            }
            gatt.close()
            bluetoothGatt = null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_BLE_PERMISSION) {
            val isGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            val msg = if (isGranted) "BLE 권한이 허용되었습니다." else "BLE 권한이 필요합니다."
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
    }
}