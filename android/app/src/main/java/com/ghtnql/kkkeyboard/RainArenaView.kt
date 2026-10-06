package com.ghtnql.kkkeyboard

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

class RainArenaView(context: Context) : View(context) {
    private data class TargetLabel(
        val layout: StaticLayout,
        val width: Float,
        val height: Float,
    )

    private enum class EffectKind { HIT, SPLASH }

    private data class Effect(
        val x: Float,
        val y: Float,
        val startedAtMs: Long,
        val kind: EffectKind,
        val score: Int = 0,
    )

    private val density = resources.displayMetrics.density
    private val backgroundBitmap: Bitmap? = BitmapFactory.decodeResource(resources, R.drawable.rain_city_alley)
    private val backgroundSource = Rect()
    private val backgroundDestination = Rect()
    private val targets = mutableListOf<RainTarget>()
    private val labelCache = mutableMapOf<Long, TargetLabel>()
    private val targetBounds = mutableMapOf<Long, RectF>()
    private val effects = mutableListOf<Effect>()
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(249, 247, 240) }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
        color = Color.argb(180, 71, 89, 91)
    }
    private val activeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = Color.rgb(74, 205, 181)
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(29, 39, 41)
        textSize = sp(20f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val rainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(82, 190, 224, 235)
        strokeWidth = dp(1f)
        strokeCap = Paint.Cap.ROUND
    }
    private val shadePaint = Paint().apply { color = Color.argb(52, 8, 24, 31) }
    private val effectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = sp(17f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(dp(3f), 0f, dp(1f), Color.argb(180, 0, 0, 0))
    }
    private val countdownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = sp(58f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(dp(8f), 0f, dp(2f), Color.argb(210, 0, 0, 0))
    }
    var practiceMode = PracticeMode.KOREAN_TYPING
    private var typedAnswer = ""
    private var countdown: Int? = null
    private var wrongUntilMs = 0L
    private var activeIds = emptySet<Long>()

    init {
        id = R.id.rain_arena
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = context.getString(R.string.rain_arena_description)
    }

    fun updateTargets(currentTargets: List<RainTarget>, typed: String, countdownValue: Int? = null) {
        targets.clear()
        targets.addAll(currentTargets)
        typedAnswer = LearningContent.normalizeAnswer(typed)
        countdown = countdownValue
        val nextIds = currentTargets.mapTo(mutableSetOf(), RainTarget::instanceId)
        if (nextIds != activeIds) {
            activeIds = nextIds
            contentDescription = if (currentTargets.isEmpty()) {
                context.getString(R.string.rain_arena_description)
            } else {
                currentTargets.joinToString(", ") { it.item.sourceText }
            }
        }
        labelCache.keys.retainAll(nextIds)
        invalidate()
    }

    fun celebrate(targetId: Long, score: Int) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        targetBounds[targetId]?.let { bounds ->
            effects += Effect(bounds.centerX(), bounds.centerY(), SystemClock.elapsedRealtime(), EffectKind.HIT, score)
        }
        announceForAccessibility(context.getString(R.string.rain_hit))
        invalidate()
    }

    fun showWrongAnswer() {
        wrongUntilMs = SystemClock.elapsedRealtime() + 260L
        announceForAccessibility(context.getString(R.string.rain_miss))
        invalidate()
    }

    fun splash(targetId: Long) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        targetBounds[targetId]?.let { bounds ->
            effects += Effect(bounds.centerX(), height - dp(12f), SystemClock.elapsedRealtime(), EffectKind.SPLASH)
        }
        invalidate()
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        backgroundDestination.set(0, 0, width, height)
        labelCache.clear()
        if (backgroundBitmap == null || width == 0 || height == 0) return
        val bitmapRatio = backgroundBitmap.width.toFloat() / backgroundBitmap.height
        val viewRatio = width.toFloat() / height
        if (bitmapRatio > viewRatio) {
            val sourceWidth = (backgroundBitmap.height * viewRatio).toInt()
            val left = (backgroundBitmap.width - sourceWidth) / 2
            backgroundSource.set(left, 0, left + sourceWidth, backgroundBitmap.height)
        } else {
            val sourceHeight = (backgroundBitmap.width / viewRatio).toInt()
            val top = ((backgroundBitmap.height - sourceHeight) * 0.62f).toInt()
                .coerceIn(0, backgroundBitmap.height - sourceHeight)
            backgroundSource.set(0, top, backgroundBitmap.width, top + sourceHeight)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        backgroundBitmap?.let { canvas.drawBitmap(it, backgroundSource, backgroundDestination, null) }
            ?: canvas.drawColor(Color.rgb(29, 57, 66))
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), shadePaint)
        val now = SystemClock.elapsedRealtime()
        drawRain(canvas, now)
        drawTargets(canvas)
        drawEffects(canvas, now)
        countdown?.let { value ->
            if (value > 0) canvas.drawText(value.toString(), width / 2f, height * 0.52f, countdownPaint)
        }
        if (now < wrongUntilMs) {
            effectPaint.color = Color.argb(190, 246, 105, 92)
            effectPaint.strokeWidth = dp(3f)
            canvas.drawRoundRect(dp(3f), dp(3f), width - dp(3f), height - dp(3f), dp(10f), dp(10f), effectPaint)
        }
    }

    private fun drawRain(canvas: Canvas, now: Long) {
        val sceneHeight = height.coerceAtLeast(1)
        for (index in 0 until 30) {
            val x = ((index * 83 + 17) % width.coerceAtLeast(1)).toFloat()
            val y = ((index * 137L + now / 7L) % sceneHeight).toFloat()
            val length = dp(8f + index % 5)
            canvas.drawLine(x, y, x - dp(2f), y + length, rainPaint)
        }
    }

    private fun drawTargets(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val maxCardWidth = (width - dp(24f)).coerceAtLeast(dp(100f))
        val horizontalPadding = dp(12f)
        val verticalPadding = dp(8f)
        val lanes = floatArrayOf(width * 0.2f, width * 0.5f, width * 0.8f)
        targets.forEach { target ->
            val label = labelCache.getOrPut(target.instanceId) {
                createLabel(target.item.sourceText, maxCardWidth, horizontalPadding, verticalPadding)
            }
            val lane = (target.x * lanes.size).toInt().coerceIn(0, lanes.lastIndex)
            val left = (lanes[lane] - label.width / 2f).coerceIn(dp(12f), width - dp(12f) - label.width)
            val top = (dp(8f) + target.y * (height - label.height - dp(20f)).coerceAtLeast(1f))
                .coerceAtMost(height - label.height - dp(8f))
            val bounds = targetBounds.getOrPut(target.instanceId) { RectF() }
            bounds.set(left, top, left + label.width, top + label.height)
            canvas.drawRoundRect(bounds, dp(7f), dp(7f), cardPaint)
            val isMatch = typedAnswer.isNotEmpty() && LearningContent.matchesPrefix(target.item, practiceMode, typedAnswer)
            canvas.drawRoundRect(bounds, dp(7f), dp(7f), if (isMatch) activeBorderPaint else cardBorderPaint)
            canvas.save()
            canvas.translate(left + horizontalPadding, top + verticalPadding)
            label.layout.draw(canvas)
            canvas.restore()
        }
    }

    private fun createLabel(text: String, maxCardWidth: Float, horizontalPadding: Float, verticalPadding: Float): TargetLabel {
        val maxTextWidth = (maxCardWidth - horizontalPadding * 2).toInt()
        val measuredWidth = textPaint.measureText(text).toInt().coerceAtLeast(dp(44f).toInt())
        val layoutWidth = measuredWidth.coerceAtMost(maxTextWidth)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, layoutWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .setLineSpacing(0f, 1f)
            .build()
        return TargetLabel(
            layout = layout,
            width = (layoutWidth + horizontalPadding * 2).coerceAtMost(maxCardWidth),
            height = max(dp(44f), layout.height + verticalPadding * 2),
        )
    }

    private fun drawEffects(canvas: Canvas, now: Long) {
        effects.removeAll { effect -> now - effect.startedAtMs > if (effect.kind == EffectKind.HIT) 430L else 300L }
        effects.forEach { effect ->
            val age = now - effect.startedAtMs
            val progress = when (effect.kind) {
                EffectKind.HIT -> (age / 430f).coerceIn(0f, 1f)
                EffectKind.SPLASH -> (age / 300f).coerceIn(0f, 1f)
            }
            val alpha = ((1f - progress) * 255).toInt()
            effectPaint.color = when (effect.kind) {
                EffectKind.HIT -> Color.argb(alpha, 116, 236, 209)
                EffectKind.SPLASH -> Color.argb(alpha, 185, 226, 236)
            }
            effectPaint.strokeWidth = dp(2f)
            val radius = dp(8f + 26f * progress)
            canvas.drawOval(effect.x - radius, effect.y - radius * 0.35f, effect.x + radius, effect.y + radius * 0.35f, effectPaint)
            if (effect.kind == EffectKind.HIT) {
                for (index in 0 until 8) {
                    val angle = (PI * 2 * index / 8).toFloat()
                    val distance = dp(10f + 34f * progress)
                    val px = effect.x + cos(angle) * distance
                    val py = effect.y + sin(angle) * distance
                    canvas.drawCircle(px, py, dp(2.5f * (1f - progress)), effectPaint)
                }
                scorePaint.alpha = alpha
                canvas.drawText("+${effect.score}", effect.x, effect.y - dp(14f + 18f * progress), scorePaint)
            }
        }
    }

    private fun dp(value: Float): Float = value * density
    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity
}
