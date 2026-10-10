plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val playUploadKeystorePath = System.getenv("ANDROID_UPLOAD_KEYSTORE_PATH")
val playUploadStorePassword = System.getenv("ANDROID_UPLOAD_STORE_PASSWORD")
val playUploadKeyAlias = System.getenv("ANDROID_UPLOAD_KEY_ALIAS")
val playUploadKeyPassword = System.getenv("ANDROID_UPLOAD_KEY_PASSWORD")
val ciVersionCode = System.getenv("ANDROID_VERSION_CODE")?.toIntOrNull()
val admobAppId = System.getenv("KK_ADMOB_ANDROID_APP_ID")?.takeIf { it.matches(Regex("ca-app-pub-[0-9]+~[0-9]+")) }
val rewardedAdUnitId = System.getenv("KK_ADMOB_ANDROID_REWARDED_ID")?.takeIf { it.matches(Regex("ca-app-pub-[0-9]+/[0-9]+")) }
val interstitialAdUnitId = System.getenv("KK_ADMOB_ANDROID_INTERSTITIAL_ID")?.takeIf { it.matches(Regex("ca-app-pub-[0-9]+/[0-9]+")) }
// Public RSA key only; absent configuration fails closed.
val billingLicensePublicKey = providers.gradleProperty("KK_BILLING_LICENSE_PUBLIC_KEY")
    .orElse(providers.environmentVariable("KK_BILLING_LICENSE_PUBLIC_KEY")).orNull
    ?.replace(Regex("\\s+"), "")?.takeIf { it.matches(Regex("[A-Za-z0-9+/=]+")) }.orEmpty()
