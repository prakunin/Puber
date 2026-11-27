# Gate - manual-quality-hls4 - round 1

**Decision:** CONCERNS
**Head:** ed0d2fe6   **Base:** 0823528b

## Done as asked

`git diff 0823528b...ed0d2fe6 -- app` matches spec Scope 1-3 and the Decisions table: manual candidates `hls4`, `hls`, `http` with `VideoSizeCap` on both HLS candidates (`PlayerInteractor.kt:245-255`); `applyVideoCap` in `prepare` and `switchStream` (`PlaybackController.kt:428,544`) with `setExceedVideoConstraintsIfNecessary(true)`; `buildAudioRows` with the localized `player_audio_track_fallback` (ru and en); `isFallbackLabel` guard in `saveTrackPreferences` (`PlayerVM.kt:1965-1979`). Beyond the spec: the cap also clears the physical-display viewport constraint and Auto restores it (`VideoCapParameters.kt:16-24`), from triage-1 #4. No hardcoded `"Track ${...}"` left in `app/src/main`. Nothing from Out of scope (corner-draw fix, "Track 1" cleanup, "Unknown" localization) is in the diff.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `selectStreamCandidates_manual_prefersSelectedQualityHls4`, `..._auto_returnsEveryProtocolInFallbackOrder` (file now has w/h), `..._manualWithSurround_keepsVideoCap`, no-width, no-height, out-of-range | check.full exit 0 at ed0d2fe6 (run by judge) | PASS |
| AC-2 | `VideoCapParametersTest` (4); `hlsVideoCap_switchStreamReplacesCap` asserts max size, force-highest and viewport after prepare, capped switch and Auto | check.full; `itest-fix1-3.xml` 12/0 (13:21:30, after last source edit); box: Auto back to 1920x800 | PASS |
| AC-3 | `hlsVideoCap_belowEveryVariant_stillSelectsVideo` (asserts the 320x180 cap is applied and 360 selected) | `itest-fix1-3.xml` | PASS |
| AC-4 | M1 on box | evidence `2026-11-27-manual-quality-hls4-device.md` round 1, shots 3,4,6,7; master shows "Track 1" (`baseline-master.md`) | PASS |
| AC-5 | M2 on box | 720p 1280x534, 480p 720x300, Auto 1920x800; round 2: 1080p 1920x800 | PASS |
| AC-6 | M3 on box | 11 rows and cues on 720p (shot 5); the evidence states the rows' manifest origin is not shown, and master 720p subtitle rows were not recorded in `baseline-master.md` for comparison | NOT VERIFIED |
| AC-7 | M4 on box | shots 3-8, r2-1..r2-5: no corner draw, no frozen frame | PASS |
| AC-8 | `buildAudioRows_unlabelledGroup_usesFallbackString`, `buildAudioRows_labelThenLanguage_keepsManifestName`; instrumented `unlabelledAudio_reportsLocalizedFallbackRowMarkedAsFallback` | check.full; `itest-fix1-3.xml` | PASS |
| AC-9 | `selectAudioTrack_fallbackRow_keepsStoredAudioPreference`, `selectSubtitle_whileAudioRowIsFallback_keepsStoredAudioPreference`; device M6 (non-fallback restore only) | check.full | PASS |
| AC-10 | check.full | judge run at ed0d2fe6: exit 0, BUILD SUCCESSFUL, profile tooling tests PASS | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| prepare cap not asserted by any test (major) | review-tests-1 F1 | fixed | 07aba956: `maxVideoSize`/`forceHighestSupportedBitrate` asserted after prepare in both tests |
| Auto guard file had no w/h (minor) | review-tests-1 F2 | fixed | 13613065 |
| no test ties real unlabelled tracks to fallback rows (minor) | review-tests-1 F3 | fixed | 07aba956: `unlabelledAudio_reportsLocalizedFallbackRowMarkedAsFallback` |
| display viewport trims a pick above display size (minor) | review-codex-1 | fixed | 07aba956: `VideoCapParameters.kt:16-24`, `applyVideoCap_cap_clearsTheDisplayViewportConstraint` |
| Auto viewport restore not discriminated on the emulator (minor) | review-tests-2 F1, review-codex-2 | accepted | test strength only; `VideoCapParameters.kt:23` read correct; one-line test fix is cheap follow-up |
| hls4 401/403 strands quality (codex) | review-codex-1 | rejected, accepted | base `autoStreamCandidates` already plays hls4 first; `PlayerVM.kt:275` advances on `NEXT_URL` |
| fallback label via `Context`, not `ResourceProvider` (codex) | review-codex-1 | rejected, accepted | spec Decisions row "Fallback label"; `PlaybackController.kt:161` holds a `Context` |
| unequal TrackGroups revert dubbing; master without RESOLUTION (codex) | review-codex-1 | rejected, accepted | spec Risks 1, 3; no such master observed |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Force-highest disables ABR on manual picks (spec risk 5, owner Q1); warm-up player ignores the cap (spec risk 6, `PlaybackController.kt:719`). No new main-thread or hot-path work. |
| Reliability | Cap travels on `StreamCandidate`, so prepare, switch, next-candidate fallback and link refresh share it (review-state-path-1). `setExceedVideoConstraintsIfNecessary(true)` is session-wide (spec risk 4). `h == 0` from the API would pin the lowest variant (review-edge-1, unseen in data). |
| Maintainability | Conventions kept: strings in resources, pure helpers in own files, JUnit 5 with `FakeResourceProvider`. `@Suppress("LargeClass")` added on `PlayerInteractorTest`. No dead code left. |
| Security / privacy | No secrets in the diff; `tools/mobile-evidence-audit.sh docs/superpowers/evidence com.kino.puber.stage` re-run by judge: passed, 418 files. Box installs were install-preserve `compatible_replace`, same signer; no owner-authorised `install -r`. |

## Manual checks for the owner

1. M5, dubbing by ear (not checked: no audio capture). On the box, open "Квазимодо, душа Парижа", Settings > Audio > "02. Дубляж (UKR)", then Quality > 720p. Listen 20 s: Ukrainian dubbing plays, not Russian. Then set Audio back to "01. Дубляж (RUS)".

## Must fix

none

## Concerns for the owner

1. AC-6 NOT VERIFIED: it could have been checked here. Subtitle rows and cues survive manual quality, but whether the rows come from the hls4 manifest is unproven. To settle: on the master dev APK open the same item at 720p and count Subtitles rows; fewer than 11 there and 11 on this build shows the manifest rows came back.
2. Manual quality now pins the variant without ABR (owner Q1): on a slow link a manual pick rebuffers where Auto would step down.
