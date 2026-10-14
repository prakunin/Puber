# Brief: subtitle-upstream-port

Migrated from the former board file on 2026-10-12; this run was triaged under the former local flow, so no plugin brief exists.
The board row (verbatim) is in state.md `## Next`. Original documents:

- no spec or plan file exists for this run
- evidence (in the worktree, uncommitted): docs/superpowers/evidence/2026-10-10-subtitle-upstream-port-device.md

## Sizing note (2026-10-12, mode size)

Kind: improvement (subtitle behaviour, mostly bug-class fixes ported from upstream). Size: M. Tracks: ui (picker labels, Off default, controls visibility on Back). Device verification: yes (playback, overlays, Back/D-pad, packaged strings). Plan and spec do not exist; the work sits uncommitted in the worktree (10 modified files, +139/-43, plus untracked SubtitleLabels.kt, SubtitleLabelsTest.kt, evidence). `replay_pid1700735.log` in the worktree is a JVM crash artefact and must not be committed.

Why M, not S: one goal, but it crosses model (`Models.kt`), mapper, playback (`PlaybackController`), VM wiring and strings, and touches the risk areas playback, ViewModel state, networking models and TV overlays/Back.
Why not L: side-load merge and restore model are already split into their own runs.

### Goal
Port the first batch of upstream's subtitle fixes so that: subtitles are Off until chosen; HLS manifest tracks are never matched to API rows by position; the picker names languages, AI and forced rows readably; and Back on freshly prepared media hides the controls before leaving.

### Acceptance criteria
- AC-1 (Off by default, 6123fdbc). Given media whose HLS manifest marks a subtitle DEFAULT or FORCED and no saved choice, When playback starts, Then no text is rendered and the picker reads Off. (`PlaybackController.kt:387` `setTrackTypeDisabled(TEXT, true)`.)
- AC-2 (Off survives rebuild). Given Off is selected, When the player is rebuilt (buffer preset or DNS change), Then text stays disabled.
- AC-3 (explicit/restored choice still works). Given a saved or just-picked subtitle, When the next media starts or the user picks a row, Then that subtitle is rendered (text type re-enabled by the selection path).
- AC-4 (no positional match). Given an HLS stream whose manifest text-track order differs from the API list, When a row is selected, Then the track is matched only by url / stableKey / unambiguous language, and with no match no override is set (never "row N = track N").
- AC-5 (language names). Given API lang `rus`, `eng`, `ai`, a blank or an unresolvable code, Then labels are the localized language name titlecased ("Русский"), `AI-generated` / `Созданные ИИ`, uppercased code, and `Subtitles N` / `Субтитры N` respectively.
- AC-6 (forced). Given `SubtitleLink.forced == true`, Then the row label carries the qualifier (`forced` / `частичные`) and the media item gets `SELECTION_FLAG_FORCED`; a missing `forced` field parses as null without breaking deserialization.
- AC-7 (collisions). Given rows whose labels would read the same, Then each gets `<label> · variant N` / `вариант N` ordinal (numbered per label, not per raw language).
- AC-8 (Back on fresh media, f3eb3533). Given newly prepared media with no resume prompt, When Back is pressed first, Then the controls hide and playback continues; with a resume prompt the controls start hidden and the prompt owns focus; state left by previous media (panel, playbackEnded) is cleared.
- AC-9 (regressions). Given the full unit suite, detekt and lint, Then no new failures on the rebased branch, including master's player overlay-autohide tests.

