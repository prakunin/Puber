# Gate - dependency-update-stable - round 1

**Decision:** CONCERNS
**Head:** dd62e708   **Base:** 4db9c0d4

## Done as asked

`git diff 4db9c0d4...HEAD` changes the version catalog, the wrapper properties, jar and `gradlew.bat`, `.gitattributes`, the release-signing fallback in `app/build.gradle.kts:173-178`, and docs. Every catalog value equals the spec's To column (`specs/2026-11-12-dependency-update-stable.md:36`: media3 stays at 1.11.0 by the gate-1 delta, `:223`). `detekt-compose-rules` stays at 1.4.0. `CompileSdk`/`TargetSdk` and the out-of-scope runs are untouched. The signing-fallback credentials are in scope by the gate-1 delta. Wrapper jar sha256 `621008af...cdaf8` matches the official 9.9.1 checksum (triage-1). Nothing under `app/src` changed.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | Diff inspection | Re-read the catalog diff here; media3 1.11.0 per spec `:36` | PASS |
| AC-2 | `--warning-mode all`, base vs new | `warnings-c4.log`: one new `Configuration.setVisible` line under `Configure project :app`; it names no build script | PASS |
| AC-3 | check.full | qa round 1 green at c62b3ae1; re-run here at dd62e708: BUILD SUCCESSFUL; no lint/detekt config or `@Suppress` in the diff | PASS |
| AC-4 | `KinoPubApiClientAuthProbeTest` | Part of check.full; `git diff 4db9c0d4...HEAD -- app/src` is empty | PASS |
| AC-5 | `:app:assembleProdRelease` + launch | qa-report AC-5; evidence row AC-5 (M6), signer equals the debug cert | PASS |
| AC-6 | Clean KSP, `git status app/schemas` | qa-report AC-6: 5 `_Impl` for devDebug and prodRelease | PASS |
| AC-7 | Device, install-preserve | Evidence row AC-7 | PASS |
| AC-8 | Device, D-pad over Home | Evidence row AC-8 | PASS |
| AC-9 | Device, HLS play + seek, 45 s | Evidence row AC-9 (Media3 1.11.0) | PASS |
| AC-10 | Device restart + DAO itests | Evidence round 1 (DB opens, rows survive, progress save fails on base too) and round 2 (`WatchStateDaoTest` 8/8, `CachedPayloadDaoTest` 6/6); `withWriteTransaction` (`DatabaseTransaction.kt:22`) on a file-backed DB never ran | NOT CHECKED |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| AC-10 write path unproven, DAO itests not run | tests F1, codex-1 (major) | fixed in part: itests run | 2d24287e, evidence round 2 |
| AC-10 `withWriteTransaction` path still unrun | edge-2 F1 (major), codex-2 (minor) | accepted under the owner's standing instruction; carried as NOT CHECKED | spec Decisions `:225` (dd62e708); state.md Decisions |
| Spec targets media3 1.11.1 | edge-1 F1, tests F2 | fixed | bebab8ec; spec `:36,:64,:95,:160` read here |
| `gradlew.bat` CRLF whole-file diff | blind F1 | fixed | 393bd7fe; `.gitattributes` in the diff |
| Ktor `readRemaining` deprecation at `KtorPlugins.kt:106` | tests F3 | deferred | run `ktor-readremaining-deprecation` (stage idea, source this run) |
| Plan C3 step still says media3 1.11.1 | edge-2 F2 (nit) | accepted minor | plan Implementation record documents the delta |
| Codex: version table needs checking | codex-1 adversarial | rejected; I accept | catalog diff re-read here matches the To column |
| Codex: hardcoded signing credentials | codex-1 adversarial | rejected; I accept | `keytool -list` on tracked `app/debug.jks` with `android` lists `androiddebugkey`: public debug credentials |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Baseline profile not regenerated for the new library versions (spec Q4, `:212`); startup cost unmeasured |
| Reliability | DB opens and existing rows survive an update (evidence AC-10 round 1); real-DB write transaction unproven (AC-10) |
| Maintainability | No dead code or helper changes; one plugin-originated Gradle deprecation (`Configuration.setVisible`, removal in Gradle 11) and the Ktor deprecation (deferred run) remain |
| Security / privacy | Signing fallback holds only the public debug keystore credentials; CI checks release secrets first (review-edge-1). `tools/mobile-evidence-audit.sh` on the evidence dir: `passed`; screenshots not stored |

## Manual checks for the owner

1. AC-10 on a device, once `resume-progress-anomaly` makes progress save work: install the worktree dev APK with `tools/android-apk-install-preserve` on a leased emulator; play a title 60 s, seek forward, Back; `adb -s <serial> shell am force-stop com.kino.puber.stage`; relaunch and reopen the title. Expect the saved position; `logcat --pid` filtered `room|sqlite|FATAL` empty.

## Must fix

none

## Concerns for the owner

1. AC-10 is NOT CHECKED and could have been checked here: an instrumented test calling `PuberDatabase.transactions()` on a file-backed database would cover `withWriteTransaction` on Room 3.0.3. Accepted under the standing instruction (spec `:225`); Room 3.0.1->3.0.3 and sqlite 2.7.0->2.7.1 are patch bumps.
2. Baseline profile left stale for the new libraries (Q4); regenerate in the next release run.
3. Three follow-up runs depend on this one: `media3-hagc-removal`, `ktor-readremaining-deprecation`, `detekt-compose-rules-revive` (all stage idea).
