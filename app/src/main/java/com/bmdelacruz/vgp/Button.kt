package com.bmdelacruz.vgp

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageButton

class Button(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int
) : AppCompatImageButton(context, attrs, defStyleAttr) {
    constructor(context: Context, attrs: AttributeSet?) : this(
        context,
        attrs,
        R.attr.imageButtonStyle
    )

    constructor(context: Context) : this(context, null)

    lateinit var onButtonStateChanged: (ButtonState) -> Unit

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val wasAcknowledged = super.onTouchEvent(event)
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                onButtonStateChanged(ButtonState.Pressed)
                true
            }
            MotionEvent.ACTION_UP -> {
                onButtonStateChanged(ButtonState.Released)
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                onButtonStateChanged(ButtonState.Released)
                true
            }
            else -> wasAcknowledged
        }
    }
}