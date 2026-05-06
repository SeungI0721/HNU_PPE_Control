package com.example.hnu_ppe_control.data

enum class RiskLevel(val label: String) {
    SAFE("정상"),
    CAUTION("주의"),
    DANGER("위험"),
    EMERGENCY("응급"),
    ERROR("오류")
}
