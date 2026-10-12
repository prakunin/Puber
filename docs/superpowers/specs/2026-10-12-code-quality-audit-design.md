# Code quality audit

Status: draft | approved
Date: 2026-10-12
Slug: code-quality-audit
Kind: refactor
Size: L
Route: standard

<!--
Each section has one owner; nobody else edits it. The conductor commits this
file in the task's worktree after every phase.
  analyst        Problem, Acceptance criteria, Out of scope, Plan (quick route)
  architect      Design
  ux-designer    Visual
  test-designer  Test design
  anyone         Open questions (append only; the user answers)
Cite code as [Source: path:line]. A claim with no source is a guess.
-->

## Problem

The user asked for an analysis of the code and its structure: places where
hand-written code duplicates a ready library or platform solution, SOLID,
best practices, and performance. It is a broad request, not a defect.

Survey (triage only, not findings):
- `:app` has 386 Kotlin files, about 51k lines, in `core/` (cross-cutting),
  `data/` (api, cache, db, preferences, repository), `domain/` and
  `ui/feature/` [Source: app/src/main/java/com/kino/puber].
- Largest files, the likely SOLID hotspots: `PlayerVM.kt` 1859 lines,
  `KinoPubApiClient.kt` 1195, `PlaybackController.kt` 990,
  `DetailsScreenContent.kt` 945, `DeviceSettingsContent.kt` 816, `HistoryVM.kt`
  805, `FlowComponent.kt` 785, `HomeVM.kt` 705 [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1].
- Hand-rolled candidates to check against libraries: `Paginator.kt` (604 lines)
  with no `androidx.paging` in the version catalogue
  [Source: app/src/main/java/com/kino/puber/core/paginator/Paginator.kt:1;
  gradle/libs.versions.toml]; `TypedTtlCache.kt` (325),
  `CachedFeed.kt` (607), `ContentCacheRepository.kt` (634)
  [Source: app/src/main/java/com/kino/puber/data/cache/ContentCacheRepository.kt:1];
  `ContentUriCodec.kt` (178); `IdGenerator.kt`; `SessionEventBus.kt`.
  The catalogue already holds Room 3, Coil 3, Media3, OkHttp, Ktor
  [Source: gradle/libs.versions.toml:14-118]. Whether each candidate is really a
  duplicate is unverified.
- Earlier work not to repeat: the 2026-09-08 memory and performance audit and
  its plan [Source: docs/superpowers/evidence/2026-09-08-memory-and-performance-audit.md:7];
  deferred items P3, M4, P5, P8-P12 sit in board row `perf-deferred`
  [Source: docs/superpowers/BOARD.md:28]. Detekt config exists
  [Source: config/detekt/detekt.yml] with a baseline.

Deliverable of this task: a prioritised, sourced audit report plus a small set
of safe, behaviour-preserving fixes applied in this branch (user decision,
Q1). Everything risky, behaviour-changing, or adding a dependency becomes a
follow-up board row.

## Acceptance criteria

- **AC-1** Given the finished task, when `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md` is opened, then it has one section each for: Structure, Reinvented wheels (hand-written vs library/platform), SOLID, Best practices, Performance.
- **AC-2** Given any finding in the report, when read, then it has an ID, a `[Source: path:line]` that points at existing code on the audited commit, a severity (high/medium/low), an effort (S/M/L), and a recommended approach (named library or platform API, or the refactor).
- **AC-3** Given the "Reinvented wheels" section, when read, then it states a verdict (replace / keep / defer) with a reason for each of `Paginator`, `TypedTtlCache`, `CachedFeed`/`ContentCacheRepository`, `ContentUriCodec`, `IdGenerator` and `SessionEventBus`, and names a replacement only if it is, or can be, added through `gradle/libs.versions.toml`.
- **AC-4** Given the 8 files over 700 lines named in Problem, when the SOLID section is read, then each has either a finding (which responsibility to split out) or an explicit "acceptable" with a reason.
- **AC-5** Given the Performance section, when compared with the 2026-09-08 audit and `perf-deferred`, then no finding repeats an item already fixed or deferred there; overlaps are cross-referenced by ID instead. Any finding not backed by a measurement is marked "static reading, unmeasured".
- **AC-6** Given the report, when its summary table is read, then findings are grouped in the order libraries (reinvented wheels), SOLID, performance (best practices after them), within each group by severity then effort, and proposed follow-up board rows (slug, one-line scope, kind, size, dimension) group them so each row is one reviewable change.
- **AC-7** Given the task branch diff against master, when listed, then production code changes only for fixes the report marks "fixed in this task"; each such fix cites its finding ID, is behaviour-preserving, has its own commit, and `gradle/libs.versions.toml` and `build.gradle.kts` dependencies are unchanged (library replacements stay follow-up rows).
- **AC-8** Given the report, when the detekt result is read, then it records the `./tools/agentw` detekt run on the audited commit (counts per rule family) and the count after the fixes.
- **AC-9** Given the final branch, when the static gate runs through `./tools/agentw` (compile `:app:compileDevDebugKotlin`, unit tests, detekt), then compile and tests pass and detekt reports no more findings than the baseline on master.
- **AC-10** Given the fixes touch runtime code, when qa runs the smoke paths those files reach (named per fix in the evidence) on a leased emulator, then app launch and those paths behave as on master with no crash, and the evidence file records them; if no fix touches runtime code, the evidence states that and no device is used.
- **AC-11** Given any "dependency recommended" finding, when read, then its reason covers TV focus behaviour and the paging model (`docs/architecture/paging-and-filters.md`), and states that adding it is a follow-up decision.


