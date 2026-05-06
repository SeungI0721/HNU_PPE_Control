// Smart Shield 위험 단계를 enum과 Firebase 표시용 라벨로 관리하는 파일
package com.example.hnu_ppe_control.data

// label은 Firebase와 UI에서 사용자에게 보여줄 한글 표시값
enum class RiskLevel(val label: String) {
    SAFE("정상"),
    CAUTION("주의"),
    DANGER("위험"),
    EMERGENCY("응급"),
    ERROR("오류")
}
