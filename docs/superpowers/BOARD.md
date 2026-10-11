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
| upstream-sync-triage | impl | `upstream-sync-triage` (Orca) | 2026-10-11 | Sort upstream (rovkinmax/Puber) commits missing from this fork into take / adapt / skip | 369 commits on upstream/master (up to b755826c, 2026-10-05) not in master; no common merge-base (unrelated histories), so match by content, not hashes. Scope: everything, app code and tooling (release, CI, hermetic player tests, Detekt baseline). Subtitle commits belong to subtitle-upstream-port. Scope widened by the user: triage plus six ports in this branch (P1 CI hardening + signer pin, P2 Main dead code, P3 diagnostics transport label, P4 settings Back check, P5 hermetic baseline profiles, P6 player hermetic fixtures). Spec approved at gate 1 (route standard, 20 ACs). P6 waits for subtitle-upstream-port; P6 part B waits for player-overlays-port. Signer pin waits for the release cert SHA-256 from the user. Plan READY (17 tasks, stops S0-S4). Next: implementer from Task 1; stops at S1 (P4 reproduce on device).  |
| startup-benchmark-entry | idea | - | 2026-10-11 | A compliant way to run `StartupBenchmarks` | From upstream-sync-triage readiness: the only route is `./gradlew connected...` (refused by the hook). Needs a make target with a private adb server, agentw and kept APKs, measuring one package and backend before and after. |
| player-focus-after-panel-close | idea | - | 2026-10-10 | Closing the Episodes panel with Up must put visible focus back on the controls bar | Deferred from player-overlay-autohide QA (Defect 1, predates the branch): after Up closes Episodes the bar has no focus ring and OK only restarts the hide timer. `requestControlsFocus` swallows a failed `requestFocus()` fired in the same frame as the bar's fade-in (`PlayerScreenContent.kt:84-88`). Second entry (player-overlay-autohide review, code #1 / codex #1): a non-last episode ends with Episodes open, viewer idles 6 s, the ended controls come up with focus aimed at `EpisodesButton` and likely no ring; not yet seen on a device. |
| pairing-renewal-retry | idea | - | 2026-10-10 | A failed request for a new pairing code must not leave a dead code on screen | Deferred from the pairing-code-refresh review (code #2): a network failure at renewal ends the auth flow and `AuthVM` only shows a snackbar, so the expired code and 00:00 stay until a restart. Needs a retry or a visible "get a new code" action. |
| unit-suite-flaky | idea | - | 2026-10-10 | Make the full unit-test run deterministic | Seen during pairing-code-refresh: 2 of 4 full `testDevDebugUnitTest` runs failed on tests the branch does not touch (`HistoryVMDeletionSchedulingTest.deleteSelectionDuringAutomaticPagination_isQueuedOnceAndRetainsSemanticFocus`, earlier `HomeVMIncrementalPublishTest.aFailedRefreshOfOneSectionKeepsTheContentItAlreadyPublished`); each passes alone. |
| player-overlays-port | impl | `player-overlays-port` (Orca) | 2026-09-26 | Put every player overlay on one ruler (`docs/design/player-overlays/`) | Moved out of the main checkout as 92a9441d. Error, resume, episode row and chips are ported. Side panel, seek, pause and buffering are not agreed yet. Not yet run on a TV. `PlayerGlass.kt` is expected to become unused. |
| subtitle-upstream-port | review | `subtitle-player-fixes` (Orca, uncommitted) | 2026-10-10 | Bring upstream's subtitle fixes into this fork | First batch: subtitles Off by default (6123fdbc), no positional HLS subtitle match, language names / AI / forced labels, `ControlsStateMachine.initialize` (f3eb3533, Back on fresh media hides the controls first). Static gate green; device pass on emulator-5554: evidence/2026-10-10-subtitle-upstream-port-device.md (in the worktree). Not seen on device: Off across a player rebuild, a DEFAULT/FORCED manifest subtitle, forced/variant labels. Later candidates: side-load API subtitles on HLS (003d4c55), the full merge model. |
| device-verification-debt | needs-device | - | 2026-10-10 | Record the device passes four plans never got | persistent-content-cache (Task 11), side-rail-focus-handoff (Task 7), content-list-swr-cache (Task 5, measurement), memory-and-performance-fixes (Task 8). Waits on host-android-sdk. |
| perf-deferred | idea | - | 2026-09-08 | Plan the items deferred by the performance plan | P3 next-episode warm-up; M4 VM outliving its Koin scope; P5 R8/largeHeap; P8–P12. Each one needs its own spec. |
| design-open-items | idea | - | 2026-10-10 | Close the defects the ported design stands left open | catalogue: `FadeGradient` overhangs the row by 32 dp. content: see "Still open" in `docs/design/content/README.md`. player-controls: the seek bar shows no focus; shared paddings; About panel not measured on a TV. |

## Done

| Slug | Plan | Landed | Evidence | Notes |
|---|---|---|---|---|
| player-overlay-autohide | specs/2026-10-10-player-overlay-autohide-design.md, plans/2026-10-10-player-overlay-autohide.md | 198d6295 (2026-10-10) | evidence/2026-10-10-player-overlay-autohide-device.md, reviews/2026-10-10-player-overlay-autohide.md | Gate CONCERNS: AC-11/AC-13 unit-only; Q8/Q9 decided by the conductor, the user adjusts after testing the release. Follow-up: player-focus-after-panel-close. |
| pairing-code-refresh | specs/2026-10-10-pairing-code-refresh-design.md (quick route, plan in the spec) | 6c9a2a80 (2026-10-10) | evidence/2026-10-10-pairing-code-refresh-device.md, reviews/2026-10-10-pairing-code-refresh.md | First task run by task-run. Gate CONCERNS: AC-7 (renewed code on a signed-out TV) not verified by decision; the expiry-string whitelist is unverified against the real server. Follow-ups: pairing-renewal-retry, unit-suite-flaky. |
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
