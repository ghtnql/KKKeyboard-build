package com.ghtnql.kkkeyboard

import android.content.SharedPreferences
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.ghtnql.kkkeyboard.sharedui.GameEndAdPolicy

internal interface GameEndAdGateway {
    fun prepare()
    fun show(onShown: () -> Unit, onFinished: () -> Unit): Boolean
    fun dispose()
}

internal class GameEndAdController(
    private val preferences: SharedPreferences,
    private val gateway: GameEndAdGateway,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        internal const val KEY_COMPLETED_ROUNDS = "completed_rounds"
        internal const val KEY_LAST_FULLSCREEN_AT = "last_fullscreen_at"
    }

    private val policy: GameEndAdPolicy
    private var pendingPresentation: Any? = null
    private var pendingShown = false
    private var pendingFinish: (() -> Unit)? = null
    private var disposed = false

    init {
        val now = nowMillis()
        val rounds = preferences.getInt(KEY_COMPLETED_ROUNDS, 0).coerceIn(0, GameEndAdPolicy.MAX_ROUNDS)
        val hasTimestamp = preferences.contains(KEY_LAST_FULLSCREEN_AT)
        val last = if (hasTimestamp) preferences.getLong(KEY_LAST_FULLSCREEN_AT, now) else now
        policy = GameEndAdPolicy(
            completedRoundsSinceAd = rounds,
            lastFullscreenAtMillis = last
        )
        if (!hasTimestamp) {
            preferences.edit().putLong(KEY_LAST_FULLSCREEN_AT, policy.lastFullscreenAtMillis).apply()
        }
    }

    fun prepare() {
        if (disposed) return
        gateway.prepare()
    }

    fun onCompletedRound(onFinished: () -> Unit): Boolean {
        if (disposed) return false
        if (pendingPresentation != null) return false
        policy.onCompletedRound()
        preferences.edit().putInt(KEY_COMPLETED_ROUNDS, policy.completedRoundsSinceAd).apply()
        val now = nowMillis()
        if (!policy.canShow(now)) {
            gateway.prepare()
            return false
        }
        val presentation = Any()
        pendingPresentation = presentation
        pendingShown = false
        pendingFinish = onFinished
        val accepted: Boolean = try {
            gateway.show(
                onShown = {
                    if (!disposed && pendingPresentation === presentation && !pendingShown) {
                        pendingShown = true
                        noteFullscreenShown()
                    }
                },
                onFinished = { finishPending(presentation) }
            )
        } catch (_: Exception) {
            false
        }
        if (!accepted) {
            if (pendingPresentation === presentation) {
                pendingFinish = null
                pendingPresentation = null
                pendingShown = false
            }
            gateway.prepare()
            return false
        }
        return true
    }

    private fun finishPending(presentation: Any? = pendingPresentation) {
        if (pendingPresentation !== presentation) return
        val callback = pendingFinish
        pendingFinish = null
        pendingPresentation = null
        pendingShown = false
        callback?.invoke()
    }

    fun noteFullscreenShown() {
        if (disposed) return
        val now = nowMillis()
        policy.onFullscreenShown(now)
        preferences.edit()
            .putInt(KEY_COMPLETED_ROUNDS, policy.completedRoundsSinceAd)
            .putLong(KEY_LAST_FULLSCREEN_AT, policy.lastFullscreenAtMillis)
            .apply()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        try {
            gateway.dispose()
        } catch (_: Throwable) {
            // Still finish the pending round when the SDK fails during disposal.
        } finally {
            finishPending()
        }
    }
}

internal class GoogleGameEndAdGateway(
    private val activity: ComponentActivity,
    private val adUnitId: String,
    private val canRequestAds: () -> Boolean = { true },
) : GameEndAdGateway {

    companion object {
        internal const val EXPIRY_MILLIS: Long = 3_600_000L
    }

    private var generation = 0L
    private var cachedAd: InterstitialAd? = null
    private var cachedAtElapsed: Long = 0L
    private var loading = false
    private var showing = false
    private var disposed = false
    private var pendingFinish: (() -> Unit)? = null

    fun invalidate() {
        generation++
        cachedAd = null
        loading = false
    }

    override fun prepare() {
        if (disposed) return
        if (!canRequestAds()) { invalidate(); return }
        if (adUnitId.isBlank()) return
        if (loading) return
        if (showing) return
        val cached = cachedAd
        if (cached != null) {
            if (SystemClock.elapsedRealtime() - cachedAtElapsed < EXPIRY_MILLIS) return
            cachedAd = null
        }
        loading = true
        val requestGeneration = generation
        try {
            MobileAds.initialize(activity)
            val request: AdRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                activity,
                adUnitId,
                request,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        if (disposed || requestGeneration != generation || !canRequestAds()) return
                        loading = false
                        cachedAd = ad
                        cachedAtElapsed = SystemClock.elapsedRealtime()
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        if (requestGeneration != generation) return
                        loading = false
                    }
                }
            )
        } catch (_: Exception) {
            loading = false
        }
    }

    override fun show(onShown: () -> Unit, onFinished: () -> Unit): Boolean {
        if (disposed) return false
        if (!canRequestAds()) { invalidate(); return false }
        val ad = cachedAd ?: return false
        if (SystemClock.elapsedRealtime() - cachedAtElapsed >= EXPIRY_MILLIS) {
            cachedAd = null
            return false
        }
        if (showing) return false
        if (activity.lifecycle.currentState != Lifecycle.State.RESUMED) return false
        if (activity.isFinishing || activity.isDestroyed) return false
        cachedAd = null
        var done = false
        var shown = false
        fun finishOnce() {
            if (!done) {
                done = true
                showing = false
                val cb = pendingFinish
                pendingFinish = null
                cb?.invoke()
            }
        }
        showing = true
        pendingFinish = onFinished
        try {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdShowedFullScreenContent() {
                    if (done || disposed || shown) return
                    shown = true
                    try {
                        onShown()
                    } catch (_: Exception) {
                    }
                }

                override fun onAdDismissedFullScreenContent() {
                    finishOnce()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    finishOnce()
                }
            }
            ad.show(activity)
        } catch (_: Throwable) {
            finishOnce()
        }
        return true
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        cachedAd = null
        loading = false
        val cb = pendingFinish
        pendingFinish = null
        showing = false
        try {
            cb?.invoke()
        } catch (_: Throwable) {
        }
    }
}