## Out of scope

- Risky or behaviour-changing refactors; they become follow-up rows.
- Adding or upgrading dependencies (recommendations only).
- Splitting the large classes (PlayerVM, KinoPubApiClient, etc.); follow-up rows.
- Formatting sweeps and mass renames.
- Re-auditing memory and performance items already covered by
  `memory-and-performance-fixes` and `perf-deferred`.
- Profiling or frame-stat measurement; perf findings stay static unless a
  follow-up perf row measures them.
- Test-suite quality, UI look and consistency, security/credential review.
- Upstream-sync triage (its own row).

## Design

The deliverable is the report
`docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md` (AC-1..AC-6,
AC-8 baseline, AC-11), written on audited commit `36cda915`. Its `app/` is
identical to master `4e18338e`. This section covers only the in-branch fixes
(AC-7, AC-9, AC-10). Finding IDs refer to the report.

### Decisions

| Question | Decision | Why |
|---|---|---|
| Which findings are fixed on this branch? | Four, one commit each, in this order: **F1** RW-5 (+ STR-4 `IdGenerator` sentence), **F2** BP-2, **F3** SOLID-10, **F4** RW-7. | Each is S effort, adds no dependency, splits no class and changes no observable output. F1 and F2 delete code with zero production callers. F3 and F4 merge byte-identical copies. |
| Why not more? | Every other finding changes behaviour (BP-3, BP-7, PERF-5), needs a product decision (BP-1), touches a build file (BP-4, AC-7), is a split (SOLID-*), or lacks a test net (BP-6 / PERF-2: `PaginatorReducerTest` does not cover the page merges). | Spec Out of scope; keeps the review small. |
| `PagingListState`/`PagingColumn` and `PanelFocusRequester.kt` are dead too. Delete them here? | No. | `upstream-sync-triage` (`b5bd1cca`) and `player-overlays-port` already delete them. Doing it twice only produces merge conflicts. |
| `ContentFilter` is dead, and `paging-and-filters.md` documents it as the filter model. Fix here? | No. Report STR-4, follow-up `dead-code-sweep`. | Deleting it means rewriting an architecture section and possibly overruling a planned filter feature. The user should choose. |
| Where does the shared clock formatter live? | New `ui/feature/player/model/PlaybackClock.kt`: `internal fun formatPlaybackClock(positionMs: Long): String`. `PlayerUIMapper.formatTime` keeps its signature and delegates to it. | VM tests mock `mapper.formatTime` [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTestFixture.kt:122], so the mapper API must stay. The component already imports from `player/model`. |
| Where does the shared `toDialogState()` live? | `core/ui/uikit/model/ApiDomainDialogState.kt`, as `internal fun ApiDomainState.toDialogState(): ApiDomainDialogState`. | Next to the type it builds. `core/ui` already imports `domain` [Source: app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/DetailsPrefetchSurfaceState.kt:9]. Both types are `internal` in `:app`. |
| Recommend a new dependency? | No (AC-11 then has no finding to apply to). | RW-1..RW-4 were checked against Paging 3, LruCache, Caffeine/Guava, Store5 and `android.net.Uri`. Each loses on TV focus, the paging model or deep-link strictness (report, Reinvented wheels). |
| Detekt baseline file | Leave `config/detekt/detekt-baseline.xml` untouched (it is empty). | AC-9 compares counts. Deleting code can only lower them, and a rewritten baseline would hide new issues. |
| Device verification | Required (AC-10): F3 and F4 reach runtime UI. F1 and F2 have no runtime reach and are covered by app launch. | AGENTS.md: ViewModel state and TV UI changes need the device. |

