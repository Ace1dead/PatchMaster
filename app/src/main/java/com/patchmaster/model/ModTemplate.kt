package com.patchmaster.model

data class ModTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: Category,
    val complexity: Complexity,
    val requiresFullDecompile: Boolean = true,
    val actions: List<ModAction> = emptyList(),
    val detectionPatterns: List<String> = emptyList(),
    val instructions: String = "",
    val riskLevel: RiskLevel = RiskLevel.MEDIUM
) {
    enum class Category {
        ADS, PREMIUM, LICENSE, DEBUG, SECURITY, PERMISSIONS, TWEAKS, CUSTOM
    }

    enum class Complexity {
        EASY, MEDIUM, HARD, EXPERT
    }

    enum class RiskLevel {
        LOW, MEDIUM, HIGH, BRICK_RISK
    }
}

object ModTemplateLibrary {
    val templates: List<ModTemplate> by lazy { buildTemplates() }

    fun findById(id: String): ModTemplate? = templates.find { it.id == id }

    fun findByCategory(category: ModTemplate.Category): List<ModTemplate> =
        templates.filter { it.category == category }

    fun search(query: String): List<ModTemplate> {
        val q = query.lowercase()
        return templates.filter {
            it.name.lowercase().contains(q) ||
            it.description.lowercase().contains(q) ||
            it.category.name.lowercase().contains(q)
        }
    }

