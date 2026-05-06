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
            "env" to env,
            "hum" to hum,
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
        env: Double,
        hum: Double,
        posture: String,
        message: String
    ) {
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
            "env" to env,
            "hum" to hum,
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