// Opt-in parallel debug install; default false keeps the Play app identity unchanged.
val parallelDebugInstall = providers.gradleProperty("KK_PARALLEL_DEBUG").orNull == "true"
val hasPlayUploadSigning = listOf(
    playUploadKeystorePath,
    playUploadStorePassword,
    playUploadKeyAlias,
    playUploadKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.ghtnql.kkkeyboard"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ghtnql.kkkeyboard"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode ?: 1
        versionName = "1.0.1"

        buildConfigField("String", "BILLING_LICENSE_PUBLIC_KEY", "\"$billingLicensePublicKey\"")
        buildConfigField("boolean", "MOCK_ADS", "false")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["admobAppId"] = admobAppId ?: "ca-app-pub-3940256099942544~3347511713"
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    signingConfigs {
        if (hasPlayUploadSigning) {
            create("playUpload") {
                storeFile = file(playUploadKeystorePath!!)
                storePassword = playUploadStorePassword
                keyAlias = playUploadKeyAlias
                keyPassword = playUploadKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            // Preserve the Play signing identity: only the debug applicationId is suffixed, never release.
            if (parallelDebugInstall) {
                applicationIdSuffix = ".dev"
                manifestPlaceholders["appLabel"] = "ㅋㅋ키보드 테스트"
            }
            buildConfigField("boolean", "EXPOSE_GAMES", "true")
            buildConfigField("boolean", "MOCK_ADS", "true")
            buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
            buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
        }
        getByName("release") {
            buildConfigField("boolean", "EXPOSE_GAMES", "true")
            buildConfigField("String", "REWARDED_AD_UNIT_ID", "\"${if (admobAppId != null) rewardedAdUnitId.orEmpty() else ""}\"")
            buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", "\"${if (admobAppId != null) interstitialAdUnitId.orEmpty() else ""}\"")
            if (hasPlayUploadSigning) {
                signingConfig = signingConfigs.getByName("playUpload")
            }
        }
        create("feedback") {
            initWith(getByName("release"))
            buildConfigField("boolean", "EXPOSE_GAMES", "false")
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    androidResources {
        noCompress += "wav"
    }

    sourceSets {
        getByName("main").resources.srcDir("../../shared/dictionaries")
        getByName("main").assets.srcDir("../../shared/themes")
        getByName("main").assets.srcDir("../../shared/phrases")
        getByName("debug").assets.srcDir("../../shared/content")
        getByName("debug").assets.srcDir(layout.buildDirectory.dir("generated/gameAudioAssets"))
        getByName("release").assets.srcDir("../../shared/content")
        getByName("release").assets.srcDir(layout.buildDirectory.dir("generated/gameAudioAssets"))
        getByName("test").resources.srcDir("../../shared/test-fixtures")
        getByName("test").resources.srcDir("../../shared/themes")
    }
}

// Release must use real AdMob IDs. Debug keeps mock behavior and needs no real IDs.
// Validates raw environment values (not the filtered placeholders above) so that
// missing or malformed IDs fail here instead of silently falling back to samples.
val validateReleaseAds by tasks.registering {
    doLast {
        val samplePublisher = "3940256099942544"
        val appIdPattern = Regex("ca-app-pub-[0-9]+~[0-9]+")
        val unitIdPattern = Regex("ca-app-pub-[0-9]+/[0-9]+")
        val names = listOf(
            "KK_ADMOB_ANDROID_APP_ID",
            "KK_ADMOB_ANDROID_REWARDED_ID",
            "KK_ADMOB_ANDROID_INTERSTITIAL_ID",
        )
        val values = names.associateWith { System.getenv(it) ?: "" }
        val missing = values.filterValues { it.isEmpty() }.keys
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Missing production AdMob configuration: " +
                    "${missing.joinToString(", ")}. " +
                    "Set KK_ADMOB_ANDROID_APP_ID, KK_ADMOB_ANDROID_REWARDED_ID and " +
                    "KK_ADMOB_ANDROID_INTERSTITIAL_ID environment variables."
            )
        }
        val appId = values.getValue("KK_ADMOB_ANDROID_APP_ID")
        val rewardedId = values.getValue("KK_ADMOB_ANDROID_REWARDED_ID")
        val interstitialId = values.getValue("KK_ADMOB_ANDROID_INTERSTITIAL_ID")
        if (!appIdPattern.matches(appId)) {
            throw GradleException(
                "Invalid KK_ADMOB_ANDROID_APP_ID: expected format ca-app-pub-<digits>~<digits>."
            )
        }
        for ((name, value) in mapOf(
            "KK_ADMOB_ANDROID_REWARDED_ID" to rewardedId,
            "KK_ADMOB_ANDROID_INTERSTITIAL_ID" to interstitialId,
        )) {
            if (!unitIdPattern.matches(value)) {
                throw GradleException(
                    "Invalid $name: expected format ca-app-pub-<digits>/<digits>."
                )
            }
        }
        fun publisherOf(value: String) = value.substringAfter("ca-app-pub-")
            .split("~", "/").firstOrNull().orEmpty()
        val publishers = setOf(
            publisherOf(appId),
            publisherOf(rewardedId),
            publisherOf(interstitialId),
        )
        if (samplePublisher in publishers) {
            throw GradleException(
                "Invalid production AdMob configuration: sample publisher IDs are not allowed " +
                    "in Release. Set KK_ADMOB_ANDROID_APP_ID, KK_ADMOB_ANDROID_REWARDED_ID and " +
                    "KK_ADMOB_ANDROID_INTERSTITIAL_ID to actual operating IDs."
            )
        }
        if (publishers.size != 1) {
            throw GradleException(
                "Invalid production AdMob configuration: KK_ADMOB_ANDROID_APP_ID, " +
                    "KK_ADMOB_ANDROID_REWARDED_ID and KK_ADMOB_ANDROID_INTERSTITIAL_ID " +
                    "must share the same publisher ID."
            )
        }
    }
}

val syncGameAudioAssets by tasks.registering(Sync::class) {
    from("../../shared/audio") {
        include("*.wav")
    }
    into(layout.buildDirectory.dir("generated/gameAudioAssets/audio"))
}

tasks.named("preBuild") {
    dependsOn(syncGameAudioAssets)
}

// Every Release archive/bundle fails before compiling when production IDs are
// missing or invalid. Debug, feedback inspection and unit tests do not depend on this.
// preReleaseBuild is created later by AGP, so hook up lazily instead of tasks.named().
tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseAds)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation(project(":sharedCore"))
    implementation(project(":sharedUI"))
    implementation("androidx.activity:activity:1.10.1")
    // Keep the transitive Fragment runtime off the Play SDK Index obsolete 1.1.0 line.
    implementation("androidx.fragment:fragment:1.9.1")
    implementation("androidx.compose.foundation:foundation:1.9.4")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.ui:ui:1.9.4")
    implementation("com.google.android.gms:play-services-ads:25.4.0")
    implementation("com.android.billingclient:billing:9.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20180813")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4:1.9.4")
}
