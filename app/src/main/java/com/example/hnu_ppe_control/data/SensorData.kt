package com.example.hnu_ppe_control.data

data class SensorData(
    val id: String,
    val temp: Double,
    val hr: Int,
    val env: Double,
    val hum: Int,
    val posture: String
)