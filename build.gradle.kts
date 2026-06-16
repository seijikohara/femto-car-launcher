plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.version.catalog.update)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    kotlinGradle {
        target("**/*.gradle.kts")
        // **/.gradle/** also covers app/.gradle, where the node-gradle plugin
        // unpacks pnpm (which ships its own Markdown and script files).
        targetExclude("**/build/**", "**/.gradle/**")
        ktlint(ktlintVersion)
    }
    format("markdown") {
        target("**/*.md")
        targetExclude(
            "**/build/**",
            "**/.gradle/**",
            ".idea/**",
            ".kotlin/**",
            "**/node_modules/**",
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
