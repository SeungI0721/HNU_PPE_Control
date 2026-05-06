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
        env: Double,
        hum: Double,
        posture: String,
        riskLevel: String,
        riskCommand: String,
        bleConnected: Boolean,
        appSessionActive: Boolean
    ) {
        if (workerId.isBlank()) {
            Log.e(TAG, "Upload canceled: workerId is blank")
            return
        }

        // Firebase 네트워크 연결 강제 활성화
        FirebaseDatabase.getInstance().goOnline()

        val ref = database.getReference("workers")
            .child(workerId)
            .child("currentStatus")

        val data = mapOf(
            "workerId" to workerId,
            "deviceName" to deviceName,
            "temp" to temp,
            "hr" to hr,
            "env" to env,
            "hum" to hum,
            "posture" to posture,
            "riskLevel" to riskLevel,
            "riskCommand" to riskCommand,
            "bleConnected" to bleConnected,
            "appSessionActive" to appSessionActive,
            "updatedAt" to System.currentTimeMillis()
        )

        Log.d(TAG, "Uploading to path: workers/$workerId/currentStatus")
        Log.d(TAG, "Data: $data")

        ref.setValue(data) { error: DatabaseError?, _ ->
            if (error == null) {
                Log.d(TAG, "Upload SUCCESS: workers/$workerId/currentStatus")
            } else {
                Log.e(
                    TAG,
                    "Upload FAILED: code=${error.code}, message=${error.message}, details=${error.details}"
                )
            }
        }
    }
}