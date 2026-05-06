package com.example.hnu_ppe_control.alert

import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.example.hnu_ppe_control.data.RiskLevel

class AlertManager(
    private val context: Context
) {
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private var showingRiskLevel: RiskLevel? = null

    fun handleRisk(riskLevel: RiskLevel) {
        when (riskLevel) {
            RiskLevel.SAFE, RiskLevel.ERROR -> {
                showingRiskLevel = null
            }

            RiskLevel.CAUTION -> {
                showWarning(
                    riskLevel = riskLevel,
                    title = "주의 알림",
                    message = "작업자 상태가 주의 단계입니다.\n수분 섭취와 휴식을 권장합니다.",
                    vibrationTime = 300L
                )
            }

            RiskLevel.DANGER -> {
                showWarning(
                    riskLevel = riskLevel,
                    title = "위험 알림",
                    message = "위험 상태가 감지되었습니다.\n즉시 작업을 멈추고 안전한 곳에서 휴식하세요.",
                    vibrationTime = 700L
                )
            }

            RiskLevel.EMERGENCY -> {
                showWarning(
                    riskLevel = riskLevel,
                    title = "응급 알림",
                    message = "응급 상태가 감지되었습니다.\n즉시 주변에 도움을 요청하고 작업을 중단하세요.",
                    vibrationTime = 1200L
                )
            }
        }
    }

    private fun showWarning(
        riskLevel: RiskLevel,
        title: String,
        message: String,
        vibrationTime: Long
    ) {
        if (showingRiskLevel == riskLevel) {
            return
        }

        showingRiskLevel = riskLevel
        vibrate(vibrationTime)

        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun vibrate(duration: Long) {
        if (!vibrator.hasVibrator()) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    duration,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}
