# Brief: subtitle-restore-model

Request: restore the chosen subtitle across player recreation with upstream's `SubtitlePreference` keys (7d5addd2, f9c9ff38, a563d6f6).
Base: master `f351d988`. Research: `research.md` (cited as `research.md: <section>`).

kind: improvement (fixes real gaps G1-G3, but no single crash; treat as bug-like data-loss on re-entry)
size: L (owner added option C on 2026-10-14: two shippable goals, see SPLIT). Part 1 alone is M.
tracks: none (no layout change; no measured number)
device verification: yes (persistence, playback, ViewModel state; exit and re-entry only show at runtime)

## Findings (code wins; app paths relative to `app/src/main/java/com/kino/puber/`)

- Deferred audio-less rebuild item: **closed in code, confirmed** (`research.md: 3`). `PlaybackController.kt:822` returns early only when there are neither audio nor text groups; `PlayerVM.kt:552-557` keeps audio rows on an audio-empty report; `PlayerVM.kt:927-951` keeps `intendedSubtitleRow` on rebuild (`871b06a6`). `prepare()` clearing `pendingSubtitleTrack` (`PlaybackController.kt:385-387`) is harmless because the VM re-sends. Only a regression test is missing. Decision: closed, pin it with one test, do not touch `prepare()`.
- Persisted shape today: `subtitle_lang_<id>` and `subtitle_url_<id>` only (`data/repository/PlayerPreferencesRepository.kt:16-22,28-56,119-122`; `domain/interactor/player/PlayerInteractor.kt:335-358`).
- Save: `PlayerVM.kt:1849-1871` writes `intendedSubtitleRow.language` / `.url`, each only when non-empty.
  - G1 (confirmed by reading): a manifest-only row without language has `url=""`, so both values are null and both keys are removed, i.e. the pick is stored as Off.
  - G2 (confirmed): a manifest-only row with a language saves only the language.
- Restore: `PlayerVM.kt:599-616` resolves only the saved preference; `AudioTrackPreferenceResolver.kt:26-39` has three rules (exact URL, stable key, unambiguous language that ignores forced and labels).
  - G2 (continued, confirmed): two same-language rows (full and forced, or labelled variants) are ambiguous, so the result is Off.
  - G3 (confirmed by reading, no test): when only a forced row of that language exists, the language rule picks it with `persist=false` (`PlayerVM.kt:606-608`), so a full-subtitle viewer silently gets the forced track.
- Rebuild order today: saved preference first, intent is only a fallback. Upstream order is `previousSubtitle ?: saved`.
- Already on master, do not re-port: a563d6f6 (separate audio/subtitle restore, pass-through while pending), selector semantic filter and `isForced` (7d5addd2 part), FORCED flag on side-loads and language-less selector identity (f9c9ff38 part), see `research.md: 3`.
- Media3 1.11.0 ids (`research.md: 5`): a manifest group id is `subtitle:<NAME>`, or `0:subtitle:<NAME>` when the item has side-loads. The prefix must be stripped before persisting (`withoutMergedSourcePrefix` in `SubtitleTrackIdentity.kt`); side-load ids `<n>:0` are positional and must never be persisted.

## Scope (option B)

