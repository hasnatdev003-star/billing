# Integrating the Billing & Subscription Library

This guide explains how to integrate and use the Billing and Subscription library in your Android project.

## 1. Setup Dependency

### Step 1: Add JitPack Repository
Add the JitPack repository to your project-level configuration.

#### Option A: `settings.gradle` (Modern Gradle)
Add the JitPack maven URL to the `dependencyResolutionManagement` block:

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REFS)
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

#### Option B: Project-level `build.gradle` (Legacy Gradle)
Add JitPack to your project-level repositories list:

```groovy
allprojects {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

---

### Step 2: Add Dependency
Add the dependency to your app-level `build.gradle` or `build.gradle.kts`:

#### Groovy (`build.gradle`)
```groovy
dependencies {
    implementation 'com.github.hasnatdev003-star:billing:1.1'
}
```

#### Kotlin DSL (`build.gradle.kts`)
```kotlin
dependencies {
    implementation("com.github.hasnatdev003-star:billing:1.1")
}
```

---

## 2. Configuration

Before launching the subscription screen, configure the required string resources in your app's `strings.xml`.

### Required String Resources
Add the following keys to `res/values/strings.xml` in your application:

```xml
<resources>
    <!-- Google Play Console licensing public key -->
    <string name="subscription_billing_license_key">YOUR_PLAY_CONSOLES_BASE64_PUBLIC_KEY</string>
    
    <!-- Link to your App's Privacy Policy -->
    <string name="subscription_privacy_policy_url">https://yourdomain.com/privacy-policy</string>
</resources>
```

---

## 3. Usage & Integration

### A. Initialize on Startup (Splash Screen)
To ensure the subscription plans are cached and ready to display immediately when the paywall opens, initialize the `SubscriptionRuntimeStore` inside your launcher or splash activity:

#### Kotlin
```kotlin
import com.reusable.subscription.data.local.SubscriptionRuntimeStore

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize/load subscription data from cache
        SubscriptionRuntimeStore.initializeOnSplash(this)
        
        // ... rest of startup code
    }
}
```

#### Java
```java
import com.reusable.subscription.data.local.SubscriptionRuntimeStore;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Initialize/load subscription data from cache
        SubscriptionRuntimeStore.initializeOnSplash(this);
    }
}
```

---

### B. Launching the Subscription Paywall
To present the premium purchase/subscription screen to the user:

#### Kotlin
```kotlin
import android.content.Intent
import com.reusable.subscription.ui.activity.SubscriptionActivity

val intent = Intent(context, SubscriptionActivity::class.java).apply {
    // Optional: Pass whether we are launching from Splash to handle exit flow differently
    putExtra(SubscriptionActivity.EXTRA_FROM_SPLASH, true)
}
context.startActivity(intent)
```

#### Java
```java
import android.content.Intent;
import com.reusable.subscription.ui.activity.SubscriptionActivity;

Intent intent = new Intent(context, SubscriptionActivity.class);
intent.putExtra(SubscriptionActivity.EXTRA_FROM_SPLASH, true);
context.startActivity(intent);
```

---

### C. Checking Entitlement Status
Verify if the user has premium status (active subscription, lifetime license, or ad-removal package).

#### Kotlin
```kotlin
import com.reusable.subscription.domain.manager.SubscriptionEntitlementManager

// Check if user has an active monthly/yearly subscription
val hasActiveSub = SubscriptionEntitlementManager.hasSubscriptionState(context)

// Check if user bought lifetime access
val hasLifetime = SubscriptionEntitlementManager.hasLifetimeState(context)

// General check (Returns true if they have either of the above active)
val isPremium = hasActiveSub || hasLifetime
```

#### Java
```java
import com.reusable.subscription.domain.manager.SubscriptionEntitlementManager;

// Check if user has an active monthly/yearly subscription
boolean hasActiveSub = SubscriptionEntitlementManager.hasSubscriptionState(context);

// Check if user bought lifetime access
boolean hasLifetime = SubscriptionEntitlementManager.hasLifetimeState(context);

// General check
boolean isPremium = hasActiveSub || hasLifetime;
```

---

### D. Interstitial Ad Integration on Paywall Exit
You can register an action to show an interstitial ad when the user closes the subscription screen while exiting a splash setup:

#### Kotlin
```kotlin
import com.reusable.subscription.domain.config.SubscriptionHostConfig

SubscriptionHostConfig.showInterstitialCallback = { activity, onDismiss ->
    // 1. Show your Ad (e.g. AdMob Interstitial)
    // 2. Call onDismiss() inside your ad listener's onAdDismissedFullScreenContent or when failed to load.
    if (myInterstitialAd != null) {
        myInterstitialAd?.show(activity)
        // Ensure onDismiss() is called when ad is dismissed or fails to show
    } else {
        onDismiss()
    }
}
```

---

### E. Dynamic Configuration (License Key & Product/SKU IDs)
If you want to dynamically set or modify the Play Billing license key and product/SKU IDs at runtime (e.g., from a remote config like Firebase Remote Config or an API response) rather than hardcoding them in `strings.xml`, update the properties in `SubscriptionHostConfig` at app startup:

#### Kotlin
```kotlin
import com.reusable.subscription.domain.config.SubscriptionHostConfig

// Dynamic/Programmatic License Key
SubscriptionHostConfig.billingLicenseKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA..."

// Dynamic/Programmatic SKU / Product IDs
SubscriptionHostConfig.productIdMonthly = "my.custom.monthly.sku"
SubscriptionHostConfig.productIdYearly = "my.custom.yearly.sku"
SubscriptionHostConfig.productIdLifetime = "my.custom.lifetime.sku"
```

#### Java
```java
import com.reusable.subscription.domain.config.SubscriptionHostConfig;

// Dynamic/Programmatic License Key
SubscriptionHostConfig.setBillingLicenseKey("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...");

// Dynamic/Programmatic SKU / Product IDs
SubscriptionHostConfig.setProductIdMonthly("my.custom.monthly.sku");
SubscriptionHostConfig.setProductIdYearly("my.custom.yearly.sku");
SubscriptionHostConfig.setProductIdLifetime("my.custom.lifetime.sku");
```

> [!NOTE]
> Make sure to set these values **before** calling `SubscriptionRuntimeStore.initializeOnSplash(context)` or launching the subscription paywall activity to ensure Play Billing queries the correct product details.
