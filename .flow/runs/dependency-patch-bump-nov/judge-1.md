# Gate - dependency-patch-bump-nov - round 1

**Decision:** PASS
**Head:** 16588c3f   **Base:** b1b3c51a

## Done as asked

`git diff b1b3c51a...HEAD` changes four keys in `gradle/libs.versions.toml` (agp 9.5.3, ksp 2.3.14, lifecycleRuntimeKtx 2.11.1, ktor 3.6.2) and `distributionUrl` to `gradle-9.9.3-bin.zip`, plus spec, plan and evidence docs. `composeBom` stays 2026.09.00; `gradlew`, `gradlew.bat`, the wrapper jar, `app/`, `buildSrc/`, `config/` have an empty diff. Nothing from Out of scope (Compose BOM, compileSdk 37.1, baseline profile, `distributionSha256Sum`) is touched.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | catalog diff | re-read by judge: only the four keys differ | PASS |
| AC-2 | properties diff, jar sha256, eol | judge: jar `621008af...f73cdaf8` equals research.md:49 published value; `ls-files --eol` gradlew `i/lf`, gradlew.bat `i/lf w/crlf`; no diff | PASS |
| AC-3 | `help --warning-mode all` diff | judge: `warnings-base.log` vs `warnings-after.log` differ only in the 9.9.1 -> 9.9.3 URL of the same two deprecations | PASS |
| AC-4 | `_Impl` listing after deleting generated ksp | judge: `ksp-impl-c1/c3/qa.txt` identical to `ksp-impl-base.txt` (7 files per variant) | PASS |
| AC-5 | `check.full`, `:app:assembleProdRelease` | `full-gate.log` BUILD SUCCESSFUL; qa re-run at 876912c9; tests lens re-run at 16588c3f, exit 0; qa-report | PASS |
| AC-6 | `make DEVICE=192.168.1.110:5555 itest` for the four `data/db` classes | evidence file, AC-6 table | PASS |
| AC-7 (crash, session, posters, badges, details, playback, toggle after leave/return) | M1-M5 | evidence file, AC-7 table; on-device APK sha256 matches build | PASS |
| AC-7 (share-chooser focus return, `DeviceSettingsContent.kt:210-218`, `currentStateFlow`) | Share log on the box | no chooser appeared on the box (evidence note); triage-1 #1 | NOT CHECKED |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| AC-7 share-focus path not exercised, reported PASS | review-tests-1 F1 (minor), review-codex-1 | fixed in the record: clause carried as NOT CHECKED, manual below | triage-1 #1; this table |
| AC-7 / M5 cite `:213-214` as the toggle, the lines are the share-focus wait | review-tests-1 F1 | accepted minor: wording only, the status split above records what was tested | spec AC-7 |
| Compare Share log on base and candidate | review-codex-1 (rejected) | rejection accepted: not in the spec; no code path changed at the call site | `DeviceSettingsContent.kt:210-218` unchanged |
| KSP 2.3.14 may drop generated code | review-blind-1 unproven | closed by evidence | AC-4 listings, AC-6 itests |
| No `distributionSha256Sum` | review-blind-1 unproven | owner decision Q3 | state.md Decisions |
| `bodyAsText` on short bodies under Ktor 3.6.2 | review-edge-1 unproven | accepted: `handleApiResponse` wraps it in `runCatchingCancellable` (`KinoPubApiClient.kt:1237`); download catches `Exception` and deletes the temp file (`:1024-1030`) | judge read the code |
| No unit test for a short streamed body | review-tests-1 unproven | accepted: posters and playback on the box (AC-7) | evidence file |
| antigravity review | review-antigravity-1 | skipped, quota 429; owner rule allows skip | review-antigravity-1.md |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | No source change; baseline profile kept (Q2), regenerated in `compose-1-13-bump`. |
| Reliability | Ktor incomplete-body failures land in existing failure paths (see Findings); Room `_Impl` set and migrations unchanged (AC-4, AC-6). |
| Maintainability | One version per library in the catalog; no stale versions in build files (review-blind-1, review-edge-1). Spec Problem section line numbers are stale (Q4 records the correct ones). |
| Security / privacy | Gradle 9.9.2/9.9.3 security fixes taken; no credentials in the diff; judge re-ran `tools/mobile-evidence-audit.sh` on the evidence: passed. Install was install-preserve, same signer, not an owner-authorised `install -r`. |

## Manual checks for the owner

1. Share-chooser focus return (AC-7 clause, NOT CHECKED): on the box open Settings > Developer, select "Share log". If a system chooser opens, close it with Back; focus must return to the "Share log" row. If no chooser opens (as in qa round 1), the box cannot show this path; checking it needs another device with a share target, which needs your authorisation (device work is box-only).

## Must fix

none

## Concerns for the owner

none