### Changes by layer

**API / data (F2).**
- `data/api/TmdbApiClient.kt`: delete `IMDB_TITLE_ID_REGEX` and `normalizedImdbTitleIdOrNull` [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:27], plus `findMediaByImdbId` [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:84], `getMovieCredits` [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:105], `getTvAggregateCredits` [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:113] and `getConfiguration` [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:121]. Also delete the five model imports they used [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:4].
- These stay, because `EpisodeScheduleRepository` [Source: app/src/main/java/com/kino/puber/data/repository/EpisodeScheduleRepository.kt:55] and `SkipSegmentService` [Source: app/src/main/java/com/kino/puber/data/repository/SkipSegmentService.kt:62] use them: `findTvByImdbId`, `getTvDetails`, `getTvSeasonDetails`, `findByImdbId` and `withTmdbPrefix`.
- Delete `data/api/models/TmdbCastModels.kt` [Source: app/src/main/java/com/kino/puber/data/api/models/TmdbCastModels.kt:1] and `data/api/config/TmdbImageConfig.kt` [Source: app/src/main/java/com/kino/puber/data/api/config/TmdbImageConfig.kt:3].
- Test: delete `castAndConfigurationEndpoints_preservePathsLocalizationAndDecoding` [Source: app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt:135]. The other tests in that file stay and still cover the kept endpoints.

**Repository.** None.

**Core (F1, F3).**
- Delete `core/system/IdGenerator.kt` [Source: app/src/main/java/com/kino/puber/core/system/IdGenerator.kt:8].
- Add the `toDialogState()` extension to `core/ui/uikit/model/ApiDomainDialogState.kt` [Source: app/src/main/java/com/kino/puber/core/ui/uikit/model/ApiDomainDialogState.kt:6]. It maps `domain → currentDomain` and `customDomain → customDomain`, and leaves `isDetecting` at its default `false`, exactly as the three copies do today.

**ViewModel state and actions (F3).** In each VM, delete the private copy and import the shared extension. No state shape, action or call site changes.
- `HomeVM` [Source: app/src/main/java/com/kino/puber/ui/feature/home/vm/HomeVM.kt:673]
- `AuthVM` [Source: app/src/main/java/com/kino/puber/ui/feature/auth/vm/AuthVM.kt:291]
- `DeviceSettingsVM` [Source: app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVM.kt:505]

**Screen and DI module.** None. None of the deleted classes is registered in Koin [Source: app/src/main/java/com/kino/puber/data/di/modules.kt:52], and no screen `buildModule` changes.

**Compose content and model (F4).**
- New `ui/feature/player/model/PlaybackClock.kt` holding the body of `PlayerUIMapper.formatTime` [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:215]: `Locale.ROOT`, `%d:%02d:%02d` when hours > 0, otherwise `%d:%02d`. The time constants are named (no `MagicNumber`).
- `PlayerUIMapper.formatTime` becomes `= formatPlaybackClock(positionMs)`. Its file-private time constants then have no use and go [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:16].
- `PlayerProgressBar.kt`: delete `formatTimeMs` [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerProgressBar.kt:325] and its three constants [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerProgressBar.kt:355]. The two `remember` call sites [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerProgressBar.kt:59] and `formatRemainingTime` [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerProgressBar.kt:337] call `formatPlaybackClock` instead.
- The `Int` constants in the component and the `Long` constants in the mapper give the same result for every `Long` input, because the `Int` divisor is widened.

**Resources.** None.

**Docs (F1).** Delete the `IdGenerator` sentence from `docs/architecture/navigation.md` [Source: docs/architecture/navigation.md:61].

