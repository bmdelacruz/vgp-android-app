package com.bmdelacruz.vgp

import com.bmdelacruz.vgp.ComBmdelacruzVgp.Input

sealed class ControllerEvent {
    class ButtonStateChanged(
        val type: ButtonType,
        val state: ButtonState,
    ) : ControllerEvent() {
        override fun toString(): String {
            return "ButtonStateChanged(type=$type, state=$state)"
        }
    }

    class ThumbStickPositionChanged(
        val type: ThumbStickType,
        val axis: ThumbStickAxis,
        val signedNormalizedPosition: Float,
    ) : ControllerEvent() {
        init {
            if (signedNormalizedPosition < -1.0f || signedNormalizedPosition > 1.0f) {
                throw IllegalArgumentException("The position is incorrect! Must be [-1, 1].")
            }
        }

        override fun toString(): String {
            return "ThumbStickPositionChanged(type=$type, axis=$axis, signedNormalizedPosition=$signedNormalizedPosition)"
        }
    }

    fun toInput(): Input = when (this) {
        is ButtonStateChanged -> Input.newBuilder()
            .setButton(
                Input.Button.newBuilder()
                    .setState(when (state) {
                        ButtonState.Pressed -> Input.Button.State.PRESSED
                        ButtonState.Released -> Input.Button.State.RELEASED
                    })
                    .setType(when (type) {
                        ButtonType.DPadUp -> Input.Button.Type.UP
                        ButtonType.DPadDown -> Input.Button.Type.DOWN
                        ButtonType.DPadLeft -> Input.Button.Type.LEFT
                        ButtonType.DPadRight -> Input.Button.Type.RIGHT
                        ButtonType.X -> Input.Button.Type.X
                        ButtonType.Y -> Input.Button.Type.Y
                        ButtonType.A -> Input.Button.Type.A
                        ButtonType.B -> Input.Button.Type.B
                        ButtonType.ThumbL -> Input.Button.Type.THUMB_LEFT
                        ButtonType.ThumbR -> Input.Button.Type.THUMB_RIGHT
                        ButtonType.TriggerL -> Input.Button.Type.TRIGGER_LEFT
                        ButtonType.TriggerR -> Input.Button.Type.TRIGGER_RIGHT
                        ButtonType.TriggerL2 -> Input.Button.Type.TRIGGER_2_LEFT
                        ButtonType.TriggerR2 -> Input.Button.Type.TRIGGER_2_RIGHT
                        ButtonType.Start -> Input.Button.Type.START
                        ButtonType.Select -> Input.Button.Type.SELECT
                    })
            )
            .build()
        is ThumbStickPositionChanged -> Input.newBuilder()
            .setPosition(
                Input.Position.newBuilder()
                    .setType(when (type) {
                        ThumbStickType.Left -> when (axis) {
                            ThumbStickAxis.X -> Input.Position.Type.LEFT_X
                            ThumbStickAxis.Y -> Input.Position.Type.LEFT_Y
                        }
                        ThumbStickType.Right -> when (axis) {
                            ThumbStickAxis.X -> Input.Position.Type.RIGHT_X
                            ThumbStickAxis.Y -> Input.Position.Type.RIGHT_Y
                        }
                    })
                    .setPosition((ABS_MULTIPLIER * signedNormalizedPosition).toInt())
            )
            .build()
    }

    companion object {
        const val ABS_MULTIPLIER = 512

        fun ButtonState.makeEvent(type: ButtonType): ControllerEvent =
            ButtonStateChanged(type, this)
    }
}