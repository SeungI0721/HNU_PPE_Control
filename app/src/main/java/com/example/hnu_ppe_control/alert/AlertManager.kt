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
    private val vibrator =
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private var isWarningShowing = false

    fun handleRisk(riskLevel: RiskLevel) {
        when (riskLevel) {
            RiskLevel.DANGER -> {
                showWarning(
                    title = "위험 알림",
                    message = "열사병 위험이 감지되었습니다.\n즉시 휴식하세요.",
                    vibrationTime = 700
                )
            }

            RiskLevel.EMERGENCY -> {
                showWarning(
                    title = "응급 알림",
                    message = "응급 상태가 감지되었습니다.\n즉시 작업을 중단하세요.",
                    vibrationTime = 1200
                )
            }

            RiskLevel.SAFE,
            RiskLevel.CAUTION,
            RiskLevel.ERROR -> {
                // 정상, 주의, 오류 상태에서는 팝업 없음
            }
        }
    }

    private fun showWarning(
        title: String,
        message: String,
        vibrationTime: Long
    ) {
        // 이미 팝업이 떠 있으면 새 팝업을 띄우지 않음
        if (isWarningShowing) {
            return
        }

        isWarningShowing = true
        vibrate(vibrationTime)

        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("확인") { dialog, _ ->
                isWarningShowing = false
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