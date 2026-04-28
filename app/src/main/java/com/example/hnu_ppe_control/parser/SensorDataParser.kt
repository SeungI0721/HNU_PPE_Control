package com.example.hnu_ppe_control.parser

import com.example.hnu_ppe_control.data.SensorData

object SensorDataParser {

    fun parse(rawData: String): SensorData? {
        return try {
            val map = rawData
                .trim()
                .split(",")
                .mapNotNull { part ->
                    val keyValue = part.split(":")
                    if (keyValue.size >= 2) {
                        keyValue[0].trim() to keyValue[1].trim()
                    } else {
                        null
                    }
                }
                .toMap()

            SensorData(
                id = map["ID"].orEmpty(),
                temp = map["TEMP"]?.toDoubleOrNull() ?: return null,
                hr = map["HR"]?.toIntOrNull() ?: return null,
                env = map["ENV"]?.toDoubleOrNull() ?: return null,
                hum = map["HUM"]?.toIntOrNull() ?: return null,
                posture = map["POSTURE"].orEmpty()
            )
        } catch (e: Exception) {
            null
        }
    }
}