LENS: tests  ROUND: 2  BASE: a97ee2b1  RANGE: c6ea3789..7ca85b93
### F1 [minor] No test checks the controller's `currentItemHasSideLoads` against the blank-url filter
- Where: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:440 and :511 (`currentItemHasSideLoads = subtitles.sideLoadable().isNotEmpty()`)
- Problem: Two places decide whether the item has side-loads: the factory through `spec(...).subtitles`, and the controller again in its own expression. `blankUrlLinks_areNotAttached` only covers the factory and `sideLoadable()`. The controller assignment is the line triage #3 changed, and no unit test reaches it.
- Failure scenario: someone reverts either line to `!subtitles.isNullOrEmpty()`. For an HLS item whose API links all have blank urls, `merged` becomes true; `subtitleSourceId` marks every manifest group (ids not starting with `0:`) as `fromManifest = false`, so rendition matching is skipped and no row gets a `playerTrackUri`. All 1578 unit tests stay green.
- Fix: derive the flag from the spec actually built (factory returns the spec or exposes `hasSideLoads = spec.subtitles.isNotEmpty()`), so the decision has one source and the existing spec test covers it.

Checked, nothing to report:
- Round-1 F1 (D9 Off check) closed: `turnsSubtitlesOff()` tested with an empty-url `rus` manifest row.
- Round-1 F2 (row building) closed: `PlayerTextTracksTest` 8 tests, each fails against its targeted regression; only platform stubs (`TextUtils`, `Uri`) mocked.
- Round-1 F3 (re-send loop) closed: exact call counts after a real pick and across two reordered reports.
- Textless-report test fails on the old code; checks no Off send and no re-send after text groups return.
- Re-find language guard: both new resolver tests fail on c6ea3789.
- Blank-url drop: mapper test fails on the old mapper; factory test covers whitespace-only urls.
- Fixture change in `merge_matchesApiToRenditionByFilePath_whenLanguageOrderDiffers` does not weaken the test; new `merge_keepsPlayersForcedFlag_onManifestRowLinkedToApiRow` fails on the old code.
- Suite at 7ca85b93: BUILD SUCCESSFUL, 1578 tests, 0 failures.
