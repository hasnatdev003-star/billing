# Migrate Subscription Paywall to App Module

This plan outlines the exact steps to integrate the reusable subscription paywall directly into an existing `app` module, bypassing the need to include it as a separate Gradle module. It ensures seamless theming and centralized ad handling.

## User Review Required
> [!IMPORTANT]
> Since we are copying code rather than importing a module, you will need to update the Kotlin package declarations (`package com.reusable.subscription...`) to match your host app's package structure (e.g., `package com.yourdomain.app.subscription...`).

## Proposed Changes

### 1. Source Code Migration
Copy the Kotlin source files directly into the host app's source tree.
- **Source:** `subscription/src/main/java/com/reusable/subscription/`
- **Destination:** `app/src/main/java/com/[your_app_package]/subscription/`
- **Refactoring:** Update all import statements and package declarations in the copied files to reflect the new `[your_app_package]` directory.

### 2. Layouts and Drawables Migration
Copy the visual resources into the host app.
- **Layouts:** Copy all XML files from `subscription/src/main/res/layout/` to `app/src/main/res/layout/`.
- **Drawables:** Copy all XML files from `subscription/src/main/res/drawable/` to `app/src/main/res/drawable/`.
- **Theming Notes:** Because the paywall was strictly refactored to use `?attr/` references (e.g., `?attr/colorPrimary`, `?attr/colorSurface`), these layouts will **automatically inherit** the host app's Material 3 color scheme. 

### 3. Resource Merging (Strings & Colors)
We will merge texts but intentionally ignore colors to respect the host app's theme.
- **Strings:** DO NOT copy a separate `strings.xml` file. Instead, copy the string items from `subscription/src/main/res/values/strings.xml` and paste them directly into the host app's `app/src/main/res/values/strings.xml`.
- **Colors:** DO NOT copy any `colors.xml` file. The copied layouts and drawables do not require it.

### 4. Dependencies and Manifest
- **Manifest:** Copy the `<activity>` declarations from the subscription module's `AndroidManifest.xml` (specifically `SubscriptionActivity` and `DisplayTermOfUseActivity`) into the host app's `AndroidManifest.xml`.
- **Dependencies:** Ensure the host app's `build.gradle` includes the Google Play Billing Library (`com.android.billingclient:billing-ktx:6.x.x` or newer) and DataStore dependencies.

### 5. Interstitial Ad Integration
The paywall natively supports triggering a host-level interstitial ad upon exiting the paywall from a splash screen.
- **Setup:** In your Application class or Splash Activity, bind your ad logic to the callback:
  ```kotlin
  SubscriptionHostConfig.showInterstitialCallback = { activity, onAdDismissed ->
      if (yourAppInterstitialAd != null) {
          yourAppInterstitialAd.fullScreenContentCallback = object: FullScreenContentCallback() {
              override fun onAdDismissedFullScreenContent() {
                  onAdDismissed() // Signals the paywall to finish closing and route to MainActivity
              }
          }
          yourAppInterstitialAd.show(activity)
      } else {
          onAdDismissed()
      }
  }
  ```
- **Remote Config:** Bind your Firebase Remote Config boolean to `SubscriptionConstant.showInterstitialOnSplashExit` to remotely enable/disable the ad.

## Verification Plan
1. **Compile:** Run `./gradlew :app:assembleDebug` to verify no package or unresolved resource errors exist.
2. **Visual Check:** Launch the paywall to ensure it perfectly matches the host app's colors (Light/Dark mode) without missing attributes.
3. **Ad Check:** Open the paywall via Splash (`EXTRA_FROM_SPLASH`), dismiss it, and verify the interstitial callback successfully fires before navigating to the Main screen.
