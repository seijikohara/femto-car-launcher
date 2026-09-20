package io.github.seijikohara.femto.catalog

/**
 * JUnit category of the screenshot-catalog generator. The regular unit-test
 * tasks exclude it and only `generateCatalog` includes it (both wired in
 * app/build.gradle.kts), so `./gradlew test` never renders 960 images.
 */
internal interface CatalogGeneration
