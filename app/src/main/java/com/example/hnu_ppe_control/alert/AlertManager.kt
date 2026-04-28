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
            RiskLevel.DANGER -> showWarning(
                title = "위험 알림",
                message = "열사병 위험이 감지되었습니다.\n즉시 휴식하세요."
            )

            RiskLevel.EMERGENCY -> showWarning(
                title = "응급 알림",
                message = "응급 상태가 감지되었습니다.\n즉시 작업을 중단하세요."
            )

            else -> Unit
        }
    }

    private fun showWarning(title: String, message: String) {
        if (isWarningShowing) return
        isWarningShowing = true
        vibrate()

        // Activity 캐스팅해서 runOnUiThread 보장
        (context as? android.app.Activity)?.runOnUiThread {
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
    }

    private fun vibrate() {
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    700,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(700)
        }
    }
}