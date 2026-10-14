# Gate - subtitle-sideload-merge - round 1

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** CONCERNS
**Head:** 871b06a6   **Base:** a97ee2b1

## Done as asked

Read `git diff a97ee2b1...HEAD` (28 commits, 31 files, +5010/-333; production code under `ui/feature/player/` and one additive `SubtitleLink.file` field).

- D1 side-load on HLS: the hand-built `HlsMediaSource.Factory` branch (`setMediaSource`) is gone. Main-player items come from `PlaybackMediaItemFactory` (M3U8 MIME type for `StreamType.HLS`, one `SubtitleConfiguration` per API link with id = label = `stableSubtitleKey()`, FORCED from `forced`) and go through `player.setMediaItem`. That means the player's `DefaultMediaSourceFactory`, which carries `PlaybackErrorPolicy`. Chunkless preparation is on by default there (spec D1 cites `HlsMediaSource.java:177`).
- Text tracks reach the VM: `PlaybackControl.Callback.onTracksUpdated(..., subtitleTracks)`. The new `SubtitleReportKind` decides between API rows (progressive), wait (side-loaded HLS without text groups yet) and player rows. `buildSubtitleRows` strips the merge prefix only when the item was merged (`subtitleSourceId`).
- Merger (more tracks wins, HLS on a tie, unknown language kept unless proven duplicate), selector (identity, then full group id + index, then unambiguous language, never position), and upstream `SubtitleLabeler` / `SubtitleLanguage` with the first batch's strings are all present. The old `subtitleLabels` and its test were removed, with every case carried into `SubtitleLabelerTest`.
- Design delta 2 (D17-D20) is in place: a text-less report is final and collapses to Off. `intendedSubtitleRow` keeps the viewer's intent, is not persisted on its own and is reset per media. The subtitle restore waits for a report with text. A rebuild re-arms it without dropping the intent (871b06a6).
- Out of scope respected: no `SubtitlePreference`, no new pref keys, the `saveTrackPreferences` signature is unchanged, no instrumented tests, no StreamType work. Warm-up still builds HLS directly, with the same shape as base.
- Commits: 28 single imperative English sentences with no prefixes. `Co-Authored-By` lines: 0. The worktree is clean.

## Traceability

