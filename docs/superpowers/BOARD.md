# Board

The single record of what is in flight and where it stands. Plans hold the
steps; this file holds the status. Skills: `task-start`, `task-verify`,
`task-ship`, `status` (see AGENTS.md, Project Flow).

Stages: `idea` → `spec` → `plan` → `impl` → `needs-device` → `review` → `done`.

Reconciled against the code on 2026-10-10: every plan's checkboxes were still
empty, so each status below comes from commits and code, not from the plans.

## Active

| Slug | Stage | Branch / worktree | Since | Goal | Notes |
|---|---|---|---|---|---|
| host-android-sdk | idea | - | 2026-10-10 | Make this Linux host build and run the app | Blocks every on-device step. `local.properties` points at a macOS Homebrew SDK; `/usr/lib/android-sdk` has platform-tools only (no platforms, no build-tools); no emulator or TV attached. Needs the user's OK to install packages. |
| player-overlays-port | impl | uncommitted in the main checkout | 2026-09-26 | Put every player overlay on one ruler (`docs/design/player-overlays/`) | Error, resume, episode row and chips are ported. Side panel, seek, pause and buffering are not agreed yet. The work has never been compiled or run on a TV. Move it to its own worktree before continuing. `PlayerGlass.kt` is expected to become unused. |
| subtitle-upstream-port | idea | `subtitle-player-fixes` (Orca, 0 commits) | 2026-10-10 | Bring upstream's subtitle fixes into this fork | `upstream/master` shares no history with this fork, so the fixes come over by hand or cherry-pick: richer source per language, forced and language-less selection, preferences restored across player recreation (2026-08-29 … 2026-10-03). |
| device-verification-debt | needs-device | - | 2026-10-10 | Record the device passes four plans never got | persistent-content-cache (Task 11), side-rail-focus-handoff (Task 7), content-list-swr-cache (Task 5, measurement), memory-and-performance-fixes (Task 8). Waits on host-android-sdk. |
| perf-deferred | idea | - | 2026-09-08 | Plan the items deferred by the performance plan | P3 next-episode warm-up; M4 VM outliving its Koin scope; P5 R8/largeHeap; P8–P12. Each one needs its own spec. |
| design-open-items | idea | - | 2026-10-10 | Close the defects the ported design stands left open | catalogue: `FadeGradient` overhangs the row by 32 dp. content: see "Still open" in `docs/design/content/README.md`. player-controls: the seek bar shows no focus; shared paddings; About panel not measured on a TV. |

## Done

| Slug | Plan | Landed | Evidence | Notes |
|---|---|---|---|---|
| persistent-content-cache | plans/2026-08-13-persistent-content-cache.md | 03c2ca31, d051c873, e9b16920 | - | Device pass pending: see device-verification-debt. |
| side-rail-focus-handoff | plans/2026-08-16-side-rail-focus-handoff.md | 04915c81, 016dde35 | - | Device pass pending. |
| content-list-swr-cache | plans/2026-08-17-content-list-swr-cache.md | d3112173 | - | Folded into `ContentCacheRepository` (d8682b3c). Device measurement pending. |
| catalogue-single-carousel | plans/2026-08-19-catalogue-single-carousel.md | e0c37dfb … fd84f46b | evidence/2026-08-19-catalogue-single-carousel-device.md | Retuned against the catalogue stand in 133d31fe. |
| focus-trailer-preview | plans/2026-08-19-focus-trailer-preview.md | 9362400f … 5d196ed9 | evidence/2026-08-19-focus-trailer-preview-smoke.md, evidence/2026-08-20-trailer-panel-and-controls-device.md | |
| details-single-screen | plans/2026-08-20-details-single-screen.md | edaf5c6d … 6f29cdfb | - | Reworked by the content stand (0192929e). |
| series-seasons-inline | plans/2026-08-20-series-seasons-inline.md | c165a439 … ca130513 | - | Replaced by season chips and `EpisodeRow` (0192929e). |
| network-diagnostics | plans/2026-08-21-network-diagnostics.md | PR #16 | - | Replaced by the media server speed test (d3fa6576). |
| memory-and-performance-fixes | plans/2026-09-08-memory-and-performance-fixes.md | 83b54f94 … 598c1912 | evidence/2026-09-08-memory-and-performance-audit.md (audit, not verification) | T1 reverted on purpose (8eee2e53: Ken Burns took about 60 % CPU on KM6). TV pass pending. Deferred items are in perf-deferred. |
