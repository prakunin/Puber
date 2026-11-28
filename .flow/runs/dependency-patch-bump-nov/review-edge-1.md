LENS: edge  ROUND: 1  BASE: b1b3c51a

No findings.

The change is five version strings: agp, ksp, lifecycleRuntimeKtx and ktor in gradle/libs.versions.toml, and distributionUrl in gradle/wrapper/gradle-wrapper.properties. Boundaries checked against the code, none unhandled:
- Old versions left elsewhere: none in build files; buildSrc has no plugin dependency of its own; CI takes the Gradle version from the wrapper through setup-gradle.
- Wrapper jar and scripts: not in the diff, no CRLF churn; jar sha256 matches the published 9.9.3 value (qa report).
- Existing databases on upgrade: app/schemas unchanged; same 7 _Impl files per variant; migration test passed on the box.
- Ktor 3.6.2 failing incomplete bodies: update download (KinoPubApiClient.kt:1006-1030) catches Exception, deletes the temp file and returns Result.failure; body logger (KtorPlugins.kt:87) debug only; bodyAsText error reads (ApiFailures.kt:37, KinoPubApiClient.kt:295) wrapped in runCatchingCancellable; posters showed on the box.
- Lifecycle 2.11.1 minCompileSdk=37: Versions.kt:12 meets it; check.full passed.

### Unproven
- app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:1240-1244 (non-streamed bodyAsText on the API path): under Ktor 3.6.2 a short body now throws instead of returning partial text. A finding only if a caller catches too narrowly or a KinoPub response really ends early in ordinary use. Nothing in the qa run showed either.
- DeviceSettingsContent.kt:213-214 (currentStateFlow read on the share-log path): qa did not exercise it, because the box showed no share chooser.
