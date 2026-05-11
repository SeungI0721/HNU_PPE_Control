// 위험 단계를 ESP32로 보낼 RISK 제어 명령 문자열로 변환하는 파일
package com.example.hnu_ppe_control.risk

import com.example.hnu_ppe_control.data.RiskLevel

object RiskCommandMapper {

    fun toCommand(riskLevel: RiskLevel): String {
        // ESP32 펌웨어와 약속한 4개 명령
        return when (riskLevel) {
            RiskLevel.SAFE -> "RISK:SAFE"
            RiskLevel.CAUTION -> "RISK:CAUTION"
            RiskLevel.DANGER -> "RISK:DANGER"
            RiskLevel.EMERGENCY -> "RISK:EMERGENCY"
            RiskLevel.ERROR -> "RISK:SAFE"
        }
    }
}