**Tests the test-designer should consider.** Pin `formatPlaybackClock` at 0, 59 999, 60 000, 3 599 999, 3 600 000 and 36 000 000 ms. Pin `ApiDomainState.toDialogState()` with and without `customDomain`. Run the existing `PlayerUIMapperTest` [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/model/PlayerUIMapperTest.kt:1] and the VM suites unchanged.

### Device verification per fix (AC-10)

| Fix | Smoke path on a leased emulator | Expected (as on master) |
|---|---|---|
| F1 | App launch to Home | no crash. Nothing else reaches the deleted class. |
| F2 | App launch. The episode schedule screen cannot be exercised from a worktree build (no TMDB token, see AGENTS.md); record that. | no crash |
| F3 | Settings → Network → open the API-domain dialog, check the current domain and the prefilled custom domain, close with Back. Auth and the Home error state use the same function; a unit test covers them. | same text and prefill as master |
| F4 | Play any title, show controls: elapsed (`m:ss` / `h:mm:ss`) and remaining (`-m:ss`) times. Seek forward once and read the seek indicator time. Reopen the title and read the resume dialog time. | same strings as master |

### Alternatives rejected

- Replace `Paginator` with `androidx.paging`: Paging 3 cannot mutate items in place without invalidating and reloading, which moves TV focus. `PagingData` also cannot express the ten-state model (report RW-1).
- Replace `CachedFeed` with Store5: its invalidation model loses the documented per-key epoch races (report RW-3).
- Use `DateUtils.formatElapsedTime` for F4: it changes visible text (`05:07` instead of `5:07`, and locale digits).
- Delete all 36 dead `KinoPubApiClient` endpoints now: this needs a product decision about which to keep (BP-1).
- Collapse the duplicate `Paginator` branches now: there is no test net for those merges, and every paged list is affected (BP-6).
- Regenerate the detekt baseline: it would hide findings instead of counting them.

### Risks

1. **Detekt gate cannot run on this host today (tooling, highest; blocks AC-8 and AC-9).** Five `./tools/agentw :app:detektAll` attempts lost the Gradle daemon to JVM faults in unrelated internals: C2, `SymbolTable::grow` twice, a VM-thread hang, and a G1 worker. This happened on both OpenJDK 21.0.12 and JBR 21.0.6, including a run with conservative flags. That points at the host (swap was ~90 % used), not the project. The same task gave 0 findings in neighbouring worktrees on 2026-10-10/11. The report's Detekt section lists every attempt and leaves the "Before" row open. qa must take the before count on an unchanged `app/` before F1 is committed, and the after count once F4 is done.
2. **Merge conflicts with in-flight branches (low).** `player-overlays-port` and `upstream-sync-triage` change other files. The fixes avoid `PagingListState.kt`, `PanelFocusRequester.kt`, `PlayerScreenContent.kt` and `PuberApp.kt` on purpose.
3. **Formatter drift (low).** A typo in the moved format string would change on-screen times. The pinned unit test and the F4 device row cover it.
4. **Focus and D-pad (none).** No focus requester, modifier or key handler changes.
5. **Koin scope lifetime (none).** No module, scope or VM registration changes.
6. **Recomposition (none).** `formatPlaybackClock` is a pure top-level function called inside the same `remember(currentPosition)` blocks; nothing new is read in composition.
7. **Main-thread work and memory (none).** The arithmetic is identical. Deleted code was never loaded.
8. **Cache invalidation (none).** No cache code changes.
9. **Baseline profile (low).** None of the deleted symbols appears in `app/src/main/generated/baselineProfiles/*.txt` (grep: 0). The new `PlaybackClockKt` class is not in the profile until the next regeneration, which is a negligible cold-path cost on first player open.

## Visual

<!-- ux-designer. Visual and perf routes only when a screen changes. -->

## Test design

Kind is refactor: no defect, so no failing-first test. The substitute is a
characterisation test written and run green on master before each fix, and kept
unchanged after it. Tests assert visible output (strings, VM state), not private calls.
Existing style reused: `PlayerUIMapperTest` (mockk `Context`, plain JUnit 5),
`AuthVMTest` / `HomeVMTest` / `DeviceSettingsVMMenuSectionsTest` (mocked
`ApiDomainInteractor.getState()` returning `ApiDomainState`).

### Risks

