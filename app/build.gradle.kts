import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.spotless)
}

// Production geocoder host is threaded in via gitignored local.properties so the
// public PoC endpoint is never the implicit production default. Production builds
// should set GEOCODER_BASE_URL (and GEOCODER_API_KEY when the host requires one)
// to a non-public host; absent keys fall back to the public Nominatim PoC endpoint.
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
    compileSdk {
        version =
            release(36) {
                minorApiLevel = 1
            }
    }

    defaultConfig {
        applicationId = "io.github.seijikohara.femto"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEOCODER_BASE_URL", "\"${geocoderBaseUrl}\"")
        buildConfigField("String", "GEOCODER_API_KEY", "\"${geocoderApiKey}\"")
    }

    buildTypes {
        release {
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
    implementation(libs.okhttp)
    implementation(libs.maplibre.android)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.composables.icons.lucide)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

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
