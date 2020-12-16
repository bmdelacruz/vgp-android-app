package com.bmdelacruz.vgp

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
        val xSignedNormalizedPosition: Float,
        val ySignedNormalizedPosition: Float,
    ) : ControllerEvent() {
        init {
            if (
                xSignedNormalizedPosition < -1.0f || xSignedNormalizedPosition > 1.0f ||
                ySignedNormalizedPosition < -1.0f || ySignedNormalizedPosition > 1.0f
            ) {
                throw IllegalArgumentException("The position is incorrect! Must be [-1, 1].")
            }
        }

        override fun toString(): String {
            return "ThumbStickPositionChanged(type=$type, xSignedNormalizedPosition=$xSignedNormalizedPosition, ySignedNormalizedPosition=$ySignedNormalizedPosition)"
        }
    }

    fun toInput(): Gamepad.InputData = when (this) {
        is ButtonStateChanged -> Gamepad.InputData.newBuilder()
            .setButton(
                Gamepad.Button.newBuilder()
                    .setState(
                        when (state) {
                            ButtonState.Pressed -> Gamepad.ButtonState.PRESSED
                            ButtonState.Released -> Gamepad.ButtonState.RELEASED
                        }
                    )
                    .setType(
                        when (type) {
                            ButtonType.DPadUp -> Gamepad.ButtonType.UP
                            ButtonType.DPadDown -> Gamepad.ButtonType.DOWN
                            ButtonType.DPadLeft -> Gamepad.ButtonType.LEFT
                            ButtonType.DPadRight -> Gamepad.ButtonType.RIGHT
                            ButtonType.X -> Gamepad.ButtonType.X
                            ButtonType.Y -> Gamepad.ButtonType.Y
                            ButtonType.A -> Gamepad.ButtonType.A
                            ButtonType.B -> Gamepad.ButtonType.B
                            ButtonType.ThumbL -> Gamepad.ButtonType.THUMB_LEFT
                            ButtonType.ThumbR -> Gamepad.ButtonType.THUMB_RIGHT
                            ButtonType.TriggerL -> Gamepad.ButtonType.TRIGGER_LEFT
                            ButtonType.TriggerR -> Gamepad.ButtonType.TRIGGER_RIGHT
                            ButtonType.TriggerL2 -> Gamepad.ButtonType.TRIGGER_2_LEFT
                            ButtonType.TriggerR2 -> Gamepad.ButtonType.TRIGGER_2_RIGHT
                            ButtonType.Start -> Gamepad.ButtonType.START
                            ButtonType.Select -> Gamepad.ButtonType.SELECT
                        }
                    )
            )
            .build()
        is ThumbStickPositionChanged -> Gamepad.InputData.newBuilder()
            .setThumbStick(
                Gamepad.ThumbStick.newBuilder()
                    .setType(
                        when (type) {
                            ThumbStickType.Left -> Gamepad.ThumbStickType.LEFT_THUMB_STICK
                            ThumbStickType.Right -> Gamepad.ThumbStickType.RIGHT_THUMB_STICK
                        }
                    )
                    .setX(xSignedNormalizedPosition)
                    .setY(ySignedNormalizedPosition)
            )
            .build()
    }

    companion object {
        fun ButtonState.makeEvent(type: ButtonType): ControllerEvent =
            ButtonStateChanged(type, this)
    }
}