| Risk | P | I | Score | Mitigation |
|---|---|---|---|---|
| R1 Detekt cannot run (JVM crashes), so AC-8 "before" and AC-9 are unproven | 3 | 3 | 9 | Gate, not a test. See "Detekt gate plan" below. Without a count, AC-9 stays open and verify blocks. |
| R2 Formatter drift in F4 (typo in format string, Int vs Long constants, delegate wiring) changes on-screen times | 2 | 3 | 6 | Unit: pin `formatTime` before F4 via `PlayerUIMapper`, add `formatPlaybackClock` test after. Device row D3. |
| R3 F3 shared `toDialogState()` maps a field wrongly (e.g. drops `customDomain`, sets `isDetecting`) in one of 3 VMs | 2 | 3 | 6 | Unit: direct test of the extension; VM-state assertions in Auth, Home and Settings. Device row D2. |
| R4 F2 deletes a TMDB member still used (compile or runtime break in schedule / skip segments) | 1 | 3 | 3 | Compile gate plus kept `TmdbApiClientTest` cases for the retained endpoints. Runtime unreachable on worktree build (no TMDB token): recorded, accepted. |
| R5 F1 `IdGenerator` has a hidden caller (reflection, test) | 1 | 2 | 2 | Compile gate plus `grep -rn IdGenerator app/src`. Accepted with that note. |
| R6 Existing VM tests that mock `mapper.formatTime` break because the signature moved | 1 | 2 | 2 | Signature kept. The VM suites run unchanged. Accepted. |
| R7 AC-7 breach: dependency or build file touched | 1 | 2 | 2 | `git diff master -- gradle/libs.versions.toml '*.gradle.kts'` must be empty. Checked in qa. |
| R8 Report claims wrong: `[Source:]` line points at nothing (AC-2) | 2 | 2 | 4 | Scripted check (see AC-2 row), not a unit test. |
| R9 Focus/D-pad, Koin scope, recomposition | 1 | 2 | 2 | Design: no change in these areas. Accepted. D-pad steps in device checks only confirm no regression. |

Only R1, R2 and R3 reach score 6.

### Characterisation tests (written first, green on master)

Write each on master's code, run it, and commit it before the fix. After the
fix the same file must pass with no edit to its expectations.

- **T1** `PlayerUIMapperTest.formatTime_*` (extend existing file). Calls `mapper.formatTime(ms)`. Table: 0 -> `0:00`; 999 -> `0:00`; 1_000 -> `0:01`; 59_999 -> `0:59`; 60_000 -> `1:00`; 3_599_999 -> `59:59`; 3_600_000 -> `1:00:00`; 36_000_000 -> `10:00:00`; 3_661_000 -> `1:01:01`; 360_000_000 -> `100:00:00` (hours not capped). Locale check: set `Locale.setDefault(Locale("ar"))` in the test, restore in `finally`, and assert the digits stay ASCII (`Locale.ROOT` guard against the rejected `DateUtils` route).
- **T2** `PlaybackClockTest.formatPlaybackClock_*` (new, `ui/feature/player/model/`). Same table, calling `formatPlaybackClock` directly. Written together with F4, so it cannot run on master. Equivalence with T1 is the proof: the two tables must be identical, and T1 must still pass with the mapper delegating.
- **T3** Remaining-time wrapper. `formatRemainingTime` in `PlayerProgressBar.kt` is private. It is not unit-testable without making it internal, which would be a production change outside the spec. Cover it by device row D3 (`-m:ss`, and `-0:00` when duration is 0). Accepted as device-only.
- **T4** `AuthVMTest`, new test `apiDomainDialog_exposesCurrentAndCustomDomain`. Stub `getState()` with `ApiDomainState("api.example", "custom.example")` and with `customDomain = null`, then assert the state `apiDomainDialog == ApiDomainDialogState(currentDomain = "api.example", customDomain = ..., isDetecting = false)`. Uses the existing `apiDomainInteractor` mock. Which action exposes the dialog (line 194/220 of `AuthVM`) is for the implementer to read; assert only the resulting state.
- **T5** `HomeVMTest`, same assertion via the error-state path that calls `updateApiDomainDialog(...)` (`HomeVM.kt:528`), with and without `customDomain`.
- **T6** `DeviceSettingsVMMenuSectionsTest` (or a new `DeviceSettingsVMApiDomainTest`), assert the initial `viewState.apiDomain` for both domain shapes (`DeviceSettingsVM.kt:65`).
- **T7** `ApiDomainDialogStateTest.toDialogState_*` (new, `core/ui/uikit/model/`), direct test of the shared extension: with custom, without custom (`initialInput == ""`), `isDetecting == false`. Written with F3.

