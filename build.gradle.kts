plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.ben.manes.versions)
    alias(libs.plugins.version.catalog.update)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**", ".gradle/**")
        ktlint(ktlintVersion)
    }
    format("markdown") {
        target("**/*.md")
        targetExclude(
            "**/build/**",
            ".gradle/**",
            ".idea/**",
            ".kotlin/**",
        )
        endWithNewline()
    }
}

versionCatalogUpdate {
    sortByKey.set(true)
    keep {
        keepUnusedVersions.set(true)
    }
}
