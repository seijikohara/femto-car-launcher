import com.github.gradle.node.pnpm.task.PnpmTask
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.node.gradle)
    alias(libs.plugins.spotless)
}

// The LIVE map page is TypeScript under webmap/ (the web-payload SSOT), built
// with pnpm + Vite into webmap/dist/web and wired into the assets source set
// below — nothing under src/main/assets/web is committed. download = true makes
// the build hermetic: Gradle provisions Node + pnpm, so neither local builds
// nor CI need a system Node.
node {
    download = true
    // renovate: datasource=node-version depName=node
    version = "24.16.0"
    // null suppresses the plugin's own repository registration; the Node.js
    // ivy repository lives in settings.gradle.kts (FAIL_ON_PROJECT_REPOS).
    distBaseUrl = null
    // Mirrors webmap/package.json "packageManager" (the pin pnpm itself reads
    // when invoked directly); this copy provisions the Gradle-managed install.
    // renovate: datasource=npm depName=pnpm
    pnpmVersion = "11.6.0"
    nodeProjectDir = file("../webmap")
}

val buildWebMap =
    tasks.register<PnpmTask>("buildWebMap") {
        dependsOn(tasks.named("pnpmInstall"))
        pnpmCommand.set(listOf("run", "build"))
        inputs.dir("../webmap/src")
        inputs.files(
            "../webmap/biome.json",
            "../webmap/map.html",
            "../webmap/no-let.grit",
            "../webmap/package.json",
            "../webmap/pnpm-lock.yaml",
            "../webmap/tsconfig.json",
            "../webmap/vite.config.ts",
        )
        outputs.dir("../webmap/dist")
    }

tasks.named("preBuild") {
    dependsOn(buildWebMap)
}

// Geocoder and weather hosts are threaded in via gitignored local.properties so
// a self-hosted caching proxy can be substituted without a code change.
// GEOCODER_BASE_URL is EMPTY by default: the launcher then uses the on-device
// platform geocoder, because no public reverse-geocoding endpoint is
// ToS-compliant to ship. Set it to a self-hosted Nominatim-compatible host (with
// GEOCODER_API_KEY when keyed) to use network geocoding. The weather default
// (api.met.no) is free for commercial use and ToS-compliant — it needs no API
// key, only the identifying User-Agent the client sets. FONTS_METADATA_BASE_URL
// overrides the Google Fonts catalog host (e.g. a caching proxy); the TTF bytes
// still come from the URLs the per-family manifest supplies, so no download host
// field exists.
val localProperties =
    Properties().apply {
        rootProject
            .file("local.properties")
            .takeIf { it.exists() }
            ?.inputStream()
            ?.use { load(it) }
    }
val geocoderBaseUrl = localProperties.getProperty("GEOCODER_BASE_URL", "")
val geocoderApiKey = localProperties.getProperty("GEOCODER_API_KEY", "")
val weatherBaseUrl = localProperties.getProperty("WEATHER_BASE_URL", "https://api.met.no/")
val fontsMetadataBaseUrl = localProperties.getProperty("FONTS_METADATA_BASE_URL", "https://fonts.google.com/")

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
        // CI injects versionCode/versionName: the nightly job uses the run
        // number, the tag-driven release workflow derives them from the version
        // tag (see .github/workflows/{ci,release}.yml). Local builds fall back to
        // the committed 1 / "1.0".
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GEOCODER_BASE_URL", "\"${geocoderBaseUrl}\"")
        buildConfigField("String", "GEOCODER_API_KEY", "\"${geocoderApiKey}\"")
        buildConfigField("String", "WEATHER_BASE_URL", "\"${weatherBaseUrl}\"")
        buildConfigField("String", "FONTS_METADATA_BASE_URL", "\"${fontsMetadataBaseUrl}\"")
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
        debug {
            // Pseudolocales surface text-fit / RTL regressions on debug builds without
            // authoring translations: en-XA accents and lengthens Latin text (~+30%),
            // ar-XB mirrors the layout right-to-left. Debug-only; never shipped.
            isPseudoLocalesEnabled = true
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
    sourceSets {
        getByName("main") {
            // Serve the built web payload (webmap/dist/web/...) as assets/web/...;
            // see the node {} block above.
            assets.srcDir(rootProject.file("webmap/dist"))
        }
        // One SSOT for the fixture builders both the JVM unit tests and the
        // instrumented tests need: src/sharedTest is compiled into each, so the
        // copies can no longer drift (they used to be duplicated per source set).
        // Wired through kotlin.srcDir (not java.srcDir) so the Kotlin test
        // compilations pick the builders up.
        getByName("test").kotlin.srcDir("src/sharedTest/java")
        getByName("androidTest").kotlin.srcDir("src/sharedTest/java")
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

// AboutLibraries collects each Gradle dependency's license at build time into
// res/raw/aboutlibraries.json (bundled, so the licenses screen works offline on
// the head unit). Non-Gradle components shipped in the app (the webmap
// maplibre-gl JS bundle) are declared as manual entries under app/config.
aboutLibraries {
    collect {
        configPath = file("config")
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
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.maplibre.android)
    implementation(libs.aboutlibraries.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.material.kolor)
    implementation(libs.haze)
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
