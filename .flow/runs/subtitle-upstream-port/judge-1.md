# Gate - subtitle-upstream-port - round 1

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** CONCERNS
**Head:** 0973d92b   **Base:** be8395a4

## Done as asked

I read `git diff be8395a4...HEAD` myself (28 files: 9 production, 10 test, docs and evidence).
It does what the approved spec asks:

- AC-1/AC-2: `PlaybackController.prepare()` builds the selector with `setTrackTypeDisabled(TEXT, true)`.
  Text is enabled only by `applySubtitleTrackSelection` (`setTrackTypeDisabled(TEXT, false)`), and an
  Off pick disables it again.
- AC-4: `findTextTrackBySubtitleIndex` is deleted. Matching is now url, then stable key, then
  unambiguous language. With no match, no override is set.
- AC-5..AC-7: new pure `SubtitleLabels.kt`, called once from `PlayerUIMapper.mapSubtitleTracks`
  (D9). It has three string ids plus the existing variant string, in ru and en. `SubtitleLink.forced`
  defaults to null. `SELECTION_FLAG_FORCED` is set on side-loaded configs.
- AC-8: `ControlsStateMachine.initialize` and `PlayerVM.publishPreparedContent` (cancels the hide
  job, resets the machine, publishes `withControlsState`).
- D3: `restoreTrackPreferences` re-sends `content.selectedSubtitleIndex` when nothing saved resolves.
- D8: both master overlay-autohide tests are rewritten. T-M2 uses the last-episode exit instead of
  the spec's recipe. The plan records that deviation and backs it with a mutation check.

Beyond the spec, with owner decisions:

- `PlayerUIMapper` now takes `ResourceProvider` (new `locale` property) instead of `Context`. This
  fixes QA defect 1, so every player label follows the in-app language. The owner decided this on
  2026-10-14. Koin `PlayerUIMapper(get(), get())` resolves `single<ResourceProvider>` (`PuberApp.kt:43`).
- Triage-1 #1 put `pendingSubtitleTrack = null` into `prepare()`. The architect had rejected exactly
  that alternative ("would also drop a live pick on a buffer or DNS rebuild"). That cost became
  triage-2 #1, which the owner deferred (see Concerns).

Nothing from Out of scope went in: no side-loading on HLS, and no new preference keys.
Hygiene: `replay_pid1700735.log` is not committed. No commit has a Co-Authored-By line (0 found).
All changed files are under `app/src` or `docs/`.

## Traceability

