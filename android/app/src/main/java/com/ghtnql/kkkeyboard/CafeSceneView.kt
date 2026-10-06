package com.ghtnql.kkkeyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CafeSceneView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val backgroundBitmap: Bitmap? = BitmapFactory.decodeResource(resources, R.drawable.cafe_background)
    private val customer = CafeCustomerAnimation(this)
    internal val customerPhase get() = customer.phase
    internal val customerIndex get() = customer.customerIndex
    private val destination = RectF()
    private val counterColor = backgroundBitmap?.let { it.getPixel(it.width / 2, it.height - 1) }
        ?: Color.rgb(209, 235, 222)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(3f)
        color = Color.argb(220, 55, 65, 65)
    }
    private val writingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(45, 67, 83)
        textSize = dp(20f)
        typeface = Typeface.create("casual", Typeface.NORMAL)
    }
    private var writing: StaticLayout? = null
    private var accepted = false
    private var rejected = false
    private var completedAtMs = 0L

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.cafe_scene_description)
    }

    fun showOrder(item: LearningItem, typed: String = "") {
        accepted = false
        rejected = false
        completedAtMs = 0L
        writing = StaticLayout.Builder.obtain(typed, 0, typed.length, writingPaint, dp(124f).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        contentDescription = context.getString(R.string.cafe_scene_order_description, item.sourceText)
        invalidate()
    }

    fun celebrate() {
        accepted = true
        completedAtMs = SystemClock.elapsedRealtime()
        announceForAccessibility(context.getString(R.string.cafe_complete))
        if (ValueAnimator.areAnimatorsEnabled()) postInvalidateOnAnimation() else invalidate()
    }

    fun enterCustomer(item: LearningItem, index: Int, onReady: () -> Unit) {
        showOrder(item)
        customer.enter(index, onReady)
    }

    fun resolveCustomer(success: Boolean, onGone: () -> Unit) {
        if (success) celebrate() else {
            rejected = true
            announceForAccessibility(context.getString(R.string.cafe_customer_disappointed))
        }
        customer.resolve(success, onGone)
    }

    fun pauseCustomer() = customer.pause()
    fun resumeCustomer() = customer.resume()

    override fun onDetachedFromWindow() {
        customer.stop()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        val bitmap = backgroundBitmap ?: return
        // IME changes reveal less counter, without moving or scaling the customer.
        val scale = width.toFloat() / bitmap.width
        destination.set(0f, 0f, width.toFloat(), bitmap.height * scale)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(counterColor)
        backgroundBitmap?.let { canvas.drawBitmap(it, null, destination, null) }
        customer.draw(canvas, width)

        val paper = RectF(
            width - dp(164f),
            height - dp(108f),
            width - dp(12f),
            height - dp(12f),
        )
        drawCompletion(canvas, paper.centerX(), paper.centerY())
        paint.color = Color.argb(35, 34, 60, 45)
        canvas.drawRoundRect(paper.left + dp(3f), paper.top + dp(6f), paper.right + dp(3f), paper.bottom + dp(6f), dp(5f), dp(5f), paint)
        paint.color = Color.rgb(224, 131, 127)
        canvas.drawRoundRect(paper.left - dp(3f), paper.top, paper.right + dp(3f), paper.bottom + dp(4f), dp(5f), dp(5f), paint)
        paint.color = Color.rgb(255, 255, 250)
        canvas.drawRoundRect(paper, dp(3f), dp(3f), paint)
        paint.strokeWidth = dp(1f)
        paint.color = Color.rgb(202, 219, 229)
        for (offset in listOf(48f, 71f, 89f)) {
            canvas.drawLine(paper.left + dp(10f), paper.top + dp(offset), paper.right - dp(8f), paper.top + dp(offset), paint)
        }
        paint.color = Color.rgb(237, 184, 181)
        canvas.drawLine(paper.left + dp(18f), paper.top + dp(17f), paper.left + dp(18f), paper.bottom - dp(5f), paint)
        for (index in 0 until 6) {
            val x = paper.left + dp(17f + index * 23f)
            canvas.drawRoundRect(x, paper.top - dp(4f), x + dp(5f), paper.top + dp(7f), dp(2f), dp(2f), outlinePaint)
        }
        paint.color = Color.rgb(128, 117, 118)
        paint.textSize = dp(11f)
        canvas.drawText(context.getString(R.string.cafe_order_label), paper.left + dp(25f), paper.top + dp(23f), paint)
        canvas.save()
        canvas.translate(paper.left + dp(24f), paper.top + dp(29f))
        writing?.draw(canvas)
        canvas.restore()
        if (accepted) {
            val x = paper.right - dp(18f)
            val y = paper.top + dp(20f)
            paint.color = Color.rgb(58, 139, 107)
            canvas.drawCircle(x, y, dp(12f), paint)
            paint.color = Color.WHITE
            paint.strokeWidth = dp(2.5f)
            canvas.drawLine(x - dp(6f), y, x - dp(1f), y + dp(5f), paint)
            canvas.drawLine(x - dp(1f), y + dp(5f), x + dp(7f), y - dp(5f), paint)
        }
        if (rejected) {
            val x = paper.right - dp(18f)
            val y = paper.top + dp(20f)
            paint.color = Color.rgb(202, 91, 87)
            canvas.drawCircle(x, y, dp(12f), paint)
            paint.color = Color.WHITE
            paint.strokeWidth = dp(2.5f)
            canvas.drawLine(x - dp(5f), y - dp(5f), x + dp(5f), y + dp(5f), paint)
            canvas.drawLine(x + dp(5f), y - dp(5f), x - dp(5f), y + dp(5f), paint)
        }
    }

    private fun drawCompletion(canvas: Canvas, centerX: Float, centerY: Float) {
        if (completedAtMs == 0L || !ValueAnimator.areAnimatorsEnabled()) return
        val elapsed = SystemClock.elapsedRealtime() - completedAtMs
        if (elapsed > 750L) return
        val progress = (elapsed / 750f).coerceIn(0f, 1f)
        val alpha = ((1f - progress) * 255).toInt()
        paint.color = Color.argb(alpha, 255, 208, 76)
        for (index in 0 until 10) {
            val angle = (PI * 2 * index / 10).toFloat()
            val distance = dp(60f + 42f * progress)
            drawSparkle(
                canvas,
                centerX + cos(angle) * distance,
                centerY + sin(angle) * distance,
                dp(5f * (1f - progress)),
            )
        }
        postInvalidateOnAnimation()
    }

    private fun drawSparkle(canvas: Canvas, x: Float, y: Float, radius: Float) {
        if (radius <= 0f) return
        val path = Path().apply {
            moveTo(x, y - radius)
            lineTo(x + radius * 0.35f, y - radius * 0.35f)
            lineTo(x + radius, y)
            lineTo(x + radius * 0.35f, y + radius * 0.35f)
            lineTo(x, y + radius)
            lineTo(x - radius * 0.35f, y + radius * 0.35f)
            lineTo(x - radius, y)
            lineTo(x - radius * 0.35f, y - radius * 0.35f)
            close()
        }
        canvas.drawPath(path, paint)
    }

    private fun dp(value: Float): Float = value * density
}