### Evidence: covered and not covered (docs/superpowers/evidence/2026-10-10-subtitle-upstream-port-device.md)
Covered on emulator-5554 (old base 7ad23247, 1373 unit tests green): Off default on an episode, `AI-generated` label, selecting text, restored choice on next media (AC-1 partly, AC-3, AC-5 for `ai`), Back hides controls on fresh movie (AC-8), no subtitles by default on a movie.
Not covered on device: Off across rebuild (AC-2, unit tests only), a DEFAULT/FORCED manifest (AC-1's actual regression never reproduced on the old build), forced and variant labels (AC-6, AC-7, unit tests only), resume-prompt variant of AC-8, and everything on the rebased code: evidence is from a base 145 commits behind master, so it must be redone after the rebase (smoke at least). The pass also moved account watch progress.

### Risks
- `setTrackTypeDisabled(TEXT)` at selector build: confirm the selection path re-enables text and sets the override, else subtitles never appear (partly seen working on device).
- Dropping the positional fallback may leave an API row with no effect on HLS until the side-load run (subtitle-sideload-merge) lands: a user picks a row and nothing shows. Accepted by the port, worth knowing.
- `SELECTION_FLAG_FORCED` on side-loaded configs may change Media3 default selection once text is enabled.
- `Locale.getAvailableLocales()` ISO-3 map is built lazily on first picker build (cost on TV hardware, unmeasured); Android locale data may name fewer languages than the JVM unit tests.
- `initialize()` in `publishPreparedContent` races with a pending hide job (cancelled there) and replaces the whole State, including master's new `episodesPanelHeld` (default false, fine).

### Rebase onto master (7ad23247 -> be8395a4)
Master changed 15 player files via player-overlay-autohide and a PlaybackClock change. Overlap with this diff:
- `ControlsStateMachine.kt`: master edits the State class, `openPanel`, `closePanel`, replaces `applyControlsVisibility`; our `initialize` is inserted between `lastPanelOpener` and `showControls`, a region master leaves alone. Expected: clean textual merge. Semantic check: `initialize` builds `State(...)`, so it must also reset the new `episodesPanelHeld` (default false: OK) and `playbackEnded`.
- `PlayerVM.kt`: master hunks at ~201, 560, 708-736, 1245, 1831; ours at ~349 and ~756 (`applyControlsState`, near master's 736 `holdEpisodesPanel`). Expected: clean or at most a trivial context conflict near `applyControlsState`. The hide job now calls `applyControlsState()` then `processEffects`, unaffected by `withControlsState`.
- `PlayerUIMapper.kt`: master replaces `formatTime` and removes imports at the file end/top (`java.util.Locale` import and constants). Our hunk is in the subtitle block (~line 46): no overlap, but check that `Locale` is still imported if the mapper needs it (our code uses `context.resources.configuration.locales[0]`, no import needed).
- Tests: `ControlsStateMachineTest.kt` changed heavily on master (+151, rewritten applyControlsVisibility section); our +45 added tests likely conflict by adjacency, resolvable by keeping both. `PlayerUIMapperTest.kt` has master additions only; `PlayerVMMutationAndResultTest` / `PlayerVMPlaybackFlowTest` unchanged on master.
- `PlaybackController.kt`, `Models.kt`, `strings.xml`, `player.xml`: untouched on master, clean.
Overall: low-to-moderate; no semantic clash expected, but master's new episodes-panel auto-hide interacts with Back handling (`handleBack`), so rerun the overlay-autohide tests and re-smoke Back after rebase. Because all work is uncommitted, commit it on the branch first, then rebase (or apply onto a fresh worktree from master).

### Open questions
1. Should the rebase be done into this worktree (commit then rebase) or by re-applying the diff on a fresh worktree from master? Recommended: commit in place, then rebase. Blocks: no.
2. Is a device re-pass on the rebased build required, including a title with a DEFAULT/FORCED HLS subtitle, or is a smoke plus unit tests enough? Recommended: smoke of Off default, picker labels and Back; ask the owner for a known title with forced/duplicate subtitles, else accept unit coverage. Blocks: no.
3. Ship before the side-load run, accepting that API subtitle rows may do nothing on HLS (positional fallback removed)? Recommended: yes, ship first batch; the follow-up run restores function. Blocks: yes (scope of AC-4).
