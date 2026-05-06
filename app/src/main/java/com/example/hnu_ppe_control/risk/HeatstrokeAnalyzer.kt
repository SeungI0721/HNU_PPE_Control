// 센서 데이터와 자세 상태를 기반으로 Smart Shield 위험 단계를 계산하는 파일
package com.example.hnu_ppe_control.risk

import com.example.hnu_ppe_control.data.RiskLevel
import com.example.hnu_ppe_control.data.SensorData

object HeatstrokeAnalyzer {

    fun analyze(data: SensorData): RiskLevel {
        // 낙상과 응급 자세는 생체 점수와 관계없이 즉시 응급으로 판단
        if (
            data.posture.equals("FALL", ignoreCase = true) ||
            data.posture.equals("EMERGENCY", ignoreCase = true)
        ) {
            return RiskLevel.EMERGENCY
        }

        var score = 0

        // 피부 온도, 심박수, 주변 온도, 습도, 자세를 점수화
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
            data.spo2 != null && data.spo2 <= 90 -> 3
            data.spo2 != null && data.spo2 <= 94 -> 1
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

        //강한 직사광선 가능성이 있으면 열 위험을 가중
        score += when {
            data.lux >= 50000 -> 2
            data.lux >= 30000 -> 1
            else -> 0
        }

        if (
            data.posture.equals("WARNING", ignoreCase = true) ||
            data.posture.equals("UNSTABLE", ignoreCase = true)
        ) {
            score += 2
        }

        return when {
            score >= 7 -> RiskLevel.EMERGENCY
            score >= 4 -> RiskLevel.DANGER
            score >= 2 -> RiskLevel.CAUTION
            else -> RiskLevel.SAFE
        }
    }
}
