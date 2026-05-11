// Smart Shield Firebase riskLogs 중복 저장 방지 정책을 관리하는 파일
package com.example.hnu_ppe_control.firebase

import android.util.Log
import com.example.hnu_ppe_control.data.RiskLevel

class RiskLogPolicy {

    companion object {
        private const val TAG = "SmartShieldFirebase"
    }

    private var lastLoggedRiskLevel: RiskLevel? = null

    fun shouldUpload(riskLevel: RiskLevel): Boolean {
        // 위험/응급만 riskLogs에 누적하고 정상/주의는 중복 방지 상태를 초기화
        val shouldLog = riskLevel == RiskLevel.DANGER || riskLevel == RiskLevel.EMERGENCY

        if (!shouldLog) {
            resetIfNeeded()
            return false
        }

        if (lastLoggedRiskLevel == riskLevel) {
            Log.d(TAG, "riskLog skipped. Duplicate riskLevel=${riskLevel.name}")
            return false
        }

        lastLoggedRiskLevel = riskLevel
        return true
    }

    fun messageFor(riskLevel: RiskLevel): String {
        // Firebase riskLogs에서 관리자 앱이 바로 읽을 이벤트 메시지
        return when (riskLevel) {
            RiskLevel.DANGER -> "위험 상태 감지"
            RiskLevel.EMERGENCY -> "응급 상태 감지"
            else -> "상태 감지"
        }
    }

    private fun resetIfNeeded() {
        if (lastLoggedRiskLevel != null) {
            Log.d(TAG, "riskLog duplicate guard reset")
        }
        lastLoggedRiskLevel = null
    }
}
