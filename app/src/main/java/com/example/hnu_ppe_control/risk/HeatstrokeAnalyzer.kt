package com.example.hnu_ppe_control.risk

import com.example.hnu_ppe_control.data.RiskLevel
import com.example.hnu_ppe_control.data.SensorData

object HeatstrokeAnalyzer {

    fun analyze(data: SensorData): RiskLevel {
        if (
            data.posture.equals("FALL", ignoreCase = true) ||
            data.posture.equals("EMERGENCY", ignoreCase = true)
        ) {
            return RiskLevel.EMERGENCY
        }

        var score = 0

        score += when {
            data.temp >= 38.0 -> 2
            data.temp >= 37.5 -> 1
            else -> 0
        }

        score += when {
            data.hr >= 120 -> 2
            data.hr >= 100 -> 1
            else -> 0
        }

        score += when {
            data.env >= 35.0 -> 3
            data.env >= 33.0 -> 2
            data.env >= 31.0 -> 1
            else -> 0
        }

        score += when {
            data.hum >= 80 -> 2
            data.hum >= 70 -> 1
            else -> 0
        }

        if (
            data.posture.equals("WARNING", ignoreCase = true) ||
            data.posture.equals("UNSTABLE", ignoreCase = true)
        ) {
            score += 2
        }

        return when {
            score >= 6 -> RiskLevel.EMERGENCY
            score >= 4 -> RiskLevel.DANGER
            score >= 2 -> RiskLevel.CAUTION
            else -> RiskLevel.SAFE
        }
    }
}