| AC | Test / manual check | Evidence | Status (PASS / FAIL / NOT CHECKED / NOT VERIFIED) |
|----|---------------------|----------|----------------------------------------------------|
| AC-1 | Unit: T-D3a `tracksUpdated_reappliesOffRow_whenSavedSubtitleDoesNotResolve`, T-D3b `tracksUpdated_nextMediaWithUnresolvedChoice_sendsOffRow`, rewritten `..._ambiguousSubtitleLanguage_withoutUrl_reappliesOffRow` (read in diff). Device: M1 and pick-then-switch (Psyren S1E1 to S1E2, Outlander S2E3 to S2E4) | evidence 2026-10-14 Round 3; 1460/0/0/0 test XML | PASS for Off by default. The HLS DEFAULT/FORCED manifest case is NOT CHECKED (not reproduced, owner Q2) |
| AC-2 | None (controller has no unit tests; optional M6 not run) | none | NOT CHECKED (carried from qa; owner Q2) |
| AC-3 | Unit: existing restore-by-url tests, `selectSubtitle_updatesStateAndDelegates`. Device: restored `AI-generated` and `Русский` render, Off then the row again | evidence Round 3 | PASS |
| AC-4 | None (private controller matching; no positional-match fixture) | none | NOT CHECKED (carried from qa; owner Q3) |
| AC-5 | Unit: `SubtitleLabelsTest` (incl. `bibliographicCodes_readAsLanguageNamesToo`), `PlayerUIMapperTest.mapSubtitleTracks_namesLanguagesInTheProvidersLocale`. Device: in-app Russian labels | evidence Rounds 2-3; ux-designer review measured | PASS |
| AC-6 | Unit: `forcedSubtitles_getAQualifier`, `SubtitleLinkTest` (4 cases). `SELECTION_FLAG_FORCED` has no test | test XML | PASS (unit only). Device NOT CHECKED (no title). The FORCED flag on the media item is untested |
| AC-7 | Unit: `rowsThatStillReadTheSame_getOrdinals`, `ordinals_countFinalLabels_notRawLanguages`, `composedLabel_usesTheSameSeparatorAsTheVariantString` | test XML | PASS (unit only). Device NOT CHECKED (no title) |
| AC-8 | Unit: `initialize_*` (4), `backPressed_onFreshMedia_hidesTheControlsInsteadOfLeaving`, T-M1, T-M2, `publishPreparedContent_closesPanelLeftByPreviousMedia`, `publishPreparedContent_resumePromptAfterRetry_...`. Device: M4, M7, M8 | evidence Rounds 1-3 | PASS |
| AC-9 | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`, which I re-ran: BUILD SUCCESSFUL, tasks up to date for this tree; XML shows 1460 tests, 0 failures, 0 errors, 0 skipped | judge run plus qa Round 3 | PASS |

Re-checked by sampling:
- The T-D3 tests use `verifyOrder` (stale row 2, then the Off row 0) and assert that nothing is saved.
- T-M1 asserts that the controls are hidden and unfocused before and after `ResumeFromPosition`.
- The evidence audit passed on both evidence markdown files and on the 2026-10-10 PNG directory.
- I viewed two PNGs. They show the player and the picker only, with no account data.
- On-device APK sha256 matches the build (Round 3: `3a38a332...7376`).

## Findings

| Finding | Source | Disposition | Proof (commit, new run, accepted reason) |
|---------|--------|-------------|------------------------------------------|
| Stale `pendingSubtitleTrack` survives prepare when the new media has no audio group (minor) | blind F1, codex-1 | fixed | c816bc44 (`prepare()` clears it); device Round 3 pick-then-switch PASS |
| Text-off default, FORCED flag and removed positional fallback have no controller test (minor) | blind F2 | deferred | Logged in `.flow/runs/playback-controller-split/state.md:33` |
| ISO 639-2 bibliographic codes unnamed (minor) | edge-1 F1 | fixed | e88d1934 + `bibliographicCodes_readAsLanguageNamesToo`. Edge-2 checked all 20 B/T pairs |
| T-M1 second half driven by its own ShowControls (minor) | tests-1 F1 | fixed | 289d835c. Tests-2 confirmed the new asserts can fail |
| Unobservable `lastPanelOpener` reset and a test line that cannot fail (nit) | tests-1 F2 | fixed | 3e73cf38 |
| Plan record describes the old T-L1 setup (nit) | tests-1 F3 | fixed | 0127f0c3 |
| Side panel with 11 rows within 1 dp of the screen edges (nit, pre-existing, not in diff) | ux-designer-1 F1 | deferred | Logged in `.flow/runs/player-overlays-port/state.md:34` |
| Audio-less rebuild of the same media drops the live subtitle pick (minor, a regression introduced by c816bc44) | edge-2 F1, codex-2 | deferred (owner, 2026-10-14) | Logged in `.flow/runs/subtitle-restore-model/state.md:33`. The run folder is untracked in the main checkout |
| Codex rated the stale-pick finding "medium" | codex-1 | severity downgraded to minor, then fixed anyway | Accepted: I verified that `notifyTracksUpdated` returns early on no audio groups and that this predates the branch. Moot, since it is fixed |

No blocker or major was raised in any review or triage. Nothing was silently dropped: every
review finding maps to a triage row. Triage-2 rejected no claims. The only rejection in the run is
the codex severity downgrade, which I spot-checked and accept. There was no second rejection to
sample.

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | `iso3Locales` is built lazily on the main dispatcher at the first picker map: `getAvailableLocales` plus `isO3Language` per locale. This cost on TV hardware is unmeasured; architect risk 6 accepted it. Labels are built once per media, not per frame. No Compose changes beyond preview data |
| Reliability | `isO3Language` throwing is caught (`runCatching`) for `ai` and unknown codes. Empty and blank `lang` fall back to `Subtitles N`. The hide job is cancelled before `initialize`. Open gap: on a stream with no audio group, the audio-less rebuild drops the live pick (deferred). Restore still depends on `onTracksUpdated`, which needs an audio group |
| Maintainability | Follows AGENTS conventions: ResourceProvider in non-composable code, strings in resources, JUnit 5 with `FakeResourceProvider`. `withControlsState` removes the duplicate copy block. No dead code left: the positional helper is removed, the dead `lastPanelOpener` reset is removed, and `Context` is gone from the mapper. `canonicalSubtitleLanguage` has no callers outside the file (D9) |
| Security / privacy | No secrets or tokens in code or docs. Evidence audit passed. The PNGs show no account data. Account watch progress was mutated during the device passes; this is recorded in the evidence (see Concerns) |

## Must fix

none

## Concerns for the owner

1. These ACs are NOT CHECKED, by your decisions Q2 and Q3:
   - AC-2 (Off survives a rebuild) and AC-4 (no positional match) have no test.
   - AC-1 is not reproduced on an HLS manifest that marks a track DEFAULT or FORCED.
   - AC-6 and AC-7 are unit only. No device title had forced or duplicate-language rows.
   - `SELECTION_FLAG_FORCED` on the media item is untested.
2. A rare regression is shipping, deferred to subtitle-restore-model. On a stream with no audio
   group, changing the buffer preset or Fast DNS turns subtitles off while the picker still shows
   the row. It comes from c816bc44, a review fix the architect had rejected as a design alternative.
   The `.flow/runs/subtitle-restore-model/` folder that records it is untracked in the main
   checkout. Commit it, or the deferral can be lost.
3. API subtitle rows may do nothing on HLS until subtitle-sideload-merge lands (accepted under Q3).
   `findUnambiguousTextTrackByLanguage` compares raw `format.language`.
4. Codex review round 2 ran without the adversarial pass. Round 1 ran it.
5. PlaybackController's changes have no regression test. This is deferred to
   playback-controller-split.
6. The ISO-3 locale map cost on TV hardware is not measured.
7. Account state changed by the device passes:
   - Slow Horses S4E6 was started from the beginning, so its saved position (50:19) may be replaced.
   - Psyren S1E1 now holds an explicit `Off`.
   - Short plays were recorded on Outlander S2E3/E4, Psyren, Nia Liston, Compostelle and Moon le Panda.
8. Every install was the standard same-signer `compatible_replace` through
   `tools/android-apk-install-preserve`. No owner-authorised `install -r` repair took place.
