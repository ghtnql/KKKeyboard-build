# BM placement experiment

Source: Google Drive `03_BM` (2026-09-10). This branch explores where optional
monetization can fit in the **main app**. It does not enable payments or ads.
The implemented Seoul Day/Night themes use one shared 24-hour reward unlock.
Selecting a locked Seoul theme in the main app offers a rewarded ad. A reward
callback selects the theme and starts the 24-hour period. During that period
both Seoul themes can be selected without another ad. At expiry Seoul Day
falls back to Light and Seoul Night to Dark, and both lock again.

## Placement decisions

| Surface | Entry point | Reason | Guardrail |
| --- | --- | --- | --- |
| Home | One compact Premium link after the learning modes | Users can choose to explore benefits outside the keyboard setup flow | No modal or first-launch prompt |
| Session result | Optional reward preview after the score and next action | The session is complete, so a future rewarded ad would not interrupt input | No ad or XP grant until an ad provider confirms completion |
| Progress | Detailed statistics preview | Shows the value near the data it would expand | Existing basic progress stays free |
| Settings | Premium information link | Gives users a stable place to find future purchase and restore options | Keyboard settings remain free of prompts |

The keyboard extension and Android input method never load ad SDKs or show ads.
Only the main app loads a rewarded ad after an explicit tap. Google sample ad
units are enabled for Debug; release builds have no ad request without
production configuration. Android release accepts validated
`KK_ADMOB_ANDROID_APP_ID` and `KK_ADMOB_ANDROID_REWARDED_ID` build environment
variables. iOS release ads remain disabled until real IDs and the production
ad path are configured. No typed text or candidate history is sent to the SDK.

## Product path before launch

1. Define the actual Premium benefit and implement it on both platforms. The
   first sensible bundle is app-only detailed learning statistics.
2. Configure product IDs in Play Console and App Store Connect. Add native
   purchase and restore adapters; grant entitlement only after store validation.
3. Persist validated entitlement for the app. If a paid keyboard theme is later
   added, expose only the theme unlock state to the keyboard through the
   platform-approved local store.
4. If rewarded XP is added, grant it only after a verified reward callback,
   with a per-session limit and no ad on onboarding or keyboard setup.
5. Compare entry-point visits, completed purchases/rewards, session exits, and
   keyboard deactivation before raising exposure. Never send typed text or
   candidate history to ad analytics.

No AAB, TestFlight build, or store product is produced by this branch before
the required user APK test.
