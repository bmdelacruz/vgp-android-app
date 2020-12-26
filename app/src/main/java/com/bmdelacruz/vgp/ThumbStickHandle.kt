package com.bmdelacruz.vgp

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sqrt

class ThumbStickHandle(
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int,
    defStyleRes: Int,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes), View.OnTouchListener {
    private var previousX = 0f
    private var previousY = 0f
    private var previousDistanceSquared = 0f

    lateinit var onPositionChanged: (x: Float, y: Float) -> Unit
    lateinit var onPositionReset: () -> Unit

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(
        context,
        attrs,
        defStyleAttr,
        0
    )

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context) : this(context, null)

    init {
        setOnTouchListener(this)
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        return when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                previousX = event.rawX
                previousY = event.rawY

                true
            }
            MotionEvent.ACTION_UP -> {
                translationX = 0f
                translationY = 0f

                onPositionReset()

                invalidate()

                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - previousX
                val dy = event.rawY - previousY

                var newTx = translationX + dx
                var newTy = translationY + dy

                val distanceSquared = newTx * newTx + newTy * newTy
                val distanceSquaredDx = distanceSquared.minus(previousDistanceSquared).absoluteValue
                if (distanceSquaredDx >= DISTANCE_THRESHOLD_SQUARED) {
                    previousDistanceSquared = distanceSquared

                    if (distanceSquared >= MAX_DISTANCE_SQUARED) {
                        val magnitude = sqrt(distanceSquared)
                        val utx = newTx / magnitude
                        val uty = newTy / magnitude

                        newTx = utx * MAX_DISTANCE
                        newTy = uty * MAX_DISTANCE

                        onPositionChanged(utx, uty)
                    } else {
                        val utx = newTx / MAX_DISTANCE
                        val uty = newTy / MAX_DISTANCE

                        onPositionChanged(utx, uty)
                    }
                }

                previousX = event.rawX
                previousY = event.rawY
                translationX = newTx
                translationY = newTy

                invalidate()

                true
            }
            else -> false
        }
    }

    companion object {
        private const val MAX_DISTANCE = 60f
        private val MAX_DISTANCE_SQUARED = MAX_DISTANCE.pow(2)
        private val DISTANCE_THRESHOLD_SQUARED = 5f.pow(2)
    }
}