| AC | Test / manual check | Evidence | Status (PASS / FAIL / NOT CHECKED / NOT VERIFIED) |
|----|---------------------|----------|----------------------------------------------------|
| AC-1 | `tracksUpdated_hlsApiOnlyLanguage_appearsInRowsAndIsSelectable`, `hls_attachesEveryApiSubtitle_withStableKeyAsIdAndLabel`; device D-1 | Both tests are in my result XML and pass. qa round 3: Psyren S1E1 (API-only list) on 720p, nine rows, `Chinese` cues rendered 12/34 frames | PASS (device on Psyren; Outlander's split is unknown) |
| AC-2 | `merge_carriesManifestOnlyLanguage_withLanguageNameLabel`, `isOff_isFalse_forManifestRowWithEmptyUrl`, `selectSubtitle_manifestOnlyRow_isDelegatedNotTreatedAsOff`, `turnsSubtitlesOff_onlyForNullAndTheOffRow`; device D-2 | Unit tests pass. Device: no title proven manifest-only. Outlander is "most likely" manifest-only (design delta 2), which would make D-1 indirect evidence, but qa did not upgrade it | NOT CHECKED (device), carried over |
| AC-3 | `merge_usesLargerLanguageGroup_preferringHlsOnTies` (7 cases ran), `merge_countsManifestByFromManifestFlag_notByRenditionUri`, `merge_choosesSourceIndependentlyForEachCanonicalLanguage`, and others | Result XML: 32 merger cases, 0 failures. Device D-3: one row per language | PASS |
| AC-4 | `select_picksEachSideLoadedTrackByStrippedFormatId_whenAllStrippedGroupIdsAreZero`, `select_prefersIdentityOverFullGroupId_whenChildOrderChanged`, `select_doesNotFallBackToCoordinates*` | Unit tests pass. qa round 3 D-4: S1E5/S1E3 Auto -> 720p -> Auto, `Russian` restored without a re-pick, cues 31/35 | PASS |
| AC-5 | `tracksUpdated_progressive_nullSubtitleTracks_keepsApiRows`, `progressive_keepsAllApiSubtitles_includingEmbedTrue_withoutM3u8Mime`; device D-5 | Unit tests pass. On device, progressive only runs after an HLS failure, so no title reached it | NOT CHECKED (device), carried over |
| AC-6 | `keepsSelectedRow_whenSameMergedRowStillExists`, `selectedRowReordered_followsRowToNewIndex`, `selectedRowDisappears*`, `identicalRepeatedReports_sendSingleReSelect`, 5 `textless*` tests, `rebuildFastDns_*`, `rebuildBufferPreset_*` | Present and passing in my run. qa round 3 PASS (D-4, D-7 focus) | PASS (rule as amended by delta 2) |
| AC-7 | 5 `tracksUpdated_mergedRows_*`, `tracksUpdated_restoresSavedUrl_onApiLinkedManifestRow`; device D-8 | Unit tests pass. qa rounds 2-3: saved `Russian` restored on cold start, reopen and next episode. The leak case has unit coverage only | PASS |
| AC-8 | `SubtitleLabelerTest`, `PlayerUIMapperTest` | Unit tests pass. Device shows `Russian`, `English`, `Off` and no raw ids. The wording is the owner's call | PASS (wording: owner judges) |
| AC-9 | Media3 trace D15 (owner Q2/Q8); device D-9 | I re-checked the trace in the 1.11.0 AAR bytecode. `ExoPlayerImplInternal.maybeThrowRendererStreamError` catches the stream error for track types 3 (text) and 5 (metadata), logs "Disabling track due to error", calls `disableRenderer` and does not rethrow. No failing title was found | NOT CHECKED (device). The owner-set trace evidence holds |
| AC-10 | device D-6 | Psyren TTFF 2.16 s cold, then 0.37/0.45/0.79 s. Master was not measured | NOT VERIFIED (observation only, no baseline) |
| AC-11 | check.full | Run by me at 871b06a6: BUILD SUCCESSFUL. Result XML: 1593 tests, 0 failures, 0 errors, 0 skipped. Tasks were up to date for this clean tree. The new tests are JUnit 5 and use `FakeResourceProvider` | PASS |

## Findings

| Finding | Source | Disposition | Proof (commit, new run, accepted reason) |
|---------|--------|-------------|------------------------------------------|
| API `forced` overwrote the manifest flag on a linked row | triage-1 #1 (edge F1, blind F2, codex) | fixed | 9120117b; `withApiMetadata` keeps the player flag when `fromManifest`; `merge_keepsPlayersForcedFlag_onManifestRowLinkedToApiRow` |
| Group + index re-find ignored language | triage-1 #2 (edge F2, codex) | fixed | 0c5b30c9; `hasCompatibleLanguageAndForced` |
| Blank-url link became a row and a side-load | triage-1 #3 (edge F3) | fixed | c72b91d6; `sideLoadable()` in the mapper and the factory |
| `buildSubtitleRows` / `findRendition` untested | triage-1 #4 (tests F2, blind F4) | fixed | 62b7376d; `PlayerTextTracksTest` |
| Controller D9 Off check untested | triage-1 #5 (tests F1) | fixed | 8c924389; `turnsSubtitlesOff` and its test |
| R5 re-send count after a real pick untested | triage-1 #6 (tests F3) | fixed | b752f658 |
| A failing side-load is fatal and triggers CDN fallback | triage-1 #7 (blind F1) | rejected; I accept the rejection | Spot-checked in 1.11.0 bytecode: errors in a text or metadata renderer's stream disable that renderer and are logged, not thrown. Matches D15 and the owner's AC-9 decision |
| Count rule can hide a different API subtitle on a tie | triage-1 #8 (blind F3) | rejected; I accept the rejection | Owner decision Q3 (2026-10-12, state.md): upstream 9bd835b0. Listed below as a known consequence |
| A text-less report counted as transient forever (major) | triage-2 #1 (edge-2 F1, codex-2, qa round 2 defect) | fixed | Design delta 2; 5a4b89e9, f4b311e0; qa round 3 PASS on D-4 |
| `currentItemHasSideLoads` computed apart from the spec | triage-2 #2 (tests-2 F1) | fixed | 5a4b89e9; `currentItemHasSideLoads = spec.hasSideLoads` (lines 440 and 511 in the diff); `hasSideLoads_*` tests |
| A rebuild nulled the intent | triage-3 #1 (edge-3 F1, codex-3) | fixed | 871b06a6; `restoreSubtitlePreference` fallback calls `selectSubtitle` only; `rebuildFastDns_keepsIntent_*` |
| Rebuild re-arm untested | triage-3 #2 (tests-3 F1, edge-3 F2) | fixed | 871b06a6; `rebuildFastDns_*`, `rebuildBufferPreset_*` |
| Round 4 (edge, tests) on 2903fc50..871b06a6 | review-edge-4, review-tests-4 | no findings | - |
| Debug stream-source host for side-loads | Q9 | deferred | run `playback-controller-split` exists |
| Manual quality plays `url.hls` without subtitle renditions | Q11, qa round 3 | deferred | run `manual-quality-hls4` exists |
| Restore across recreation (step 2) | Q1 | deferred | run `subtitle-restore-model` exists |

No blocker or major was dropped. The only major (triage-2 #1) is fixed and was device-verified in round 3.

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Merge and labelling run on the main thread for each track report. The lists are a handful of rows, and a `SubtitleLabeler` is built per call (4 string reads), so the cost is negligible. Side-loads are attached on every HLS item, but `SingleSampleMediaSource` children load lazily: qa D-6 saw no extra fetch or buffering with subtitles Off. The main-player HLS item is now wrapped in a `MergingMediaSource` while warm-up keeps the bare HLS shape. TTFF has no master baseline (AC-10) |
| Reliability | Covered: empty and text-less reports (collapse to Off, intent kept), next media (intent and flags reset), link refresh (API rows recomputed), rebuild (restore re-armed, intent kept), HLS-to-progressive fallback (API rows), audio-less reports. A subtitle load failure disables the text renderer (trace re-verified). Not covered: process death and recreation, which is out of scope (subtitle-restore-model). The VM-only fix 871b06a6 was not re-run on device; unit tests cover it |
| Maintainability | Follows AGENTS conventions: JUnit 5 with `MainDispatcherExtension`/`FakeResourceProvider`, strings through `ResourceProvider`, no new DI. The base controller helpers (`buildMediaItem`, `subtitleMimeType`, `setMediaSource`, `findTextTrack*`) and `subtitleLabels` are removed, not left dead. Subtitle re-find lives in `AudioTrackPreferenceResolver`, which already held the subtitle restore, so the misleading name remains. `PlayerVM` grows by about 120 lines of subtitle state; `subtitle-restore-model` is the natural place to extract it |
| Security / privacy | No secrets or account data in code or logs. The evidence file keeps no screenshots or raw responses. I re-ran `tools/mobile-evidence-audit.sh` on a copy of the evidence file: `passed`. Installs were install-preserve `compatible_replace` with the same signer; no owner-authorised `install -r` was used |

## Must fix

none

## Concerns for the owner

1. AC-2 and AC-5 are NOT CHECKED on device. No title was proven manifest-only (Outlander is "most likely" manifest-only, which would make its D-1 pass indirect AC-2 evidence), and progressive is reachable only after an HLS failure. Unit tests cover both.
2. AC-9 is NOT CHECKED on device. The evidence is the Media3 1.11.0 trace you chose (Q8), and I re-checked it in bytecode.
3. AC-10 is NOT VERIFIED: TTFF was observed (0.37-2.16 s) but master was never measured. The main player's HLS item changed shape (`MergingMediaSource` when the item has side-loads); warm-up did not.
4. Known effect of the Q3 merge rule (rejected finding #8): when a language ties, for example 1 forced manifest track against 1 full API track, the manifest wins and the full API subtitle is not offered on HLS.
5. Functional limit seen on device: Outlander on a manual quality (720p/480p) shows only `Off` because that stream has no subtitles. Psyren shows that API side-loads do render there. Follow-up run: `manual-quality-hls4`.
6. Device observation not compared with master: D-pad keys pressed within about 0.9 s of a manual-quality pick were dropped twice. Also, after a switch to 480p the new stream once drew at native size in the top-left corner over a frozen frame (qa round 2).
7. Process: verify and review ran 3 rounds plus a round-4 increment review, beyond the cap of 2. The authority for this is your standing instruction "proceed on recommendations", recorded by the conductor in state.md. The last commit (871b06a6, VM-only) had no device re-pass.
8. Account state changed during qa. Outlander's saved subtitle went from `AI-generated` to `Russian`; it cannot be restored from the UI because the API no longer lists an AI row. Watch progress of Outlander S1E3/S1E5/S1E6/S2E3 and Psyren S1E1 may have moved. A stray Compostelle start was backed out.
