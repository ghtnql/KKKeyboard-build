package com.ghtnql.kkkeyboard

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.PI
import kotlin.math.sin

internal class CafeCustomerAnimation(private val host: View) {
    enum class Phase { ENTERING, WAITING, HAPPY, DISAPPOINTED, LEAVING, GONE }

    private val sprites = BitmapFactory.decodeResource(host.resources, R.drawable.cafe_customers)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val destination = RectF()
    private val density = host.resources.displayMetrics.density
    private var animator: ValueAnimator? = null
    private var progress = 0f
    private var served = false
    var phase = Phase.GONE
        private set
    var customerIndex = 0
        private set

    fun enter(index: Int, onReady: () -> Unit) {
        customerIndex = index
        served = false
        play(Phase.ENTERING, 650L) {
            idle()
            onReady()
        }
    }

    fun resolve(success: Boolean, onGone: () -> Unit) {
        served = success
        play(if (success) Phase.HAPPY else Phase.DISAPPOINTED, 1_050L) {
            play(Phase.LEAVING, 700L) {
                phase = Phase.GONE
                host.invalidate()
                onGone()
            }
        }
    }

    fun pause() { animator?.pause() }
    fun resume() { animator?.resume() }

    fun stop() {
        cancelAnimator()
        phase = Phase.GONE
    }

    private fun cancelAnimator() {
        animator?.removeAllListeners()
        animator?.removeAllUpdateListeners()
        animator?.cancel()
        animator = null
    }

    private fun play(next: Phase, durationMs: Long, onEnd: () -> Unit) {
        cancelAnimator()
        phase = next
        progress = 0f
        host.invalidate()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            progress = 1f
            onEnd()
            return
        }
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                host.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animator = null
                    onEnd()
                }
            })
            start()
        }
    }

    private fun idle() {
        cancelAnimator()
        phase = Phase.WAITING
        progress = 0f
        if (!ValueAnimator.areAnimatorsEnabled()) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2_600L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                host.invalidate()
            }
            start()
        }
    }

    fun draw(canvas: Canvas, width: Int) {
        if (phase == Phase.GONE) return
        val size = width * 0.52f
        val counterY = width * 0.535f
        val centerX = width * 0.31f
        var offsetX = 0f
        var offsetY = 0f
        var rotation = 0f
        var alpha = 1f
        var column = 0
        val wave = sin(progress * PI * 2).toFloat()
        when (phase) {
            Phase.ENTERING -> {
                offsetX = -width * 0.68f * (1f - OvershootInterpolator(0.5f).getInterpolation(progress))
                offsetY = sin(progress * PI * 6).toFloat() * density * 2f
            }
            Phase.WAITING -> {
                offsetY = wave * density * 1.3f
                rotation = wave * 0.45f
            }
            Phase.HAPPY -> {
                column = 1
                offsetY = -sin(progress * PI).toFloat() * density * 6f
                rotation = wave * 1.5f
            }
            Phase.DISAPPOINTED -> {
                column = 2
                offsetY = progress * density * 4f
                rotation = -progress * 3f
            }
            Phase.LEAVING -> {
                column = if (served) 1 else 2
                offsetX = (if (served) width * 1.1f else -width * 0.7f) * progress * progress
                offsetY = sin(progress * PI * 8).toFloat() * density * 2f
                rotation = if (served) 2f else -4f
                alpha = ((1f - progress) / 0.2f).coerceIn(0f, 1f)
            }
            Phase.GONE -> return
        }
        canvas.save()
        // The counter occludes the waist while head/pose motion stays independent of the IME.
        canvas.clipRect(0f, 0f, width.toFloat(), counterY)
        canvas.translate(offsetX, offsetY)
        canvas.rotate(rotation, centerX, counterY)
        destination.set(centerX - size / 2, counterY - size * 0.98f, centerX + size / 2, counterY + size * 0.02f)
        if (phase == Phase.HAPPY && progress < 0.3f) {
            val handover = (progress / 0.3f).coerceIn(0f, 1f)
            drawPose(canvas, 0, 1f - handover)
            drawPose(canvas, column, handover)
        } else {
            drawPose(canvas, column, alpha)
        }
        canvas.restore()
    }

    private fun drawPose(canvas: Canvas, column: Int, alpha: Float) {
        val row = customerIndex % 2
        source.set(column * sprites.width / 3, row * sprites.height / 2, (column + 1) * sprites.width / 3, (row + 1) * sprites.height / 2)
        paint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
        canvas.drawBitmap(sprites, source, destination, paint)
    }
}
