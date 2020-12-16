package com.bmdelacruz.vgp

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.R
import androidx.appcompat.widget.AppCompatImageButton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class ThumbStick(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int
) : AppCompatImageButton(context, attrs, defStyleAttr) {
    private val stateChannel = Channel<ButtonState>();

    constructor(context: Context, attrs: AttributeSet?) : this(
        context,
        attrs,
        R.attr.imageButtonStyle
    )

    constructor(context: Context) : this(context, null)

    val stateFlow: Flow<ButtonState>
        get() = stateChannel.receiveAsFlow()

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wasAcknowledged = super.onTouchEvent(event)
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                stateChannel.offer(ButtonState.Pressed)
                true
            }
            MotionEvent.ACTION_UP -> {
                stateChannel.offer(ButtonState.Released)
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                stateChannel.offer(ButtonState.Released)
                true
            }
            else -> wasAcknowledged
        }
    }
}