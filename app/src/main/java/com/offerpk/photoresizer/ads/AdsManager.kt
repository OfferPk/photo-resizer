package com.offerpk.photoresizer.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.offerpk.photoresizer.core.AdGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Consent-first ads controller:
 *  1. UMP consent info update + consent form if required,
 *  2. only then MobileAds.initialize() and ad requests (if canRequestAds()),
 *  3. one preloaded interstitial, shown only when [AdGate] allows it.
 */
object AdsManager {
    private const val TAG = "AdsManager"

    private val _canShowAds = MutableStateFlow(false)
    /** True once consent has been gathered (or is not required) and the SDK is initialised. */
    val canShowAds: StateFlow<Boolean> = _canShowAds

    private val _privacyOptionsRequired = MutableStateFlow(false)
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired

    private val initStarted = AtomicBoolean(false)
    private var consentInfo: ConsentInformation? = null
    private var interstitial: InterstitialAd? = null
    private var loadingInterstitial = false

    val gate = AdGate(AdConfig.INTERSTITIAL_MIN_INTERVAL_MS)

    fun gatherConsentAndInit(activity: Activity) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        consentInfo = info
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .apply {
                if (com.offerpk.photoresizer.BuildConfig.DEBUG) {
                    setConsentDebugSettings(ConsentDebugSettings.Builder(activity).build())
                }
            }
            .build()
        info.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                formError?.let { Log.w(TAG, "Consent form: ${it.errorCode} ${it.message}") }
                updatePrivacyOptions(info)
                if (info.canRequestAds()) initializeAds(activity.applicationContext)
            }
        }, { err ->
            Log.w(TAG, "Consent info update failed: ${err.errorCode} ${err.message}")
            updatePrivacyOptions(info)
            // Per Google guidance, ads may still be requested if consent was gathered earlier.
            if (info.canRequestAds()) initializeAds(activity.applicationContext)
        })
        // Consent from a previous session can be used right away.
        if (info.canRequestAds()) initializeAds(activity.applicationContext)
    }

    private fun updatePrivacyOptions(info: ConsentInformation) {
        _privacyOptionsRequired.value =
            info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    fun showPrivacyOptions(activity: Activity, onDone: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { err ->
            err?.let { Log.w(TAG, "Privacy options: ${it.message}") }
            consentInfo?.let { updatePrivacyOptions(it) }
            if (consentInfo?.canRequestAds() == true) initializeAds(activity.applicationContext)
            onDone()
        }
    }

    private fun initializeAds(context: Context) {
        if (!initStarted.compareAndSet(false, true)) return
        Thread {
            MobileAds.initialize(context) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    _canShowAds.value = true
                    preloadInterstitial(context)
                }
            }
        }.start()
    }

    private fun preloadInterstitial(context: Context) {
        if (!_canShowAds.value || interstitial != null || loadingInterstitial) return
        loadingInterstitial = true
        InterstitialAd.load(context, AdConfig.INTERSTITIAL_ID, AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad; loadingInterstitial = false
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null; loadingInterstitial = false
                    Log.i(TAG, "Interstitial failed to load: ${error.message}")
                }
            })
    }

    /** Records a successful export (save or share). */
    fun onExportSucceeded() = gate.onExportSucceeded()

    /**
     * Called at the natural break after an export (leaving the result screen).
     * Shows the preloaded interstitial only if the gate allows it; then runs [onFinished].
     */
    fun maybeShowInterstitialAfterExport(activity: Activity, onFinished: () -> Unit) {
        val ad = interstitial
        if (!_canShowAds.value || ad == null || !gate.consumeShouldShow()) {
            if (_canShowAds.value) preloadInterstitial(activity.applicationContext)
            onFinished(); return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() { gate.onInterstitialShown() }
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                preloadInterstitial(activity.applicationContext)
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                interstitial = null
                preloadInterstitial(activity.applicationContext)
            }
        }
        interstitial = null
        onFinished() // navigate underneath; the ad covers the transition
        ad.show(activity)
    }
}
