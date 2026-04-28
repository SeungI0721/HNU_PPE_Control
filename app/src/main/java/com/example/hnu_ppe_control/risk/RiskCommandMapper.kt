package com.example.hnu_ppe_control.risk

import com.example.hnu_ppe_control.data.RiskLevel

object RiskCommandMapper {

    fun toCommand(riskLevel: RiskLevel): String {
        return when (riskLevel) {
            RiskLevel.SAFE -> "RISK:SAFE"
            RiskLevel.CAUTION -> "RISK:CAUTION"
            RiskLevel.DANGER -> "RISK:DANGER"
            RiskLevel.EMERGENCY -> "RISK:EMERGENCY"
            RiskLevel.ERROR -> "RISK:SAFE"
        }
    }
}