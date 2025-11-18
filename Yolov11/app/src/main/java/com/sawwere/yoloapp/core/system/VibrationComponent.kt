package com.sawwere.yoloapp.core.system

import android.annotation.SuppressLint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

class VibrationComponent(
    private val vibrator: Vibrator
) {
    @SuppressLint("ObsoleteSdkInt")
    fun triggerHapticFeedback() {
        if (vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Современный API с контролем амплитуды и длительности
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        30, // Длительность в миллисекундах
                        VibrationEffect.DEFAULT_AMPLITUDE // Стандартная интенсивность
                    )
                )
            } else {
                // Совместимость со старыми версиями
                @Suppress("DEPRECATION")
                vibrator.vibrate(30)
            }
        }
    }
}