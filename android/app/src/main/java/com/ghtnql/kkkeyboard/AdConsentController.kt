package com.ghtnql.kkkeyboard

import androidx.activity.ComponentActivity
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

/**
 * App-only UMP consent controller. Never initializes or starts ads.
 *
 * Contract: call [refresh] once per app launch, then read [canRequestAds]
 * on every ad request. Privacy options UI only when [privacyOptionsRequired].
 * No locally persisted consent boolean; [canRequestAds] is the SDK status.
 */
class AdConsentController(private val activity: ComponentActivity) {

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(activity)

    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()

    val privacyOptionsRequired: Boolean
        get() =
            consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    var onChanged: (() -> Unit)? = null

    private var closed = false
    private var requestedThisLaunch = false
    private var presentingForm = false

    /**
     * Requests consent info update once per current launch, then presents the
     * required consent form. Notifies [onChanged] on success, failure, and
     * form dismissal (failure path reports the SDK prior status).
     */
    fun refresh() {
        if (closed || requestedThisLaunch) return
        if (!isActivityUsable()) return
        requestedThisLaunch = true
        try {
            val params = ConsentRequestParameters.Builder().build()
            consentInformation.requestConsentInfoUpdate(
                activity,
                params,
                {
                    // Success: present form if required, then notify.
                    presentIfRequired()
                },
                {
                    // Failure: still notify so host re-reads SDK prior status.
                    notifyChanged()
                },
            )
        } catch (_: Exception) {
            presentingForm = false
            notifyChanged()
        } catch (_: Error) {
            presentingForm = false
            notifyChanged()
        }
    }

    /**
     * Shows the privacy options form. No-op unless requirement status is
     * REQUIRED and the activity can host UI.
     */
    fun showPrivacyOptions() {
        if (closed || presentingForm) return
        if (!privacyOptionsRequired) return
        if (!isActivityUsable()) return
        try {
            presentingForm = true
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { _: FormError? ->
                presentingForm = false
                notifyChanged()
            }
        } catch (_: Exception) {
            presentingForm = false
            notifyChanged()
        } catch (_: Error) {
            presentingForm = false
            notifyChanged()
        }
    }

    /** Stops callbacks; no further form presentation or notifications. */
    fun close() {
        closed = true
        presentingForm = false
        onChanged = null
    }

    private fun presentIfRequired() {
        if (closed || !isActivityUsable()) {
            notifyChanged()
            return
        }
        if (presentingForm) return
        try {
            presentingForm = true
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _: FormError? ->
                presentingForm = false
                notifyChanged()
            }
        } catch (_: Exception) {
            presentingForm = false
            notifyChanged()
        } catch (_: Error) {
            presentingForm = false
            notifyChanged()
        }
    }

    private fun isActivityUsable(): Boolean {
        if (closed) return false
        if (activity.isFinishing) return false
        if (activity.isDestroyed) return false
        return true
    }

    private fun notifyChanged() {
        if (closed) return
        onChanged?.invoke()
    }
}
