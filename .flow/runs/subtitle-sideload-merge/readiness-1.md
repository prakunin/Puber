# Readiness - subtitle-sideload-merge, round 1

Verdict: **READY**

Inputs I read myself: the spec (`docs/superpowers/specs/2026-10-14-subtitle-sideload-merge.md`, Status approved,
AC-4 reworded and AC-9 evidence set at gate 1), the plan at `3b979408`, and the worktree code at base `a97ee2b1`.
The worktree has no code change past base: `git diff --stat a97ee2b1 HEAD -- app` is empty. I also read the upstream
sources the plan ports from with `git show b755826c:...`.

## 1. AC coverage (plan task + Test design row)

| AC | Plan task(s) | Test design rows (level) | Covered |
|----|--------------|--------------------------|---------|
| AC-1 | T5 (VM rows), T7 (factory spec), T8 (HLS side-load) | VM `tracksUpdated_hlsApiOnlyLanguage_...` (P0), `PlaybackMediaItemSpecTest.hls_...` (P0), device D-1 | yes |
| AC-2 | T2 (`isOff`, D9), T4 (merger), T5, T8 | merger `merge_carriesManifestOnlyLanguage_...` (P0), `SubtitleTrackUIStateTest` (P0), VM `selectSubtitle_manifestOnlyRow_...` (P0), device D-2 | yes |
| AC-3 | T4 | merger ports + `merge_countsManifestByFromManifestFlag_...` (P0), unknown-language cases (P1), device D-3 | yes |
| AC-4 | T1 (strip helper), T6 (selector), T8 (wiring) | selector `select_picksEachSideLoadedTrackByStrippedFormatId_...`, `select_prefersIdentityOverFullGroupId_...`, `select_usesFullGroupIdAndIndex_...`, `select_neverPicksByPosition` (P0), identity tests (P1), device D-4 | yes |
| AC-5 | T5 (`null` rows mean API rows), T7 (no `embed` filter), T8 (`null` on progressive) | VM `tracksUpdated_progressive_nullSubtitleTracks_keepsApiRows` (P0), spec `progressive_...` (P0), device D-5 | yes |
| AC-6 | T5 (re-find + re-send), T8 (guard change) | VM keep / reorder / disappear / single re-send (P0), resolver `findSameSubtitleTrackIndex_*` (P1), refresh recompute (P1) | yes |
| AC-7 | T1 (`file`), T5 (resolver `sameSubtitleLanguage`, merged restore variants) | existing restore tests plus merged variants (P0), `restoresSavedUrl_onApiLinkedManifestRow` (P0), resolver (P1), `SubtitleLinkTest` (P2), device D-8 | yes |
| AC-8 | T3 | `SubtitleLabelerTest` fork cases (P0), upstream cases and factory (P1), `PlayerUIMapperTest` | yes |
| AC-9 | T8 (no code; D15) | UNTESTABLE by owner decision Q2: Media3 trace D15, P6 hermetic test, opportunistic D-9 | yes, per the gate-1 evidence method |
| AC-10 | T7/T8 (warm-up shape kept, D13) | device D-6 (observation) | yes |
| AC-11 | every task; full gate in T8 | full gate row (P0) | yes |

Every P0 row of the Test design traceability table has a named test in a plan task, and §7 D-1..D-9 is handed to qa-device after Task 8.

## 2. Citation sample (all resolved in code)

I checked these 22 `path:line` citations. All 22 resolve to the symbol the plan names:

- `PC:67` `onTracksUpdated(audioTracks, selectedIndex)`; `PC:162` `currentStreamUrl`; `PC:393` text disabled at build.
- `PC:423-425`, `:491-493` `buildMediaItem` + `setMediaSource(..., stream.type)`; `PC:480-481` comment + `PlaybackErrorPolicy`.
- `PC:551` `track.url.isEmpty()`; `PC:576` clears `currentStreamUrl`.
- `PC:606-622` `buildMediaItem`; `PC:624-635` `subtitleMimeType`; `PC:659-681` `setMediaSource`; `PC:736` warm-up `buildMediaItem`; `PC:758-774` `setWarmUpMediaSource`.
- `PC:852-868` `notifyTracksUpdated` with the audio-only guard at `:855`; `PC:874-890` `applySubtitleTrackSelection`; `PC:892-940` `findTextTrack` / `findTextTrackBy` / `TextTrackSelection`; the no-positional comment at `:902-904`; `PC:7` `toUri` import; `PC:30` `HlsMediaSource` import.
- `VM:69` `resources`; `VM:175` resolver field; `VM:211-222` `onTracksUpdated`; `VM:362-368` `publishPreparedContent`; `VM:469-473` / `:487` refresh; `VM:526-554` restore; `VM:807-817` `applySubtitleSelection`; `VM:578` `action.index`.
- `AudioTrackPreferenceResolver.kt:24-37` matcher list, `:97` the `==` language compare.
- `model/SubtitleLabels.kt:7,8,17-40,42-53,56-61,70-79,81-90`; `PlayerUIModels.kt:26-32`; `PlayerUIMapper.kt:35-66`; `PlayerPreview.kt:36-42`; `Models.kt:428-435` `SubtitleLink`.
- Tests: `PlayerVMTestFixture.kt:86` mapper mock, `:129`, `:229-238`; `PlayerVMTrackSelectionTest.kt:26-30`, `:112-202`; `PlayerUIMapperTest.kt:80-92`, `:98-106` (locale changed after the mapper is built, so P-c is needed); `SubtitleLabelsTest.kt:15-18`, `:24-101`, `:103-107`; `FakeResourceProvider.kt:8-19`; `PlayerVMEpisodeFlowTest.kt:109-125`.
- Config: `detekt.yml:81-83, 90-92, 110-116, 756-764, 817-820`; `app/build.gradle.kts:338-342` `detektAll`; `PuberVM.kt:82-84` `updateViewState` on a `MutableStateFlow` (and `PlayerViewState.Content` is a data class, so `assertSame` works).
- Upstream `b755826c`: the file sizes match (labeler 106, merger 154, selector 104, identity 48, and the tests 33/130/525/156). Also checked: labeler `displayLanguageTag` at `UP:15,21`; merger `isFromManifest` at `UP:148-149`, private strip at `UP:151-154`, partitions at `UP:42`, `:54-56`; selector `UP:46-57`, `:60-69`, `:79-89`, `:91-101`; `UPT` selector `:62-71`; merger test `:86`, `:221`; `PlayerVMSubtitleVariantTest:187-200`; `buildSubtitleTracks` / `findHlsRendition` / the positional `getOrNull(renditionIndex)` in upstream `PlaybackController`.