1. `SubtitlePreference(language, url, isForced, descriptiveLabel)`; repository keys `subtitle_forced_<id>` (Boolean, null when the key is missing) and `subtitle_label_<id>`; interactor getter returning the preference. Old `subtitle_lang_`/`subtitle_url_` entries keep working (forced and label null = don't care).
2. Save from `intendedSubtitleRow` with a `preferenceKey()` in the URL slot: url, sourceFile, merge-prefix-stripped manifest group id (manifest rows only), rendition URI, track id. Also save forced and the readable descriptive label. Pass-through while the restore is pending covers all four values. An explicit Off clears all four.
3. Resolver `findSubtitleTrackIndex`: semantic pre-filter (language, forced, readable label), identity over all row ids, descriptive-label match, language match that prefers manifest rows, empty preferred language means Off only when no identity is given. No coordinates.
4. Rebuild restore: intent first, then the saved preference.
5. Tests (below).

## Scope, option C (owner decision 2026-10-14: included)

6. `PlaybackController.switchStream` (`ui/feature/player/vm/PlaybackController.kt:501-521`) gets upstream's `disableSubtitlesForSourceReplacement` (7d5addd2): `pendingSubtitleTrack = null`, then clear text overrides and disable the text type, before `setMediaItem`. Today it copies `savedTrackParams` (`:505,518`), so a text override built for the old stream's group ids rides onto the new stream and can pick the wrong row (or a row with a reused id). After the change the VM must re-send the subtitle once the new stream reports tracks. Verify in the design step that the track report path (`PlayerVM.kt:552-616`) restores after a `switchStream` (callers `PlayerVM.kt:433,453,505`); upstream's test is `hlsSwitch_..._butWaitsForFreshSubtitleRestore`.
7. Instrumented tests for it, run with `make DEVICE=<serial> itest TESTS=<class>` (never `connected*AndroidTest`).

### Instrumented-test infrastructure (from code)

- On master: `app/src/androidTest/kotlin/...` exists with 21 Compose/DAO/focus tests, none touches `PlaybackController` or ExoPlayer; deps are only compose `ui-test-junit4`, `test-ext-junit`, `test-runner`, `coroutines-test` (`app/build.gradle.kts:419-423`); runner `AndroidJUnitRunner` (`:67`); `make itest` exists (`Makefile:126-136`, private adb server, keeps APKs and login). No flavor `instrumentation`, no fixtures module (`settings.gradle.kts:23-24` includes only `:app`, `:baselineprofile`), no MockWebServer in the app, no media assets, no Robolectric. `PlaybackController` needs a real `ExoPlayer`, so it cannot be unit-tested on the JVM.
- Upstream's cases (`PlaybackControllerNetworkTracksTest`, 7d5addd2 and f9c9ff38) all rely on `HermeticTestServer` / `PlayerTestFixtures` from `:player-test-fixtures` (synthetic MP4/HLS/VTT, loopback server, network blocker) and on Kaspresso `run {}`.
- P6 (upstream-sync-triage, spec `docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md` D20-D25, plan Tasks 13-16): brings `:player-test-fixtures`, tests in `app/src/androidTestInstrumentation/.../player/vm/`, a new `instrumentation` flavor run with `make FLAVOR=instrumentation itest`, plain AndroidJUnit4 instead of Kaspresso. State: not started in the worktree `/home/user1/orca/workspaces/Puber/upstream-sync-triage` (no `player-test-fixtures` dir, `settings.gradle.kts` unchanged; P1-P5 done, Task 2 waits for a cert). Its only gate on this was `subtitle-upstream-port`, which is now `done`. D24 ports only cases needing no production change, so the `switchStream` cases are explicitly not in P6; they would be added by this run on top of P6's fixtures.
- Can this run have instrumented tests without P6? Only by duplicating P6-A in this run (new module, loopback HLS/MP4 fixtures, flavor, blocker): that is the same L work, would conflict on `settings.gradle.kts`, `libs.versions.toml`, `Makefile`, and be thrown away. A hand-made minimum (own `ServerSocket` serving an HLS manifest plus a WebVTT rendition, no decodable video) is not reliable: ExoPlayer reports text groups only after the media period is prepared. **Conclusion: the instrumented tests depend on P6-A.** Without P6-A the only evidence for item 6 is the device check (AC-11 quality-switch half) plus a code review of the change.

## Out of scope

- Building `:player-test-fixtures` (that is P6-A, owned by upstream-sync-triage), Kaspresso, Robolectric.
- Upstream's callback-generation gate (`PlaybackCallbackGate`, P6 D25/D26).
- Any change to `prepare()`, the D19 mirror semantics, the merger, or the selector.
- Audio preference persistence, UI layout, strings.
- Extracting coordinators from `PlayerVM` (`player-vm-split`).

## Acceptance criteria

- AC-1. Given a manifest-only row with a language and no API URL is picked, When the preference is saved, Then the group id with the `N:` merge prefix stripped is stored in `subtitle_url_`, together with language, forced flag and label, and a restore in a new session picks that row. (unit: VM + repository)
- AC-2. Given a language-less manifest-only row is picked, When saved, Then the preference is not stored as Off: the identity key is persisted, and after re-entry the same row is selected. (unit)
- AC-3. Given the viewer picked Off explicitly, When saved, Then `subtitle_lang_`, `subtitle_url_`, `subtitle_forced_` and `subtitle_label_` are all removed (no stale forced flag or label). (unit, repository)
- AC-4. Given a saved preference from before this change (only lang and url), When it is read and restored, Then it resolves exactly as before. (unit, resolver and repository)
- AC-5. Given the saved preference is a full subtitle and the stream has only a forced row of that language, When restoring, Then the result is Off (no match), not the forced row. (unit, resolver and VM)
- AC-6. Given two rows share a language (full and forced, or two labelled variants), When restoring, Then the saved forced flag and readable label choose the right one instead of giving Off. (unit, resolver)
- AC-7. Given a preference carrying an identity (language-less) whose row has disappeared, When restoring, Then it does not select Off by the empty-language rule. Given a preference with an empty language and no identity, Then it still means Off. (unit, resolver)
- AC-8. Given the player is rebuilt (buffer preset or Fast DNS) and the live intent differs from the saved preference, When the subtitle restore runs, Then the intent is resolved first and the saved preference is the fallback. Existing tests `PlayerVMTrackSelectionTest.kt:401-457` stay green. (unit)
- AC-9. Given a rebuild followed by an audio-less report that contains text groups, When tracks are reported, Then the intended subtitle is re-sent to the controller and nothing is persisted. This closes the deferred audio-less item. (unit, regression)
- AC-10. Given a side-loaded row (group id `<n>:0`), When saved, Then its positional group id is never written to `subtitle_url_`. (unit)
- AC-11. Given an HLS title with a manifest-only subtitle, When the viewer picks it, leaves the player and opens the same title again, Then the same subtitle is on; and also after a quality switch. (device, D-pad)
- AC-12. `check.full` passes: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` (Part 1), plus the instrumentation compile of the new tests (Part 2).
- AC-13 (Part 2). Given a subtitle is on and `switchStream` replaces the source (quality switch, next candidate, link refresh), When the call runs, Then `pendingSubtitleTrack` is null and the text type is disabled with text overrides cleared before the new media item is set, and the old stream's text override is not carried onto the new stream. (instrumented, `PlaybackController` against loopback HLS)
- AC-14 (Part 2). Given the quality switch finished and the new stream reports tracks, When the VM restores, Then the intended subtitle is on again (matched by identity, not by the old coordinates), and when the new stream lacks that row the result is Off. (instrumented, plus a VM unit test with the fake controller)
- AC-15 (Part 2). Given the instrumented tests above, When run through `make DEVICE=<serial> itest TESTS=<class>` on the leased emulator, Then they pass and the app keeps its login. The tests live where P6-A puts them (D23) and use its fixtures; if P6-A is not on master the part does not start. (instrumented)

## Sizing

L: two shippable goals. Part 1 (persistence model, rebuild order, resolver, ACs 1-12) is self-contained and unit-testable. Part 2 (item 6 and 7, ACs 13-15) changes a controller method on the playback hot path and cannot be tested without P6-A's fixtures, which are not on master.

## SPLIT

1. `subtitle-restore-model` (M, first): Part 1, option B, ACs 1-12. Independent of P6, device check AC-11 includes the quality-switch half as observation only.
2. `subtitle-switch-text-disable` (M, after P6-A lands and after item 1): option C item 6 and the instrumented tests, ACs 13-15. If the item-1 device check already shows no wrong track on a quality switch, it can still go ahead as a hardening and regression test, but the owner may then drop it.

Order reason: item 1 touches `PlayerVM`/resolver/repository, item 2 touches `PlaybackController` and test infrastructure; item 2 needs P6-A, which is a separate run's L work. P6 itself is not to be started by this run.

## Risks

- Persisted data shape (risk area): new keys must be removed on Off and on null, or a stale forced flag or label filters out every row later (AC-3).
- A non-URL stored in the URL slot: a `0:`-prefixed id stops matching once the API subtitle set changes (AC-1, AC-10). Plain equality fallback in `SubtitleTrackIdentity.kt:13-31` is safe but wants one test.
- Over-strict label filter: if KinoPub renames a manifest NAME between sessions the right row is filtered out and the result is Off. Same as upstream; stability of NAMEs is `unverified`.
- Intent-first on rebuild changes which value wins; low risk because they differ only after a `persist=false` restore.
- Device evidence: no forced-track title was ever found (sideload-merge AC-2/5/9 NOT CHECKED); forced and language-less cases rest on unit tests unless the owner names a title.
- Part 2 depends on P6-A (not started); if it is forced into this run, the run becomes P6-A plus tests and conflicts on `settings.gradle.kts`, `libs.versions.toml`, `Makefile`. Disabling text before `setMediaItem` flashes subtitles off during a switch and fully relies on the VM re-send (AC-14); a missing re-send would leave subtitles Off after every quality switch.
- Sequencing conflict with `player-vm-split` (PlayerVM save and restore) and, if it lands first, with P6 on `PlaybackController`.

## Open questions

Answered by the owner on 2026-10-14:

1. Device check scope: yes, as recommended (exit and re-enter on an HLS title with a manifest-only row, then after a quality switch; forced and language-less cases are unit-only).
2. Option C (switchStream text disable plus instrumented tests): YES, included, against the recommendation. Now Part 2, AC-13..15.
3. Intent first on rebuild, then saved: yes (AC-8).

New:

4. (Blocks: yes) How is Part 2 sequenced? Recommended: split it into its own run `subtitle-switch-text-disable`, started after this run's Part 1 and after P6-A lands; do not build fixtures here. Alternatives: (a) keep Part 2 in this run and wait for P6-A (this run stalls); (b) do item 6 now without instrumented tests (loses AC-13..15 evidence, only device observation).
5. (Blocks: no) Should the conductor ask upstream-sync-triage to prioritise P6-A now that `subtitle-upstream-port` is done? Recommended: yes, since it is the only blocker left on Part 2; P6-B and the callback probe can stay after it.

Assumed (not asked): the deferred audio-less item is closed with one test (AC-9); manifest-only identity goes into `subtitle_url_` via `preferenceKey()` rather than a new key (fewer keys, upstream tests port).
