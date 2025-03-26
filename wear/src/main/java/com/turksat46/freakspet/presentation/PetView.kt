package com.example.wearablepet

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.util.AttributeSet
import android.view.View
import java.util.Random

class PetView(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val toothPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private var eyeRadius = 30f // Augenradius vergrößert
    private var eyeSpacing = 50f // Augenabstand vergrößert
    private var mouthWidth = 120f // Mundbreite vergrößert
    private var mouthHeight = 60f // Mundhöhe vergrößert
    private var mouthYOffset = 80f // Mundposition nach unten verschoben
    private var toothWidth = 20f // Zahnbreite vergrößert
    private var toothHeight = 20f // Zahnhöhe vergrößert
    private var toothOffsetX = 30f // Zahnposition nach rechts verschoben

    private var isBlinking = false
    private var blinkStartTime: Long = 0
    private val blinkDuration = 200L
    private val minBlinkInterval = 3000
    private val maxBlinkInterval = 8000

    private val handler = Handler()
    private val blinkRunnable = Runnable {
        startBlinkAnimation()
        val nextInterval = Random().nextInt(maxBlinkInterval - minBlinkInterval) + minBlinkInterval

    }

    init {
        startBlinking()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2
        val centerY = height / 2
        val leftEyeX = centerX - eyeSpacing / 2
        val rightEyeX = centerX + eyeSpacing / 2
        val eyeY = centerY - 80f // Augenposition nach oben verschoben
        val mouthY = centerY + mouthYOffset

        // Augen zeichnen (Kreise)
        canvas.drawCircle(leftEyeX, eyeY, eyeRadius, eyePaint)
        canvas.drawCircle(rightEyeX, eyeY, eyeRadius, eyePaint)

        // Mund zeichnen (abgerundetes Rechteck)
        val mouthLeft = centerX - mouthWidth / 2
        val mouthTop = mouthY - mouthHeight / 2
        val mouthRight = centerX + mouthWidth / 2
        val mouthBottom = mouthY + mouthHeight / 2
        val mouthRect = RectF(mouthLeft, mouthTop, mouthRight, mouthBottom)
        val cornerRadius = mouthHeight / 2
        canvas.drawRoundRect(mouthRect, cornerRadius, cornerRadius, mouthPaint)

        // Zahn zeichnen (Rechteck)
        val toothLeft = centerX + toothOffsetX - toothWidth / 2
        val toothTop = mouthY - toothHeight / 2
        val toothRight = centerX + toothOffsetX + toothWidth / 2
        val toothBottom = mouthY + toothHeight / 2
        canvas.drawRect(toothLeft, toothTop, toothRight, toothBottom, toothPaint)
    }

    private fun startBlinkAnimation() {
        isBlinking = true
        blinkStartTime = System.currentTimeMillis()
        invalidate()
        handler.postDelayed({
            isBlinking = false
            invalidate()
        }, blinkDuration)
    }

    private fun startBlinking() {
        val initialDelay = Random().nextInt(maxBlinkInterval - minBlinkInterval) + minBlinkInterval
        handler.postDelayed(blinkRunnable, initialDelay.toLong())
    }

    fun stopBlinking() {
        handler.removeCallbacks(blinkRunnable)
        isBlinking = false
        invalidate()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            startBlinking()
        } else {
            stopBlinking()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startBlinking()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopBlinking()
    }
}