package com.bmdelacruz.vgp

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

sealed class ForceFeedback {
    abstract val id: Int
    abstract val replayDelay: Int
    abstract val replayLength: Int

    abstract fun performVibration(vibrator: Vibrator)

    class Rumble(
        override val id: Int,
        override val replayDelay: Int,
        override val replayLength: Int,
        @Suppress("unused") val strongMagnitude: Int,
        @Suppress("unused") val weakMagnitude: Int,
    ) : ForceFeedback() {
        private val vibrationEffect by lazy {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                VibrationEffect.createOneShot(
                    replayLength.toLong(),
                    VibrationEffect.DEFAULT_AMPLITUDE // FIXME
                )
            } else {
                throw IllegalStateException()
            }
        }

        override fun performVibration(vibrator: Vibrator) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(replayLength.toLong())
            }
        }
    }
}
