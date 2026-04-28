package com.example.hnu_ppe_control.parser

import com.example.hnu_ppe_control.data.SensorData

object SensorDataParser {

    private val allowedPostures = setOf(
        "NORMAL",
        "WARNING",
        "UNSTABLE",
        "FALL",
        "EMERGENCY"
    )

    fun parse(rawData: String): SensorData? {
        val cleanedData = rawData.trim()

        if (cleanedData.isEmpty()) {
            return null
        }

        // 필수 키가 없으면 잘린 데이터로 판단
        if (
            !cleanedData.contains("ID:") ||
            !cleanedData.contains("TEMP:") ||
            !cleanedData.contains("HR:") ||
            !cleanedData.contains("ENV:") ||
            !cleanedData.contains("HUM:") ||
            !cleanedData.contains("POSTURE:")
        ) {
            return null
        }

        val dataMap = mutableMapOf<String, String>()

        val parts = cleanedData.split(",")

        for (part in parts) {
            val keyValue = part.split(":", limit = 2)

            if (keyValue.size != 2) {
                continue
            }

            val key = keyValue[0].trim()
            val value = keyValue[1].trim()

            if (key.isNotEmpty() && value.isNotEmpty()) {
                dataMap[key] = value
            }
        }

        val id = dataMap["ID"] ?: return null
        val temp = dataMap["TEMP"]?.toDoubleOrNull() ?: return null
        val hr = dataMap["HR"]?.toIntOrNull() ?: return null
        val env = dataMap["ENV"]?.toDoubleOrNull() ?: return null
        val hum = dataMap["HUM"]?.toIntOrNull() ?: return null
        val posture = dataMap["POSTURE"]?.uppercase() ?: return null

        if (!isValidWorkerId(id)) {
            return null
        }

        if (!isValidSensorRange(temp, hr, env, hum)) {
            return null
        }

        if (!allowedPostures.contains(posture)) {
            return null
        }

        return SensorData(
            id = id,
            temp = temp,
            hr = hr,
            env = env,
            hum = hum,
            posture = posture
        )
    }

    private fun isValidWorkerId(id: String): Boolean {
        return id.length == 4 && id.all { it.isDigit() }
    }

    private fun isValidSensorRange(
        temp: Double,
        hr: Int,
        env: Double,
        hum: Int
    ): Boolean {
        if (temp.isNaN() || env.isNaN()) {
            return false
        }

        if (temp < 30.0 || temp > 43.0) {
            return false
        }

        if (hr < 30 || hr > 220) {
            return false
        }

        if (env < -20.0 || env > 60.0) {
            return false
        }

        if (hum < 0 || hum > 100) {
            return false
        }

        return true
    }
}