T4-T6 are state-level characterisations: they pass on master (private copies) and
after F3 (shared one). If one cannot reach the dialog state without heavy setup,
downgrade it to the T7 test plus device row D2, and note it in the plan.

F1 and F2 need no new test. F2 deletes `castAndConfigurationEndpoints_preservePathsLocalizationAndDecoding` with the code. The remaining `TmdbApiClientTest` cases must stay green.

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | script | P0 | `grep -c '^## ' report` lists Structure, Reinvented wheels, SOLID, Best practices, Performance | five required sections exist |
| AC-2 | script | P0 | for every finding ID: extract each `[Source: path:line]`, `test -f path` and `line <= wc -l`; check severity and effort fields present | sources exist on the audited commit; fields complete |
| AC-3 | review | P0 | read "Reinvented wheels": six named items each have replace/keep/defer plus reason; any named library exists in `libs.versions.toml` or is marked "to add" | verdict coverage |
| AC-4 | review | P1 | check the 8 files (PlayerVM, KinoPubApiClient, PlaybackController, DetailsScreenContent, DeviceSettingsContent, HistoryVM, FlowComponent, HomeVM) each have a finding or "acceptable" plus reason | all 8 covered |
| AC-5 | review | P1 | cross-check Performance IDs against `2026-09-08-memory-and-performance-audit.md` and `perf-deferred` (P3, M4, P5, P8-P12); each unmeasured finding says "static reading, unmeasured" | no repeats; labelled |
| AC-6 | review | P1 | summary table order and follow-up rows list (slug, scope, kind, size, dimension) | ordering and row shape |
| AC-7 | script | P0 | `git diff --stat master..HEAD -- app/src/main`: only files from F1-F4; `git diff master..HEAD -- gradle/libs.versions.toml '*.gradle.kts'` empty; `git log` shows one commit per fix citing its ID | scope, no dependency change, commit shape |
| AC-7 | unit | P0 | T1 (before F4), T2, T7, T4 (before F3) | the two merges are behaviour-preserving |
| AC-8 | tooling | P0 | `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:detektAll` before F1 and after F4, counts per rule family recorded in the report | before/after counts exist |
| AC-9 | tooling | P0 | `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`, `:app:testDevDebugUnitTest`, `:app:detektAll`; detekt <= baseline on master | static gate green |
| AC-9 | unit | P0 | full unit suite incl. `PlayerVM*Test`, `PlayerUIMapperTest`, Auth/Home/DeviceSettings VM suites, `TmdbApiClientTest` unchanged except the one deleted case | no regression from F1-F4 |
| AC-10 | device | P0 | D1-D4 below, recorded in `...-device.md` | launch and paths behave as master |
| AC-11 | review | P2 | design says no dependency recommended; if the report has any "dependency recommended" finding, check it states TV focus, paging model and "follow-up decision" | wording present or n/a |

Priority totals (table rows): P0 = 9, P1 = 3, P2 = 1. Unit tests: P0 = T1, T2, T4, T7; P1 = T5, T6; T3 is device-only (D3).

### Detekt gate plan (R1, AC-8, AC-9; Q5)

1. Before any production edit, check host memory (`free -m`, swap use). Do not start while swap is above about 50 percent. A reboot, or stopping other Gradle daemons (`./tools/agentw --stop` if present, otherwise ask), is the fix. This is a host precondition, not a project change.
2. Run with `AGENT_GRADLE_SERIAL=1` so no other worktree builds in parallel. Use a per-run log and keep only counts per rule family in the evidence (AGENTS.md: no raw logs).
3. If the crash repeats, the fallback in Q5 is the main checkout's `./gradlew :app:detektAll`. It is valid for the "before" count only, because master `app/` is identical to the audited commit. Needs the user's answer to Q5 first. The "after" count must come from the branch, so it has no fallback. If it cannot run, AC-9 is reported as unverified, not passed. Do not edit `detekt-baseline.xml` to get around it.
4. If neither runs, the compile gate and unit suite still run (they use other tasks). Qa records "detekt: not run, host fault" in the verdict. Merge is a user decision.
5. New code must avoid known detekt hits by hand: no magic numbers in `PlaybackClock.kt` (named constants), no unused imports left in `PlayerProgressBar.kt`, `PlayerUIMapper.kt` and the three VMs (`Locale`, `ApiDomainState`, `ApiDomainDialogState` imports), no unused private constants after F4. Grep for them before commit, because the gate may not run.

