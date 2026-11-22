// The BRouter routing engine, vendored unmodified (README.md names the upstream
// tag and commit). Upstream publishes its artifacts only to a registry that needs
// a login, so the five engine modules build here from source as one library.
plugins {
    `java-library`
}

// Upstream compiles with release 11 (its brouter.java-conventions); the app
// targets Java 11 as well (AGENTS.md#tech-stack).
tasks.withType<JavaCompile>().configureEach {
    options.release = 11
}
