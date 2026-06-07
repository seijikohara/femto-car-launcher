import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.spotless)
}

// Production geocoder and weather hosts are threaded in via gitignored
// local.properties so the public PoC endpoints are never the implicit production
// default. Production builds should set GEOCODER_BASE_URL / WEATHER_BASE_URL (and
// the matching *_API_KEY when the host requires one) to a non-public host; absent
// values fall back to the public Nominatim / Open-Meteo PoC endpoints, which are
// rate-limited and unsuitable for production traffic.
val localProperties =
    Properties().apply {
        rootProject
            .file("local.properties")
            .takeIf { it.exists() }
            ?.inputStream()
            ?.use { load(it) }
    }
val geocoderBaseUrl = localProperties.getProperty("GEOCODER_BASE_URL", "https://nominatim.openstreetmap.org/")
val geocoderApiKey = localProperties.getProperty("GEOCODER_API_KEY", "")
val weatherBaseUrl = localProperties.getProperty("WEATHER_BASE_URL", "https://api.open-meteo.com/")
val weatherApiKey = localProperties.getProperty("WEATHER_API_KEY", "")

// Release signing is driven entirely by environment variables so CI can sign the
// nightly APK without committing a keystore, while local `assembleRelease` stays
// unsigned (no signing config attached) when the variables are absent.
val releaseKeystorePath: String? = System.getenv("RELEASE_KEYSTORE_PATH")

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    val composeRulesVersion = libs.versions.ktlintComposeRules.get()
    kotlin {
        target("src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(ktlintVersion)
            .customRuleSets(
                listOf("io.nlopez.compose.rules:ktlint:$composeRulesVersion"),
            )
    }
}

android {
    namespace = "io.github.seijikohara.femto"
    // compileSdk 37: androidx.core(-ktx) 1.19.0 requires compiling against API 37+.
    // minSdk stays 33 (Android 13) and targetSdk stays 36 — only the compile
    // target advances, not the supported-device floor or the runtime opt-in.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.seijikohara.femto"
        minSdk = 33
        targetSdk = 36
        // CI injects a monotonically increasing versionCode/versionName for the
        // nightly channel; local builds fall back to the committed 1 / "1.0".
        versionCode = System.getenv("NIGHTLY_VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("NIGHTLY_VERSION_NAME") ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEOCODER_BASE_URL", "\"${geocoderBaseUrl}\"")
        buildConfigField("String", "GEOCODER_API_KEY", "\"${geocoderApiKey}\"")
        buildConfigField("String", "WEATHER_BASE_URL", "\"${weatherBaseUrl}\"")
        buildConfigField("String", "WEATHER_API_KEY", "\"${weatherApiKey}\"")
    }

    signingConfigs {
        // Only register the release signing config when CI supplies a keystore path
        // via env; absent it, `signingConfigs.findByName("release")` returns null and
        // the release build stays unsigned so local `assembleRelease` still works.
        releaseKeystorePath?.let { keystorePath ->
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Null when no env-driven keystore is present, leaving the release build
            // unsigned for local contributors; CI attaches the "release" config above.
            signingConfig = signingConfigs.findByName("release")
            // Intentionally unminified: the launcher is a small, reflection-light app
            // and keeping R8 off removes a class of release-only stripping bugs. The
            // keep rules in proguard-rules.pro stay R8-ready so this flag can flip to
            // true later without a separate audit.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            // Plain JVM unit tests (NominatimApiTest) reach android.util.Log on
            // the failure path; without this the stub throws "not mocked".
            // Returning defaults lets the void Log calls no-op instead.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.maplibre.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.material.kolor)
    implementation(libs.composables.icons.lucide)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.okhttp.bom))
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlin.test)
}