Two citations are slightly off, with no effect on the work:

- The plan's `merge_groupsVariantsByLanguage_keepingManifestOrder` is upstream's `..._keepingManifestOrderWithinEachLanguage`.
- The Test design's `merge_matchesApiToRenditionByFilePath_...` is upstream's `merge_usesApiFilePathToMatchHlsRenditionUri_...`.

## 3. Build stays green, dependency order

- Each task's production code compiles on its own:
  - T1 adds unused `internal` helpers.
  - T2 updates every `SubtitleTrackUIState(` constructor. Grep finds only the mapper, the preview, `PlayerUIMapperTest` and the fixture.
  - T5's third callback parameter defaults to `null`, so `PC:867` and every two-argument test call still compile.
  - T6 and T4 are unused until they are wired.
  - T7 replaces all three `buildMediaItem` call sites.
  - T8 wires everything and runs the full gate.
- The order follows the dependencies: T1 → T2 → T3 → T4 → T5 (needs T4) → T6 → T7 → T8 (needs T5, T6 and T7). The labeler's imports from `vm` (T1) exist before T3.
- Fixture risk: from T5 on, the refresh tests call `mapper.mapSubtitleTracks`, which the mock does not stub (`relaxUnitFun` does not cover it). T5 adds the stub.

## 4. Scope, open questions, refinements P-a / P-b / P-c

- **Out of scope respected.** The plan adds no `SubtitlePreference` and no new pref keys, and leaves `PlayerPreferencesRepository` unchanged. It adds no instrumented tests, touches no f38fbcf0/dc011a81 work, and leaves DI, the screen module and Compose layout alone. Q3 and Q4 stay out.
- **Blocking questions answered.** Q1 and Q2 were answered at gate 1. Q5 (device titles) is non-blocking and needed only before verify.
- **P-a (strip only when merged): inside D5.** Media3 builds an HLS rendition id as `<GROUP-ID>:<NAME>`. An unmerged item has no merge prefix to strip, so the guard only makes D5 safe for numeric GROUP-IDs. `fromManifest = !merged || groupId.startsWith("0:")` is D5's rule: child 0 is the main source. "Merged" equals "the factory set `subtitleConfigurations`", and Task 7 sets them only when the list is non-empty, consistent with Task 8's `!subtitles.isNullOrEmpty()`.
- **P-b (identity in two steps): inside D8 and AC-4.** Both steps are "prefix-stripped source identity (format id / stable subtitle key)", each reduced with `singleOrNull`, and they come before the full group id + index and before language. No positional rule remains. The upstream test `select_prefersManifestRendition_overSideLoadedCopyOfTheSameSubtitle` exercises the reason for the split.
- **P-c (labeler per call): inside D10 and D16.** There is still no DI. D16's "plain fields" changes to per-call construction only so that `resources.locale` is read when labelling. That reason is real: `PlayerUIMapperTest.kt:98-106` changes the locale after the mapper is built. The cost is negligible (R7). Output stays deterministic, so R5's loop guard holds.

## Fix list

None blocking.

## Advisory (for the implementer and reviewers; not required for READY)

1. **Unit-test P-a.** P-a lives in `PlayerTextTracks.buildSubtitleRows`, which has no unit test, so the prefix strip and `fromManifest` derivation are checked only on device. Factor `(formatId, groupId, merged) -> (playerTrackId, fromManifest)` into a pure helper and test it, including a numeric GROUP-ID such as `"1:Russian"` in both the merged and unmerged case.
2. **Merger strip contradicts P-a.** The merger keeps upstream's "defensive strip" in `sourceIdentities` / `findExactIdentityMatch`, so it strips unmerged numeric GROUP-ID ids that P-a deliberately keeps. It is harmless today: such ids are not paths, so `sameSubtitleIdentity` falls back to equality. Reviewers should confirm it stays harmless, or drop the strip there.
3. **Disappear test needs its input pinned.** `tracksUpdated_selectedRowDisappears_goesOffAndDisablesText` must remove every row of that language. D11 rule 6 (unambiguous language) would otherwise re-find the remaining `rus` row instead of Off, and the fixture has two `rus` rows.
4. **Detekt runs only at the end.** It first runs in Task 8, and the plan expects `ComplexCondition` to possibly fire on the T2 `isOff` getter. Running `:app:detektAll` after T2 and T5 avoids collecting several fixes at the end.
5. **Stale spec wording.** "The wording needs changing (Q1)" under AC-4 in the spec's Design section is stale. It is cosmetic, and the spec is not changed in this run.