### Order of work

1. Add T1 (and T4, T5, T6 where feasible) on master, run green, commit.
2. Detekt "before" count (see plan above).
3. F1, F2 (compile, `TmdbApiClientTest`).
4. F3 with T7, rerun T4-T6.
5. F4 with T2, rerun T1.
6. Detekt "after", full unit suite, device checks.

### Device checks

Leased emulator, dev APK installed through `tools/android-apk-install-preserve`, adb with `-s`. No uninstall or clear. Record the serial and results in `...-device.md`, without raw logs.

1. **D1, app launch (F1, F2).** Cold start the app. Expect Home renders with rows and focus on the first card, DPAD_RIGHT/DPAD_DOWN move focus. Must not happen: crash, ANR, `NoClassDefFoundError` in a filtered crash check, focus lost on start. Record: the episode schedule screen is not exercised (no TMDB token in a worktree build).
2. **D2, API-domain dialog (F3).** Home, open the Settings tab with DPAD, go to Network, press DPAD_CENTER on the API-domain item. Expect the dialog shows the same current domain and the same prefilled custom value (empty when none) as master. Press BACK. Expect dialog closes and focus returns to the same item. Must not happen: empty or swapped domain text, spinner shown (`isDetecting` true), focus lost, crash. If the account is logged in, the Auth path cannot be reached; T4 covers it.
3. **D3, player clock (F4).** Open a title and start playback, press DPAD_CENTER to show controls. Expect elapsed time as `m:ss` (or `h:mm:ss` after an hour), remaining as `-m:ss`. Compare to the same title on a master build if one is installed, or against the known title duration. Press DPAD_RIGHT once to seek. Expect the seek indicator shows `m:ss` of the target. Must not happen: `00:05`-style padding of the first field, missing minus sign, `-0:00` while the duration is known, wrong digits.
4. **D4, resume dialog (F4).** Leave playback mid-title (BACK), reopen the same title. Expect the resume dialog time to match where you stopped, same format as D3. Choose either button with DPAD_CENTER. Expect playback starts without crash. Must not happen: `0:00` when a position was saved, stale value from a previous title.

If an emulator is not available, say so and ask. Compile and unit results do not replace these (AGENTS.md).

### Baseline (perf route)

n/a. Standard route, no performance claim; all perf findings in the report are static reading.

## Plan

<!-- analyst, quick route only; otherwise see docs/superpowers/plans/. -->

## Open questions

1. Should this task only deliver the report (proposed), or also apply a small set of safe fixes? Proposed: report only, fixes as follow-up rows.
2. Which dimension matters most, so findings are ordered and follow-up rows opened in that order: structure/SOLID, replacing hand-written code with libraries, or performance? Proposed: libraries first, then SOLID of the 8 big files, then performance.
3. Is adding a dependency (for example `androidx.paging` in place of the 604-line `Paginator`) acceptable to recommend, given the project's TV focus and paging model in `docs/architecture/paging-and-filters.md`?
4. May the audit run detekt through `./tools/agentw` in the worktree (shared Gradle home, `AGENT_GRADLE_SERIAL=1`), or should it rely on static reading only?

Answers (user, 2026-10-12):
- Q1 → answered: report **plus** a small set of safe, behaviour-preserving fixes in this branch (so device verification and code review apply).
- Q2 → answered: order is libraries (reinvented wheels) → SOLID of the big files → performance.
- Q3 → answered: yes, the report may recommend new dependencies through `gradle/libs.versions.toml`, with a reason that covers TV focus and the paging model; the decision to add one belongs to the follow-up row.
- Q4 → answered: yes, run detekt through `./tools/agentw` in the worktree with `AGENT_GRADLE_SERIAL=1`.

5. (architect, 2026-10-12) The detekt "before" count for AC-8 could not be taken: five runs crashed the JVM on this host (report, Detekt). Should qa re-run it once the host is stable (reboot, or memory and swap freed), or may it be taken from the main checkout with `./gradlew :app:detektAll`, since master's `app/` is identical? Not blocking for plan; blocking for verify.
