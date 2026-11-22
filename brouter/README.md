# BRouter (vendored)

This module holds the routing engine of [BRouter](https://github.com/abrensch/brouter), which the launcher runs on the device to compute routes from the routing data that brouter.de publishes.

- Upstream: <https://github.com/abrensch/brouter>
- Tag: `v1.7.10`
- Commit: `4d2639af77ea5ed9c30d3e400764eb6f9e8522da`
- License: MIT (`LICENSE`, copied from the upstream repository root)

`src/main/java` is the union of `src/main/java` of these five upstream modules, copied without any change: `brouter-util`, `brouter-codec`, `brouter-expressions`, `brouter-mapaccess` and `brouter-core`. The packages stay `btools.*`. Upstream tests, the map creator, the server and the Android app are not included.

Upstream publishes its artifacts only to GitHub Packages, which needs a login, so the sources build here as one `java-library`. Do not edit the files: update them by copying the same five directories from a newer tag, then update the tag and commit above. The runtime files the engine reads (`lookups.dat` and the `car-vario.brf` profile) come from `misc/profiles2/` of the same tag and live in `app/src/main/assets/routing/`, so update them together with the sources.
