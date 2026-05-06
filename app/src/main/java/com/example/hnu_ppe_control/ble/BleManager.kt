// Smart Shield ESP32 BLE 스캔, 연결, Notify 수신, Write 전송, 자동 재연결을 담당하는 파일
package com.example.hnu_ppe_control.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.nio.charset.StandardCharsets

class BleManager(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onScanStarted()
        fun onScanStopped()
        fun onScanFailed(errorCode: Int)
        fun onDeviceFound(deviceInfo: BleDeviceInfo)
        fun onBleStatusChanged(message: String)
        fun onReconnectStatusChanged(message: String)
        fun onConnected(deviceName: String, address: String)
        fun onDisconnected(manual: Boolean)
        fun onReconnectFailed()
        fun onNotifyReady()
        fun onDataReceived(rawData: String)
        fun onWriteResult(command: String, started: Boolean, reason: String?)
    }

    data class BleDeviceInfo(
        val device: BluetoothDevice,
        val name: String,
        val address: String
    )

    companion object {
        private const val TAG = "SmartShieldBLE"
        private const val RECONNECT_INTERVAL_MS = 3000L
        private const val RECONNECT_MAX_DURATION_MS = 10 * 60 * 1000L
        private const val UNSTABLE_TIMEOUT_MS = 10 * 1000L
        private const val OFFLINE_TIMEOUT_MS = 30 * 1000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private var bluetoothGatt: BluetoothGatt? = null

    private var isScanning = false
    private var isManualDisconnect = false
    private var isReconnecting = false
    private var reconnectStartTime = 0L
    private var lastDataReceivedTime = 0L
    private var isOfflineCheckerRunning = false

    private var lastConnectedDevice: BluetoothDevice? = null
    private var lastSentRiskCommand: String? = null

    var isBleConnected = false
        private set

    var isServiceDiscovered = false
        private set

    var isNotifyReady = false
        private set

    var connectedDeviceName: String = "Unknown"
        private set

    var connectedDeviceAddress: String? = null
        private set

    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun startScan() {
        // BLE 스캐너 상태와 권한을 확인한 뒤 SS_XXXX 장치 검색을 시작합니다.
        bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

        if (bluetoothLeScanner == null) {
            listener.onBleStatusChanged("BLE 상태: 스캐너 사용 불가")
            Log.e(TAG, "BLE scanner is null")
            return
        }

        if (isScanning) {
            listener.onBleStatusChanged("BLE 상태: 이미 스캔 중")
            return
        }

        if (!BlePermissionHelper.hasScanPermission(context)) {
            listener.onBleStatusChanged("BLE 상태: 스캔 권한 없음")
            return
        }

        isScanning = true
        listener.onScanStarted()

        try {
            bluetoothLeScanner?.startScan(scanCallback)
            Log.d(TAG, "BLE scan started")
        } catch (e: SecurityException) {
            isScanning = false
            Log.e(TAG, "startScan failed by permission", e)
            listener.onBleStatusChanged("BLE 상태: 스캔 권한 오류")
            return
        }

        mainHandler.postDelayed({
            if (isScanning) stopScan()
        }, BleConstants.SCAN_PERIOD)
    }

    fun stopScan() {
        // 스캔은 배터리 소모가 크기 때문에 제한 시간 후 반드시 중지
        if (bluetoothLeScanner != null && isScanning && BlePermissionHelper.hasScanPermission(context)) {
            try {
                bluetoothLeScanner?.stopScan(scanCallback)
                Log.d(TAG, "BLE scan stopped")
            } catch (e: SecurityException) {
                Log.e(TAG, "stopScan failed by permission", e)
            }
        }

        if (isScanning) {
            isScanning = false
            listener.onScanStopped()
        }
    }

    fun connect(device: BluetoothDevice) {
        // 사용자가 선택한 ESP32 장치에 GATT 연결을 시도
        stopScan()

        if (!BlePermissionHelper.hasConnectPermission(context)) {
            listener.onBleStatusChanged("BLE 상태: 연결 권한 없음")
            return
        }

        val address = readDeviceAddress(device) ?: return
        val name = readDeviceName(device) ?: "Unknown"

        resetConnectionFlags()
        isManualDisconnect = false
        lastConnectedDevice = device
        connectedDeviceAddress = address
        connectedDeviceName = name

        listener.onBleStatusChanged("BLE 상태: 연결 시도 중")
        Log.d(TAG, "Connecting to device: $address / $name")

        try {
            bluetoothGatt?.close()
            bluetoothGatt = null
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "connectGatt failed by permission", e)
            listener.onBleStatusChanged("BLE 상태: 연결 권한 오류")
        }
    }

    fun disconnectManually() {
        // 작업자가 직접 연결을 끊으면 자동 재연결을 하지 않습니다.
        isManualDisconnect = true
        stopReconnect()
        stopOfflineChecker()

        try {
            if (BlePermissionHelper.hasConnectPermission(context)) {
                bluetoothGatt?.disconnect()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Manual disconnect failed by permission", e)
        }

        closeGatt()
        resetConnectionFlags()
        listener.onDisconnected(manual = true)
    }

    fun writeRiskCommand(command: String) {
        // 위험 명령은 같은 값이 반복 전송되지 않도록 막습니다.
        if (lastSentRiskCommand == command) {
            Log.d(TAG, "BLE write skipped. Duplicate command=$command")
            listener.onWriteResult(command, started = false, reason = "duplicate")
            return
        }

        if (!isBleConnected || !isServiceDiscovered) {
            listener.onWriteResult(command, started = false, reason = "not_ready")
            return
        }

        val gatt = bluetoothGatt
        if (gatt == null) {
            listener.onWriteResult(command, started = false, reason = "gatt_null")
            return
        }

        if (!BlePermissionHelper.hasConnectPermission(context)) {
            listener.onWriteResult(command, started = false, reason = "permission")
            return
        }

        val service = gatt.getService(BleConstants.TARGET_SERVICE_UUID)
        val characteristic = service?.getCharacteristic(BleConstants.CONTROL_CHARACTERISTIC_UUID)

        if (service == null || characteristic == null) {
            listener.onWriteResult(command, started = false, reason = "characteristic_null")
            return
        }

        val sendData = command.toByteArray(StandardCharsets.UTF_8)
        val started = writeCharacteristic(gatt, characteristic, sendData)

        if (started) {
            lastSentRiskCommand = command
            Log.d(TAG, "BLE write started successfully. command=$command")
        } else {
            Log.e(TAG, "BLE write failed to start. command=$command")
        }

        listener.onWriteResult(command, started, if (started) null else "write_failed")
    }

    fun release() {
        // Activity 종료 시 BLE 관련 콜백과 GATT 리소스를 모두 정리
        stopScan()
        stopReconnect()
        stopOfflineChecker()
        closeGatt()
        resetConnectionFlags()
        lastConnectedDevice = null
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)

            val device = result.device ?: return
            val scanRecord = result.scanRecord ?: return
            val name = readDeviceName(device) ?: return
            val address = readDeviceAddress(device) ?: return

            if (!name.matches(Regex("^SS_\\d{4}$"))) return
            if (!hasTargetServiceUuid(scanRecord)) return

            listener.onDeviceFound(
                BleDeviceInfo(
                    device = device,
                    name = name,
                    address = address
                )
            )
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            isScanning = false
            Log.e(TAG, "BLE scan failed. errorCode=$errorCode")
            listener.onScanFailed(errorCode)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> handleGattConnected(gatt)
                BluetoothProfile.STATE_DISCONNECTED -> handleGattDisconnected(status)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            super.onServicesDiscovered(gatt, status)
            handleServicesDiscovered(gatt, status)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            handleNotifyValue(characteristic, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            handleNotifyValue(characteristic, value)
        }
    }

    private fun handleGattConnected(gatt: BluetoothGatt) {
        // 연결 직후에는 GATT 서비스 탐색
        isBleConnected = true
        isServiceDiscovered = false
        isNotifyReady = false
        lastSentRiskCommand = null

        stopReconnect()
        startOfflineChecker()
        listener.onConnected(connectedDeviceName, connectedDeviceAddress ?: "")

        if (!BlePermissionHelper.hasConnectPermission(context)) {
            listener.onBleStatusChanged("BLE 상태: 서비스 탐색 권한 없음")
            return
        }

        try {
            val started = gatt.discoverServices()
            Log.d(TAG, "discoverServices started=$started")
        } catch (e: SecurityException) {
            Log.e(TAG, "discoverServices failed by permission", e)
        }
    }

    private fun handleGattDisconnected(status: Int) {
        // 비정상 연결 해제는 자동 재연결 대상으로 처리
        Log.w(TAG, "BLE disconnected. status=$status")

        isBleConnected = false
        isServiceDiscovered = false
        isNotifyReady = false
        lastSentRiskCommand = null
        listener.onDisconnected(manual = isManualDisconnect)

        if (isManualDisconnect) {
            stopOfflineChecker()
            closeGatt()
        } else {
            startReconnect()
        }
    }

    private fun handleServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        // 대상 서비스, Notify 특성, Write 특성을 찾아 Notify를 활성화
        Log.d(TAG, "onServicesDiscovered: status=$status")

        if (status != BluetoothGatt.GATT_SUCCESS) {
            isServiceDiscovered = false
            isNotifyReady = false
            listener.onBleStatusChanged("BLE 상태: 서비스 탐색 실패")
            return
        }

        val service = gatt.getService(BleConstants.TARGET_SERVICE_UUID)
        val notifyCharacteristic =
            service?.getCharacteristic(BleConstants.DATA_CHARACTERISTIC_UUID)
        val writeCharacteristic =
            service?.getCharacteristic(BleConstants.CONTROL_CHARACTERISTIC_UUID)

        if (service == null) {
            listener.onBleStatusChanged("BLE 상태: 대상 서비스 없음")
            return
        }

        isServiceDiscovered = true

        if (notifyCharacteristic == null) {
            listener.onBleStatusChanged("BLE 상태: Notify 특성 없음")
            return
        }

        if (writeCharacteristic == null) {
            listener.onWriteResult("", started = false, reason = "write_characteristic_null")
        }

        enableNotify(gatt, notifyCharacteristic)
    }

    private fun enableNotify(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        // ESP32가 1초 주기로 보내는 센서 데이터를 받기 위해 CCCD를 활성화
        if (!BlePermissionHelper.hasConnectPermission(context)) {
            listener.onBleStatusChanged("BLE 상태: Notify 권한 없음")
            return
        }

        val notifyEnabled = try {
            gatt.setCharacteristicNotification(characteristic, true)
        } catch (e: SecurityException) {
            Log.e(TAG, "setCharacteristicNotification failed", e)
            false
        }

        val descriptor =
            characteristic.getDescriptor(BleConstants.CLIENT_CHARACTERISTIC_CONFIG_UUID)

        if (descriptor == null) {
            isNotifyReady = false
            listener.onBleStatusChanged("BLE 상태: CCCD 없음")
            return
        }

        val descriptorWriteStarted = writeNotifyDescriptor(gatt, descriptor)
        isNotifyReady = notifyEnabled && descriptorWriteStarted

        if (isNotifyReady) {
            listener.onNotifyReady()
        } else {
            listener.onBleStatusChanged("BLE 상태: Notify 설정 실패")
        }
    }

    private fun handleNotifyValue(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray?
    ) {
        // Notify로 받은 ByteArray를 UTF-8 문자열 payload로 변환
        if (characteristic.uuid != BleConstants.DATA_CHARACTERISTIC_UUID) return

        val bytes = value ?: return
        val rawData = String(bytes, StandardCharsets.UTF_8)
        lastDataReceivedTime = System.currentTimeMillis()

        Log.d(TAG, "Notify received: $rawData")
        listener.onDataReceived(rawData)
    }

    private fun writeNotifyDescriptor(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val result = gatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                )
                result == BluetoothStatusCodes.SUCCESS
            } catch (e: SecurityException) {
                Log.e(TAG, "writeDescriptor failed", e)
                false
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            } catch (e: SecurityException) {
                Log.e(TAG, "writeDescriptor legacy failed", e)
                false
            }
        }
    }

    private fun writeCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        sendData: ByteArray
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val result = gatt.writeCharacteristic(
                    characteristic,
                    sendData,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                )
                result == BluetoothStatusCodes.SUCCESS
            } catch (e: SecurityException) {
                Log.e(TAG, "writeCharacteristic failed by permission", e)
                false
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                characteristic.value = sendData
                @Suppress("DEPRECATION")
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            } catch (e: SecurityException) {
                Log.e(TAG, "writeCharacteristic legacy failed by permission", e)
                false
            }
        }
    }

    private fun startReconnect() {
        // BLE가 예기치 않게 끊기면 3초 간격으로 10분 동안 재연결을 시도
        if (lastConnectedDevice == null || isReconnecting) return

        isReconnecting = true
        reconnectStartTime = System.currentTimeMillis()
        listener.onReconnectStatusChanged("재연결 상태: 시도 중")
        mainHandler.post(reconnectRunnable)
    }

    private fun stopReconnect() {
        if (!isReconnecting) return

        isReconnecting = false
        mainHandler.removeCallbacks(reconnectRunnable)
        listener.onReconnectStatusChanged("재연결 상태: 대기")
    }

    private val reconnectRunnable = object : Runnable {
        override fun run() {
            if (!isReconnecting) return

            val elapsed = System.currentTimeMillis() - reconnectStartTime

            if (elapsed >= RECONNECT_MAX_DURATION_MS) {
                isReconnecting = false
                listener.onReconnectFailed()
                resetConnectionFlags()
                stopOfflineChecker()
                return
            }

            val device = lastConnectedDevice
            if (device == null || !BlePermissionHelper.hasConnectPermission(context)) {
                isReconnecting = false
                return
            }

            listener.onReconnectStatusChanged("재연결 상태: 시도 중 (${elapsed / 1000}s)")

            try {
                bluetoothGatt?.close()
                bluetoothGatt = null
                bluetoothGatt = device.connectGatt(context, false, gattCallback)
            } catch (e: SecurityException) {
                Log.e(TAG, "Reconnect connectGatt failed", e)
            }

            mainHandler.postDelayed(this, RECONNECT_INTERVAL_MS)
        }
    }

    private fun startOfflineChecker() {
        // 연결은 되어 있지만 Notify가 멈춘 상태를 UI에서 확인하기 위한 타이머
        if (isOfflineCheckerRunning) return

        isOfflineCheckerRunning = true
        lastDataReceivedTime = System.currentTimeMillis()
        mainHandler.post(offlineCheckRunnable)
    }

    private fun stopOfflineChecker() {
        if (!isOfflineCheckerRunning) return

        isOfflineCheckerRunning = false
        mainHandler.removeCallbacks(offlineCheckRunnable)
    }

    private val offlineCheckRunnable = object : Runnable {
        override fun run() {
            if (!isOfflineCheckerRunning) return

            val elapsed = System.currentTimeMillis() - lastDataReceivedTime

            when {
                elapsed >= OFFLINE_TIMEOUT_MS -> {
                    listener.onBleStatusChanged("BLE 상태: 데이터 오프라인")
                }

                elapsed >= UNSTABLE_TIMEOUT_MS -> {
                    listener.onBleStatusChanged("BLE 상태: 데이터 수신 불안정")
                }

                isNotifyReady -> {
                    listener.onBleStatusChanged("BLE 상태: 데이터 수신 중")
                }
            }

            mainHandler.postDelayed(this, 1000L)
        }
    }

    private fun hasTargetServiceUuid(scanRecord: ScanRecord): Boolean {
        val serviceUuids = scanRecord.serviceUuids ?: return false
        return serviceUuids.any { it.uuid == BleConstants.TARGET_SERVICE_UUID }
    }

    private fun readDeviceName(device: BluetoothDevice): String? {
        return try {
            if (!BlePermissionHelper.hasConnectPermission(context)) null
            else device.name?.takeIf { it.isNotBlank() }
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot read device name", e)
            null
        }
    }

    private fun readDeviceAddress(device: BluetoothDevice): String? {
        return try {
            if (!BlePermissionHelper.hasConnectPermission(context)) null else device.address
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot read device address", e)
            null
        }
    }

    private fun resetConnectionFlags() {
        isBleConnected = false
        isServiceDiscovered = false
        isNotifyReady = false
        lastSentRiskCommand = null
        lastDataReceivedTime = 0L
    }

    private fun closeGatt() {
        val gatt = bluetoothGatt ?: return

        try {
            if (BlePermissionHelper.hasConnectPermission(context)) {
                gatt.close()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "BluetoothGatt close failed", e)
        }

        bluetoothGatt = null
    }
}
