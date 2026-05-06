// Firebase Realtime Database에 작업자 현재 상태와 위험 로그를 업로드하는 파일
package com.example.hnu_ppe_control.firebase

import android.util.Log
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase

object FirebaseStatusUploader {

    private const val TAG = "SmartShieldFirebase"

    private val database = FirebaseDatabase.getInstance()

    fun uploadCurrentStatus(
        workerId: String,
        deviceName: String,
        temp: Double,
        hr: Int,
        spo2: Int?,
        env: Double,
        hum: Double,
        lux: Int,
        posture: String,
        riskLevel: String,
        riskCommand: String,
        bleConnected: Boolean,
        appSessionActive: Boolean
    ) {
        // currentStatus는 관리자 앱이 실시간으로 읽는 덮어쓰기 경로
        if (workerId.isBlank()) {
            Log.e(TAG, "currentStatus upload FAILED. workerId is blank")
            return
        }

        FirebaseDatabase.getInstance().goOnline()

        val ref = database.getReference("workers")
            .child(workerId)
            .child("currentStatus")

        val data = mapOf(
            "workerId" to workerId,
            "deviceName" to deviceName,
            "temp" to temp,
            "hr" to hr,
            "spo2" to spo2,
            "env" to env,
            "hum" to hum,
            "lux" to lux,
            "directSunlight" to (lux >= 50000),
            "posture" to posture,
            "riskLevel" to riskLevel,
            "riskCommand" to riskCommand,
            "bleConnected" to bleConnected,
            "appSessionActive" to appSessionActive,
            "updatedAt" to System.currentTimeMillis()
        )

        Log.d(TAG, "currentStatus upload START path=workers/$workerId/currentStatus data=$data")

        ref.setValue(data) { error: DatabaseError?, _ ->
            if (error == null) {
                Log.d(TAG, "currentStatus upload SUCCESS")
            } else {
                Log.e(
                    TAG,
                    "currentStatus upload FAILED. code=${error.code}, message=${error.message}, details=${error.details}"
                )
            }
        }
    }

    fun uploadRiskLog(
        workerId: String,
        riskLevel: String,
        riskCommand: String,
        temp: Double,
        hr: Int,
        spo2: Int?,
        env: Double,
        hum: Double,
        lux: Int,
        posture: String,
        message: String
    ) {
        // riskLogs는 위험/응급 이벤트를 push()로 누적 저장하는 경로
        if (workerId.isBlank()) {
            Log.e(TAG, "riskLog upload FAILED. workerId is blank")
            return
        }

        FirebaseDatabase.getInstance().goOnline()

        val ref = database.getReference("workers")
            .child(workerId)
            .child("riskLogs")
            .push()

        val data = mapOf(
            "workerId" to workerId,
            "riskLevel" to riskLevel,
            "riskCommand" to riskCommand,
            "temp" to temp,
            "hr" to hr,
            "spo2" to spo2,
            "env" to env,
            "hum" to hum,
            "lux" to lux,
            "directSunlight" to (lux >= 50000),
            "posture" to posture,
            "message" to message,
            "createdAt" to System.currentTimeMillis()
        )

        Log.d(TAG, "riskLog upload START path=workers/$workerId/riskLogs data=$data")

        ref.setValue(data) { error: DatabaseError?, _ ->
            if (error == null) {
                Log.d(TAG, "riskLog upload SUCCESS")
            } else {
                Log.e(
                    TAG,
                    "riskLog upload FAILED. code=${error.code}, message=${error.message}, details=${error.details}"
                )
            }
        }
    }
}
