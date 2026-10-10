# Board

The single record of what is in flight and where it stands. Plans hold the
steps; this file holds the status. Skills: `task-start`, `task-verify`,
`task-ship`, `status` (see AGENTS.md, Project Flow).

Stages: `idea` → `triage` → `spec` → `plan` → `impl` → `verify` → `review` → `done`;
`needs-device` is a blocker that can interrupt `triage`, `verify` or `review`.

Reconciled against the code on 2026-10-10: every plan's checkboxes were still
empty, so each status below comes from commits and code, not from the plans.

## Active

| Slug | Stage | Branch / worktree | Since | Goal | Notes |
|---|---|---|---|---|---|
| pairing-code-refresh | review | `pairing-code-refresh` (Orca) | 2026-10-10 | The device-pairing code must renew itself when its timer runs out | Seen on emulator-5554 with the dev build 1.9.69-dev: the countdown reached 00:00 and the code stayed the same until the app was restarted. Verified: AC-1..6 PASS via tests, smoke on emulator-5554 signed in, AC-7 NOT CHECKED by decision (evidence 6617ae99). Next: code, test and codex review. |
| pairing-renewal-retry | idea | - | 2026-10-10 | A failed request for a new pairing code must not leave a dead code on screen | Deferred from the pairing-code-refresh review (code #2): a network failure at renewal ends the auth flow and `AuthVM` only shows a snackbar, so the expired code and 00:00 stay until a restart. Needs a retry or a visible "get a new code" action. |
| unit-suite-flaky | idea | - | 2026-10-10 | Make the full unit-test run deterministic | Seen during pairing-code-refresh: 2 of 4 full `testDevDebugUnitTest` runs failed on tests the branch does not touch (`HistoryVMDeletionSchedulingTest.deleteSelectionDuringAutomaticPagination_isQueuedOnceAndRetainsSemanticFocus`, earlier `HomeVMIncrementalPublishTest.aFailedRefreshOfOneSectionKeepsTheContentItAlreadyPublished`); each passes alone. |
| player-overlays-port | impl | `player-overlays-port` (Orca) | 2026-09-26 | Put every player overlay on one ruler (`docs/design/player-overlays/`) | Moved out of the main checkout as 92a9441d. Error, resume, episode row and chips are ported. Side panel, seek, pause and buffering are not agreed yet. Not yet run on a TV. `PlayerGlass.kt` is expected to become unused. |
| subtitle-upstream-port | needs-device | `subtitle-player-fixes` (Orca, uncommitted) | 2026-10-10 | Bring upstream's subtitle fixes into this fork | First batch written: subtitles Off by default (6123fdbc), no positional HLS subtitle match, language names / AI / forced labels, `ControlsStateMachine.initialize` (f3eb3533, Back on fresh media hides the controls first). Static gate green (1373 unit tests, detekt, lint: no new issues). Installed and launched on emulator-5554 without a crash, but the app is unpaired there, so the player was never reached. Needs a paired device: subtitle Off on an HLS series, picker labels, Back at start. Later candidates: side-load API subtitles on HLS (003d4c55), the full merge model. |
| device-verification-debt | needs-device | - | 2026-10-10 | Record the device passes four plans never got | persistent-content-cache (Task 11), side-rail-focus-handoff (Task 7), content-list-swr-cache (Task 5, measurement), memory-and-performance-fixes (Task 8). Waits on host-android-sdk. |
| perf-deferred | idea | - | 2026-09-08 | Plan the items deferred by the performance plan | P3 next-episode warm-up; M4 VM outliving its Koin scope; P5 R8/largeHeap; P8–P12. Each one needs its own spec. |
| design-open-items | idea | - | 2026-10-10 | Close the defects the ported design stands left open | catalogue: `FadeGradient` overhangs the row by 32 dp. content: see "Still open" in `docs/design/content/README.md`. player-controls: the seek bar shows no focus; shared paddings; About panel not measured on a TV. |

## Done

| Slug | Plan | Landed | Evidence | Notes |
|---|---|---|---|---|
| host-android-sdk | - | 2026-10-10 | - | `~/Android/Sdk` (platform 37, build-tools 36/37, emulator, `android-36/android-tv/x86_64`). AVD `puber_tv_36` = `emulator-5554`, paired, snapshot in `.auth/`. install-preserve needs `cmdline-tools/latest/bin` on PATH for apkanalyzer. |
| persistent-content-cache | plans/2026-08-13-persistent-content-cache.md | 03c2ca31, d051c873, e9b16920 | - | Device pass pending: see device-verification-debt. |
| side-rail-focus-handoff | plans/2026-08-16-side-rail-focus-handoff.md | 04915c81, 016dde35 | - | Device pass pending. |
| content-list-swr-cache | plans/2026-08-17-content-list-swr-cache.md | d3112173 | - | Folded into `ContentCacheRepository` (d8682b3c). Device measurement pending. |
| catalogue-single-carousel | plans/2026-08-19-catalogue-single-carousel.md | e0c37dfb … fd84f46b | evidence/2026-08-19-catalogue-single-carousel-device.md | Retuned against the catalogue stand in 133d31fe. |
| focus-trailer-preview | plans/2026-08-19-focus-trailer-preview.md | 9362400f … 5d196ed9 | evidence/2026-08-19-focus-trailer-preview-smoke.md, evidence/2026-08-20-trailer-panel-and-controls-device.md | |
| details-single-screen | plans/2026-08-20-details-single-screen.md | edaf5c6d … 6f29cdfb | - | Reworked by the content stand (0192929e). |
| series-seasons-inline | plans/2026-08-20-series-seasons-inline.md | c165a439 … ca130513 | - | Replaced by season chips and `EpisodeRow` (0192929e). |
| network-diagnostics | plans/2026-08-21-network-diagnostics.md | PR #16 | - | Replaced by the media server speed test (d3fa6576). |
| memory-and-performance-fixes | plans/2026-09-08-memory-and-performance-fixes.md | 83b54f94 … 598c1912 | evidence/2026-09-08-memory-and-performance-audit.md (audit, not verification) | T1 reverted on purpose (8eee2e53: Ken Burns took about 60 % CPU on KM6). TV pass pending. Deferred items are in perf-deferred. |