    private fun buildTemplates(): List<ModTemplate> = listOf(
        // === ADS ===
        ModTemplate(
            id = "ads_remove_all", name = "Remove All Ads",
            description = "Removes AdMob, Facebook, Unity Ads, IronSource, AppLovin, StartApp, Vungle",
            category = ModTemplate.Category.ADS, complexity = ModTemplate.Complexity.EASY,
            riskLevel = ModTemplate.RiskLevel.LOW,
            detectionPatterns = listOf("ad", "AdView", "InterstitialAd", "RewardedVideoAd", "Admob", "com.google.android.gms.ads"),
            actions = listOf(
                ModAction.ComponentDisable("com.google.android.gms.ads.AdActivity"),
                ModAction.ComponentDisable("com.facebook.ads.InterstitialAdActivity"),
                ModAction.ComponentDisable("com.facebook.ads.AudienceNetworkActivity"),
                ModAction.ComponentDisable("com.unity3d.ads.adunit.AdUnitActivity"),
                ModAction.ComponentDisable("com.unity3d.ads.adunit.AdUnitSoftwareActivity"),
                ModAction.ComponentDisable("com.applovin.adview.AppLovinAdView"),
                ModAction.ComponentDisable("com.ironsource.sdk.controller.ControllerActivity"),
                ModAction.ComponentDisable("com.startapp.android.publish.ads.list3d.List3DActivity"),
                ModAction.ComponentDisable("com.vungle.warren.ui.VungleActivity")
            )
        ),
        ModTemplate(
            id = "ads_remove_google", name = "Remove Google Ads (AdMob)",
            description = "Removes only Google Mobile Ads / AdMob components and permissions",
            category = ModTemplate.Category.ADS, complexity = ModTemplate.Complexity.EASY,
            detectionPatterns = listOf("AdMob", "com.google.android.gms.ads", "Google Mobile Ads"),
            actions = listOf(
                ModAction.ComponentDisable("com.google.android.gms.ads.AdActivity"),
                ModAction.ComponentDisable("com.google.android.gms.ads.InterstitialActivity"),
                ModAction.PermissionRemove("com.google.android.gms.permission.AD_ID")
            )
        ),
        ModTemplate(
            id = "ads_bypass_rewarded", name = "Bypass Rewarded Video Check",
            description = "Patches rewarded video ad checks to always return success",
            category = ModTemplate.Category.ADS, complexity = ModTemplate.Complexity.HARD,
            detectionPatterns = listOf("onRewarded", "onAdRewarded", "RewardedVideoAd", "isRewarded"),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.MEDIUM,
            instructions = "Search for onRewarded/onAdRewarded callback methods and force them to call onReward() immediately"
        ),

        // === PREMIUM ===
        ModTemplate(
            id = "premium_force_true", name = "Force Premium/Pro (Method Patch)",
            description = "Forces isPremium(), isPro(), isPurchased() methods to return true",
            category = ModTemplate.Category.PREMIUM, complexity = ModTemplate.Complexity.MEDIUM,
            detectionPatterns = listOf("isPremium", "isPro", "isPurchased", "isVip", "isUnlocked", "hasSubscription"),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.LOW,
            instructions = "Find methods returning boolean for premium/pro/purchased status and force-return true"
        ),
        ModTemplate(
            id = "premium_iap_patch", name = "IAP Purchase Patch",
            description = "Patches in-app billing to consider all items as purchased",
            category = ModTemplate.Category.PREMIUM, complexity = ModTemplate.Complexity.HARD,
            detectionPatterns = listOf("BillingClient", "queryPurchases", "querySkuDetails", "purchasesUpdated", "handlePurchase", "verifyPurchase"),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.MEDIUM,
            instructions = "Patch onPurchasesUpdated to immediately call acknowledgePurchase. NOP verifyPurchase/signature checks."
        ),
        ModTemplate(
            id = "premium_lvl_bypass", name = "LVL License Bypass",
            description = "Bypasses Google Play License Verification (LVL)",
            category = ModTemplate.Category.PREMIUM, complexity = ModTemplate.Complexity.HARD,
            detectionPatterns = listOf("LicenseChecker", "LVL", "ServerManagedPolicy", "validateLicense", "checkAccess"),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.MEDIUM,
            instructions = "NOP checkAccess/validateLicense methods. Force allow() in LicenseCheckerCallback."
        ),
        ModTemplate(
            id = "premium_subscription_bypass", name = "Subscription Bypass",
            description = "Bypasses subscription checks, forces all subs as active",
            category = ModTemplate.Category.PREMIUM, complexity = ModTemplate.Complexity.HARD,
            detectionPatterns = listOf("isSubscriptionActive", "hasActiveSubscription", "subscriptionExpiry", "sub_status"),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.HIGH,
            instructions = "Find subscription check methods, force return true/long expiry date"
        ),

        // === LICENSE ===
        ModTemplate(
            id = "license_signature_bypass", name = "Signature Verification Bypass",
            description = "Bypasses APK signature verification checks (protection against tampering)",
            category = ModTemplate.Category.LICENSE, complexity = ModTemplate.Complexity.EXPERT,
            detectionPatterns = listOf("signature", "getPackageManager", "GET_SIGNATURES", "signing", "verifySig"),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.HIGH,
            instructions = "Find signature check methods. Replace signature comparison with forced-equal (const/4 v0, 0x1)."
        ),
        ModTemplate(
            id = "license_crack", name = "License Crack (Generic)",
            description = "Generic license verification bypass - searches for common license patterns",
            category = ModTemplate.Category.LICENSE, complexity = ModTemplate.Complexity.MEDIUM,
            detectionPatterns = listOf("license", "crack", "cracked", "illegal", "unauthorized"),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.MEDIUM,
            instructions = "Search for strings containing 'license', 'crack', 'illegal'. Find methods using these strings and patch them."
        ),
        ModTemplate(
            id = "license_remove_trial", name = "Remove Trial Limitations",
            description = "Removes trial period, demo mode, and time limitations",
            category = ModTemplate.Category.LICENSE, complexity = ModTemplate.Complexity.MEDIUM,
            detectionPatterns = listOf("trial", "demo", "expir", "time_limit", "days_left", "remaining_days"),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.MEDIUM,
            instructions = "Find trial/expiry check methods. Force them to return max values or bypass checks."
        ),

        // === DEBUG ===
        ModTemplate(
            id = "debug_enable", name = "Enable Debug Mode",
            description = "Makes APK debuggable, enables backup and native lib extraction",
            category = ModTemplate.Category.DEBUG, complexity = ModTemplate.Complexity.EASY,
            detectionPatterns = listOf(),
            requiresFullDecompile = false,
            riskLevel = ModTemplate.RiskLevel.LOW,
            actions = listOf(
                ModAction.ManifestEdit("android:debuggable", "true", ActionType.ADD),
                ModAction.ManifestEdit("android:allowBackup", "true", ActionType.ADD),
                ModAction.ManifestEdit("android:extractNativeLibs", "true", ActionType.ADD)
            )
        ),
        ModTemplate(
            id = "debug_ssl_bypass", name = "Bypass SSL Pinning",
            description = "Bypasses SSL certificate pinning and enables cleartext traffic",
            category = ModTemplate.Category.DEBUG, complexity = ModTemplate.Complexity.EASY,
            detectionPatterns = listOf("ssl", "pinning", "certificate", "NetworkSecurityConfig"),
            requiresFullDecompile = false,
            riskLevel = ModTemplate.RiskLevel.MEDIUM,
            actions = listOf(
                ModAction.ManifestEdit("android:networkSecurityConfig", "@xml/network_security_config", ActionType.ADD)
            ),
            instructions = "Also need to create res/xml/network_security_config.xml trusting user certs"
        ),
        ModTemplate(
            id = "debug_log_enable", name = "Enable Logcat Logging",
            description = "Injects Log.d calls into key methods and enables verbose logging",
            category = ModTemplate.Category.DEBUG, complexity = ModTemplate.Complexity.MEDIUM,
            detectionPatterns = listOf("Log", "isLoggable"),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.LOW,
            instructions = "Remove isLoggable checks. Inject Log.d in key entry points."
        ),

        // === SECURITY ===
        ModTemplate(
            id = "sec_disable_root_check", name = "Disable Root Detection",
            description = "Disables root/jailbreak detection mechanisms",
            category = ModTemplate.Category.SECURITY, complexity = ModTemplate.Complexity.MEDIUM,
            detectionPatterns = listOf("root", "su", "superuser", "detect", "tamper", "deviceRooted", "isRooted"),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.MEDIUM,
            instructions = "Find isRooted() or root detection methods, NOP them or force return false"
        ),
        ModTemplate(
            id = "sec_disable_emulator_check", name = "Disable Emulator Detection",
            description = "Disables emulator detection to run apps on emulators",
            category = ModTemplate.Category.SECURITY, complexity = ModTemplate.Complexity.MEDIUM,
            detectionPatterns = listOf("emulator", "bluestacks", "genymotion", "isEmulator", "isRunningInEmulator"),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.LOW,
            instructions = "NOP isEmulator() methods, force return false"
        ),
        ModTemplate(
            id = "sec_disable_anti_hooking", name = "Disable Anti-Hooking",
            description = "Disables Frida/Xposed/Substrate detection",
            category = ModTemplate.Category.SECURITY, complexity = ModTemplate.Complexity.HARD,
            detectionPatterns = listOf("frida", "xposed", "substrate", "hooking", "inject", "detect"),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.MEDIUM,
            instructions = "Find anti-hooking detection methods and disable them"
        ),
        ModTemplate(
            id = "sec_disable_google_play_services_check", name = "Disable Google Play Services Check",
            description = "Bypasses Google Play Services requirement",
            category = ModTemplate.Category.SECURITY, complexity = ModTemplate.Complexity.MEDIUM,
            detectionPatterns = listOf("GooglePlayServices", "isGooglePlayServicesAvailable", "googleplayservices"),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.MEDIUM,
            instructions = "NOP isGooglePlayServicesAvailable() or force return SUCCESS"
        ),

        // === PERMISSIONS ===
        ModTemplate(
            id = "perm_remove_internet", name = "Remove Internet Permission",
            description = "Removes INTERNET permission from the APK",
            category = ModTemplate.Category.PERMISSIONS, complexity = ModTemplate.Complexity.EASY,
            detectionPatterns = listOf(),
            requiresFullDecompile = false,
            riskLevel = ModTemplate.RiskLevel.LOW,
            actions = listOf(ModAction.PermissionRemove("android.permission.INTERNET"))
        ),
        ModTemplate(
            id = "perm_remove_all", name = "Remove All Permissions",
            description = "Removes every permission from the APK",
            category = ModTemplate.Category.PERMISSIONS, complexity = ModTemplate.Complexity.EASY,
            detectionPatterns = listOf(),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.HIGH,
            instructions = "Removes all uses-permission entries from AndroidManifest.xml. App may crash without permissions."
        ),
        ModTemplate(
            id = "perm_add_custom", name = "Add Custom Permission",
            description = "Adds a specified permission to the APK",
            category = ModTemplate.Category.PERMISSIONS, complexity = ModTemplate.Complexity.EASY,
            detectionPatterns = listOf(),
            requiresFullDecompile = false,
            riskLevel = ModTemplate.RiskLevel.LOW,
            actions = listOf(ModAction.PermissionAdd("REPLACE_WITH_PERMISSION"))
        ),

        // === TWEAKS ===
        ModTemplate(
            id = "tweak_dpi_change", name = "Override Screen DPI",
            description = "Changes the app's reported screen density",
            category = ModTemplate.Category.TWEAKS, complexity = ModTemplate.Complexity.EASY,
            detectionPatterns = listOf(),
            requiresFullDecompile = false,
            riskLevel = ModTemplate.RiskLevel.LOW,
            actions = listOf(
                ModAction.ResourceEdit("screen_dpi", "640", "integer")
            )
        ),
        ModTemplate(
            id = "tweak_remove_analytics", name = "Remove Analytics",
            description = "Removes Firebase/Google Analytics from the app",
            category = ModTemplate.Category.TWEAKS, complexity = ModTemplate.Complexity.MEDIUM,
            detectionPatterns = listOf("Firebase", "GoogleAnalytics", "Analytics", "ga_"),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.LOW,
            instructions = "Remove FirebaseAnalytics calls, NOP logEvent methods, remove GA permissions"
        ),
        ModTemplate(
            id = "tweak_remove_facebook_sdk", name = "Remove Facebook SDK",
            description = "Removes the Facebook SDK integration (often used for tracking)",
            category = ModTemplate.Category.TWEAKS, complexity = ModTemplate.Complexity.MEDIUM,
            detectionPatterns = listOf("Facebook", "com.facebook", "FBAudienceNetwork", "fb_"),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.MEDIUM,
            actions = listOf(
                ModAction.ComponentDisable("com.facebook.FacebookActivity"),
                ModAction.ComponentDisable("com.facebook.CustomTabActivity")
            ),
            instructions = "Remove all com.facebook references from smali and remove Facebook SDK resources"
        ),
        ModTemplate(
            id = "tweak_package_name", name = "Change Package Name",
            description = "Changes the APK's package name (rebranding)",
            category = ModTemplate.Category.TWEAKS, complexity = ModTemplate.Complexity.HARD,
            detectionPatterns = listOf(),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.HIGH,
            instructions = "Change package in AndroidManifest.xml. Rename all smali directories. Update all R references."
        ),
        ModTemplate(
            id = "tweak_inject_toast", name = "Inject Toast Message",
            description = "Injects a Toast message on app startup showing 'Modded by PatchMaster'",
            category = ModTemplate.Category.TWEAKS, complexity = ModTemplate.Complexity.MEDIUM,
            detectionPatterns = listOf(),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.LOW,
            instructions = "Inject invoke-static for Toast.makeText in main activity's onCreate method"
        ),
        ModTemplate(
            id = "tweak_remove_splash", name = "Remove Splash Screen",
            description = "Removes or skips the splash/welcome screen",
            category = ModTemplate.Category.TWEAKS, complexity = ModTemplate.Complexity.MEDIUM,
            detectionPatterns = listOf("Splash", "SplashActivity", "WelcomeActivity"),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.LOW,
            instructions = "Find SplashActivity, change its onCreate to immediately start main activity"
        ),
        ModTemplate(
            id = "tweak_unlimited_energy", name = "Unlimited Energy/Lives",
            description = "Finds and patches energy/lives/coin counters to max values",
            category = ModTemplate.Category.TWEAKS, complexity = ModTemplate.Complexity.HARD,
            detectionPatterns = listOf("energy", "lives", "coin", "gem", "point", "score", "credit"),
            requiresFullDecompile = true,
            riskLevel = ModTemplate.RiskLevel.MEDIUM,
            instructions = "Search for energy/lives/coin related methods. Force them to return max values or prevent decrementing."
        )
    )
}
