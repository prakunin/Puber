# Review triage - round 1

Run: subtitle-upstream-port   Base: be8395a4   Reviewed head: 4ae32d90

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial), review-ux-designer-1.md (VISUAL: match).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1, codex | minor | `PlaybackController.kt:161,234-237,549,852,868` | A previous media's `pendingSubtitleTrack` survives `release()`/`prepare()`; when the new media has no audio group, `notifyTracksUpdated` returns early so the VM's D3 restore never runs, and `applyPendingSubtitleSelection` turns text on under a picker reading Off | Read the code: the field is cleared only at :549 (Off pick), `onTracksChanged` calls `applyPendingSubtitleSelection` unconditionally, `notifyTracksUpdated` returns at :851 with no audio groups | fix | Clear `pendingSubtitleTrack` in `prepare()` (new media); controller has no unit tests, so note the gap (see #2) |
| 2 | blind F2 | minor | `PlaybackController.kt:390,611,893-901` | Text-off default, FORCED flag and removed positional fallback have no regression test | No PlaybackController test exists; the VM tests mock it. Owner Q2/Q3 accepted these as untestable here (spec Test design UNTESTABLE) | defer | Extraction of text-track matching is already the scope of run playback-controller-split; Log line added there |
| 3 | edge F1 | minor | `SubtitleLabels.kt:62-80` | ISO 639-2 bibliographic codes (`ger`, `fre`, `chi`, `cze`, `dut`, ...) miss the localized-name map | Read the code: map keyed by `Locale.isO3Language` (terminology codes only); reviewer's jshell check shows `ger` falls back untranslated | fix | Map bibliographic codes to terminology codes in `canonicalSubtitleLanguage`, `ger`/`fre` test cases |
| 4 | tests F1 | minor | `PlayerVMOverlayAutoHideTest.kt:171-196` | T-M1's second half is driven by its own `ShowControls`; nothing asserts the state right after `ResumeFromPosition`; the name over-claims | Read the test; scenario holds | fix | Assert `resumeDialog == null`, controls hidden and unfocused right after resume; rename |
| 5 | tests F2 | nit | `ControlsStateMachine.kt:50`, `ControlsStateMachineTest.kt:41-56` | `lastPanelOpener = Buttons` in `initialize` is unobservable; the trailing assertion cannot fail | Every reader (:89, :139) needs an active panel, only `openPanel` (:64) sets one and overwrites the value first | fix | Drop the dead reset line and the three trailing test lines |
| 6 | tests F3 | nit | plan `:441-444, :591, :606, :661-664` | Implementation record still describes T-L1 with a mocked Context and ru coverage only at function level | Since 0bf26ee5 the test uses `FakeResourceProvider` with a ru case through the mapper | fix | Update the record |
| 7 | ux-designer F1 | nit | `PlayerSidePanel.kt:112-124` (not in diff) | With 11 rows the side panel reaches 1 dp from the top and bottom screen edges (no safe-area inset) | Same geometry on master; this diff only renames rows | defer | Belongs to the player-overlays stand's side-panel scene; Log line added to run player-overlays-port |

## Rejected codex claims

- Codex rated #1 "medium"; the codex-reviewer downgraded it to minor (rare trigger, early return predates the branch). Kept as minor, but fixed because it is cheap and narrows AC-1.

## Next

- Fix round 1: implementer fixes #1, #3, #4, #5, #6 -> check.fast -> lens edge + codex-reviewer on `git diff 4ae32d90`, lens tests (tests change), qa-device on AC-1/AC-3/AC-5 (runtime code changed) -> triage-2.
