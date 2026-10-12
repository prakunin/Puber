# Code quality audit — plan

**Spec:** `docs/superpowers/specs/2026-10-12-code-quality-audit-design.md`
**Report:** `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md`
**Goal:** Apply the four behaviour-preserving fixes F1-F4 the audit report marks
"fix in this task", each behind characterisation tests and detekt counts taken
before and after, then mark them fixed in the report with their commit hashes.

## Global constraints

- Worktree: `/home/user1/orca/workspaces/Puber/code-quality-audit` (below:
  `<wt>`). Run every command as `cd <wt> && …`.
- Builds in the worktree: `AGENT_GRADLE_SERIAL=1 ./tools/agentw <task>`, never
  `./gradlew`. Other worktrees build in parallel on the shared
  `~/.gradle-agents` home. **Never run `--stop`** and never kill a Gradle daemon
  you did not start. The single exception to "no `./gradlew`" is the detekt
  "before" fallback in Task 2, which runs in the **main checkout** (user
  answer Q5 in the spec).
- One task = green build + green tests of the touched classes + one commit.
  Commit message: one imperative English sentence, no prefixes, **no
  `Co-Authored-By` line**. Fix commits name their finding ID(s) in the message.
- Do not touch `gradle/libs.versions.toml`, any `*.gradle.kts`, or
  `config/detekt/detekt-baseline.xml` (AC-7, spec Decisions).
- Do not touch `PagingListState.kt`, `PagingColumn`, `PanelFocusRequester.kt`,
  `PlayerScreenContent.kt`, `PuberApp.kt`, or `ContentFilter` (in-flight
  branches own them; spec Decisions and Risk 2).
- Raw Gradle/JVM logs and `hs_err_pid*.log` files stay outside the worktree
  and out of Git. Only counts go into the report.
- The plan is self-contained: the implementer reads this file, the spec, and
  the files cited below — nothing else.

Shared commands (referred to by name below):

- **COMPILE**: `cd <wt> && AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin :app:compileDevDebugUnitTestKotlin`
- **TEST `<pattern…>`**: `cd <wt> && AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<pattern>' [--tests '<pattern>' …]`
- **FULLTEST**: `cd <wt> && AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest`
- **DETEKT**: `cd <wt> && AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:detektAll --continue`
  (`detektAll` = `detektDevDebug` + `detektDevDebugUnitTest`
  [Source: app/build.gradle.kts:338]; reports land in
  `app/build/reports/detekt/devDebug.md` and `devDebugUnitTest.md`).

---

### Task 1: Characterisation tests T1, T4, T5, T6 on unchanged production code

**Covers:** AC-7 (behaviour-preservation net for F3 and F4), test design T1, T4, T5, T6
**Files:**
- Test (modify): `app/src/test/kotlin/com/kino/puber/ui/feature/player/model/PlayerUIMapperTest.kt`
  — existing mapper fixture with a mocked `Context` [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/model/PlayerUIMapperTest.kt:12]
- Test (modify): `app/src/test/kotlin/com/kino/puber/ui/feature/auth/vm/AuthVMTest.kt`
  — existing `apiDomainInteractor` relaxed mock and `createVM()` [Source: app/src/test/kotlin/com/kino/puber/ui/feature/auth/vm/AuthVMTest.kt:48]
- Test (modify): `app/src/test/kotlin/com/kino/puber/ui/feature/home/vm/HomeVMTest.kt`
  — `@BeforeEach setup()` and `createVM()` [Source: app/src/test/kotlin/com/kino/puber/ui/feature/home/vm/HomeVMTest.kt:66] [Source: app/src/test/kotlin/com/kino/puber/ui/feature/home/vm/HomeVMTest.kt:408]
- Test (modify): `app/src/test/kotlin/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVMMenuSectionsTest.kt`
  — `createVM(startupTab)` stubs `apiDomainInteractor.getState()` inside [Source: app/src/test/kotlin/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVMMenuSectionsTest.kt:107]
- No production file changes.

**Consumes (existing code, read-only):**
- `PlayerUIMapper.formatTime(positionMs: Long): String` [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:215]
- `ApiDomainState(domain: String, customDomain: String?)` [Source: app/src/main/java/com/kino/puber/domain/interactor/api/ApiDomainInteractor.kt:18]
- `ApiDomainDialogState(currentDomain, customDomain, isDetecting = false)` [Source: app/src/main/java/com/kino/puber/core/ui/uikit/model/ApiDomainDialogState.kt:6]
- `PuberVM.testStateValue` [Source: app/src/main/java/com/kino/puber/core/ui/PuberVM.kt:56], `PuberVM.onAction` [Source: app/src/main/java/com/kino/puber/core/ui/PuberVM.kt:135]
- Auth: `AuthAction.OpenApiDomainDialog` [Source: app/src/main/java/com/kino/puber/ui/feature/auth/model/AuthAction.kt:6] → `openApiDomainDialog()` [Source: app/src/main/java/com/kino/puber/ui/feature/auth/vm/AuthVM.kt:219]; initial state is `AuthViewState.Loading()` [Source: app/src/main/java/com/kino/puber/ui/feature/auth/vm/AuthVM.kt:40] whose `apiDomainDialog` field is at [Source: app/src/main/java/com/kino/puber/ui/feature/auth/model/AuthViewState.kt:11].
- Home: `HomeAction.OpenApiDomainDialog` [Source: app/src/main/java/com/kino/puber/ui/feature/home/model/HomeAction.kt:6] → `openApiDomainDialog()` [Source: app/src/main/java/com/kino/puber/ui/feature/home/vm/HomeVM.kt:527]; `HomeViewState.apiDomainDialog` is on the sealed interface [Source: app/src/main/java/com/kino/puber/ui/feature/home/model/HomeViewState.kt:10].
- Settings: `initialViewState` maps `getState().toDialogState()` [Source: app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVM.kt:64]; `DeviceSettingsActions.OpenApiDomainDialog` [Source: app/src/main/java/com/kino/puber/ui/feature/device/settings/model/DeviceSettingsActions.kt:32] → `openApiDomainDialog()` [Source: app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVM.kt:444]; field `DeviceSettingsViewState.apiDomain` [Source: app/src/main/java/com/kino/puber/ui/feature/device/settings/model/DeviceSettingsViewState.kt:12].

**Produces:** the clock table and the dialog-state assertions Tasks 5 and 6 must keep green with no edit.

- [x] **T1** in `PlayerUIMapperTest` add:
  - `formatTime_matchesPlaybackClockTable`: for each pair assert
    `assertEquals(expected, mapper.formatTime(ms), "ms=$ms")`:
    `0L→"0:00"`, `999L→"0:00"`, `1_000L→"0:01"`, `59_999L→"0:59"`,
    `60_000L→"1:00"`, `3_599_999L→"59:59"`, `3_600_000L→"1:00:00"`,
    `36_000_000L→"10:00:00"`, `3_661_000L→"1:01:01"`, `360_000_000L→"100:00:00"`.
  - `formatTime_keepsAsciiDigitsUnderArabicDefaultLocale`: save
    `Locale.getDefault()`, `Locale.setDefault(Locale.forLanguageTag("ar-EG-u-nu-arab"))`
    (do not use the deprecated `Locale(String)` constructor), assert
    `mapper.formatTime(3_661_000L) == "1:01:01"` and `mapper.formatTime(65_000L) == "1:05"`,
    restore the saved default in `finally`. Add `import java.util.Locale`.
- [x] **T4** in `AuthVMTest` add two tests. In each: `val vm = createVM()`, then
  re-stub **after** `createVM()` (it stubs `getState()` itself)
  `every { apiDomainInteractor.getState() } returns ApiDomainState(...)`, then
  `vm.onAction(AuthAction.OpenApiDomainDialog)` without `testOnStart()`, then
  assert `(vm.testStateValue as AuthViewState.Loading).apiDomainDialog`:
  - `apiDomainDialog_exposesCurrentAndCustomDomain`: state `ApiDomainState("api.example", "custom.example")` →
    `ApiDomainDialogState(currentDomain = "api.example", customDomain = "custom.example", isDetecting = false)`.
  - `apiDomainDialog_leavesCustomDomainEmpty_whenNoneIsSet`: `ApiDomainState("api.example", null)` →
    `ApiDomainDialogState("api.example", null, false)` and `initialInput == ""`.
  Imports to add: `com.kino.puber.ui.feature.auth.model.AuthAction`,
  `com.kino.puber.core.ui.uikit.model.ApiDomainDialogState`.
- [x] **T5** in `HomeVMTest` add the same two shapes as
  `openApiDomainDialog_exposesCurrentAndCustomDomain` and
  `openApiDomainDialog_leavesCustomDomainEmpty_whenNoneIsSet`: stub
  `every { apiDomainInteractor.getState() } returns ApiDomainState(...)`,
  `val vm = createVM()`, `vm.onAction(HomeAction.OpenApiDomainDialog)` (no
  `testOnStart()`; the state stays `Loading`), assert
  `assertEquals(expected, vm.testStateValue.apiDomainDialog)`. Imports to add:
  `org.junit.jupiter.api.Assertions.assertEquals`,
  `com.kino.puber.ui.feature.home.model.HomeAction`,
  `com.kino.puber.core.ui.uikit.model.ApiDomainDialogState`.
- [x] **T6** in `DeviceSettingsVMMenuSectionsTest`: give `createVM` a second
  parameter `apiDomainState: ApiDomainState = ApiDomainState(domain = "service-kp.com", customDomain = null)`
  and use it in the existing `getState()` stub (existing callers unchanged).
  Add `apiDomain_mapsCurrentAndCustomDomain` (`ApiDomainState("api.example", "custom.example")`)
  and `apiDomain_leavesCustomDomainEmpty_whenNoneIsSet` (`customDomain = null`).
  Each asserts `vm.testStateValue.apiDomain` after `createVM` (initial-state path),
  then `vm.onAction(DeviceSettingsActions.OpenApiDomainDialog)` and asserts
  `apiDomain` again plus `isApiDomainDialogOpen == true` (open path). Add the
  `ApiDomainDialogState` import.
- [x] If any of T4-T6 cannot reach the dialog state without heavy setup, drop
  that one, keep T7 (Task 5) plus device row D2, and record it under
  "Deviations" (spec Test design).
- [x] COMPILE, then TEST `com.kino.puber.ui.feature.player.model.PlayerUIMapperTest`
  `com.kino.puber.ui.feature.auth.vm.AuthVMTest` `com.kino.puber.ui.feature.home.vm.HomeVMTest`
  `com.kino.puber.ui.feature.device.settings.vm.DeviceSettingsVMMenuSectionsTest`. All green
  on unchanged production code. `git diff --stat` shows only the four test files.
- [x] Commit: `Add characterisation tests for the playback clock and the API-domain dialog state`.

**Done when:** the TEST command above passes and `git show --stat HEAD` lists
only those four files under `app/src/test`.

---

### Task 2: Detekt "before" count

**Covers:** AC-8 (before), AC-9 reference point; spec Risk 1, Test design "Detekt gate plan", Q5
**Files:**
- Modify: `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md`
  — "Detekt" section table row "Before" and the "Status" line (section starts at line 119; table at line 174).

- [x] Host precondition: `free -m`. If swap use is above about 50 %, do not
  start: report it to the conductor and wait. Do not run `--stop` and do not
  kill daemons that are not yours.
- [x] Run DETEKT in the worktree, log to a file outside `<wt>`. Production code
  is unchanged at this point (only Task 1 tests were added), so `devDebug` is
  the audited-commit count.
- [x] If the JVM crashes again (SIGSEGV / hang, no `app/build/reports/detekt/*.md`):
  1. Check the main checkout is clean and its `app/src/main` equals the audited commit:
     `git -C /mnt/data/projects/PetProjets/Puber status --short` (empty) and
     `git -C /mnt/data/projects/PetProjets/Puber diff --stat 36cda915 HEAD -- app/src/main` (empty).
  2. Run `cd /mnt/data/projects/PetProjets/Puber && ./gradlew :app:detektAll --continue`
     (allowed for the "before" count only, user answer Q5).
  3. Read the counts from the main checkout's `app/build/reports/detekt/`.
  Move any `hs_err_pid*.log` out of both checkouts; never commit one.
- [x] Record in the report table: commit actually analysed (worktree `HEAD`, or
  main `master` hash), where it ran (worktree / main checkout per Q5),
  `devDebug` total, `devDebugUnitTest` total, and counts per rule set. Note
  that a worktree `devDebugUnitTest` count includes the Task 1 tests. Update the
  "Status" line. Do not edit `config/detekt/detekt-baseline.xml`.
- [x] If neither run completes: write "not obtained, host fault" with the
  attempt, tell the conductor (AC-8/AC-9 stay open; the rest of the plan
  proceeds, spec Detekt gate plan step 4).
- [x] Commit: `Record the detekt count before the code-quality-audit fixes`.

**Done when:** the report's "Before" row holds two numbers and a per-rule-set
breakdown (or the explicit "not obtained, host fault" entry), and `git status`
shows no stray logs.

---

### Task 3: F1 — delete `IdGenerator` (RW-5, STR-4 part)

**Covers:** AC-7
**Files:**
- Delete: `app/src/main/java/com/kino/puber/core/system/IdGenerator.kt` [Source: app/src/main/java/com/kino/puber/core/system/IdGenerator.kt:8]
- Modify: `docs/architecture/navigation.md` — delete the two-line sentence
  "`IdGenerator.generateId()` (`core/system/IdGenerator.kt`) exists but is not the
  navigation-result convention." [Source: docs/architecture/navigation.md:61]; keep the
  preceding sentence ending in `result = changes)`.`

**Consumes:** nothing. **Produces:** nothing.

- [ ] Pre-check: `cd <wt> && grep -rn IdGenerator app/src baselineprofile docs/architecture`
  shows only the two places above (R5).
- [ ] Delete the file, edit the doc.
- [ ] COMPILE. Re-run the grep: no hits in `app/src`, `baselineprofile`, `docs/architecture`.
- [ ] Commit: `Delete the unused IdGenerator (RW-5, STR-4)`.

**Done when:** COMPILE passes and the grep is empty.

---

### Task 4: F2 — delete the TMDB cast leftovers (BP-2)

**Covers:** AC-7
**Files:**
- Modify: `app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt`
  - delete `IMDB_TITLE_ID_REGEX` and `String.normalizedImdbTitleIdOrNull()` [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:27] [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:29]
  - delete `findMediaByImdbId` [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:84], `getMovieCredits` [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:105], `getTvAggregateCredits` [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:113], `getConfiguration` [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:121]
  - delete the imports `TmdbAggregateCreditsResponse`, `TmdbConfigurationResponse`, `TmdbCreditsResponse`, `TmdbMediaKind`, `TmdbMediaRef` [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:4]; keep `TmdbFindResponse`, `TmdbSeasonDetailsResponse`, `TmdbTvDetailsResponse`
  - keep `isConfigured`, `findTvByImdbId`, `getTvDetails`, `getTvSeasonDetails`, `findByImdbId`, `apiResult`, `withTmdbPrefix`, `requireSuccess`, `forTesting`, `TmdbApiException` (used by `EpisodeScheduleRepository` [Source: app/src/main/java/com/kino/puber/data/repository/EpisodeScheduleRepository.kt:55] and `SkipSegmentService` [Source: app/src/main/java/com/kino/puber/data/repository/SkipSegmentService.kt:62])
- Delete: `app/src/main/java/com/kino/puber/data/api/models/TmdbCastModels.kt` (all of `TmdbMediaKind`, `TmdbMediaRef`, `TmdbCastMember`, `TmdbCreditsResponse`, `TmdbAggregateCreditsResponse`, `TmdbCastCredit`, `TmdbConfigurationResponse`, `TmdbImageConfiguration`) [Source: app/src/main/java/com/kino/puber/data/api/models/TmdbCastModels.kt:1]
- Delete: `app/src/main/java/com/kino/puber/data/api/config/TmdbImageConfig.kt` [Source: app/src/main/java/com/kino/puber/data/api/config/TmdbImageConfig.kt:3]
- Test (modify): delete `castAndConfigurationEndpoints_preservePathsLocalizationAndDecoding` [Source: app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt:135]; leave every other test in the file unchanged. Remove any import the deletion leaves unused.

**Consumes:** nothing. **Produces:** nothing.

- [ ] Pre-check: for each of `TmdbMediaRef TmdbMediaKind TmdbCastMember TmdbCastCredit TmdbCreditsResponse TmdbAggregateCreditsResponse TmdbConfigurationResponse TmdbImageConfiguration TmdbImageConfig normalizedImdbTitleIdOrNull findMediaByImdbId getMovieCredits getTvAggregateCredits`,
  `grep -rlw <name> app/src baselineprofile` lists only the files being changed (R4).
  (`getConfiguration` hits in `app/src/main/generated/baselineProfiles/*.txt` are
  `AndroidComposeView`/`Json`/Voyager methods, not TMDB; leave the profile alone.)
- [ ] Apply the deletions.
- [ ] COMPILE, then TEST `com.kino.puber.data.api.TmdbApiClientTest` — the six
  remaining tests pass.
- [ ] Re-run the grep: no hits.
- [ ] Commit: `Delete the unused TMDB cast and configuration endpoints (BP-2)`.

**Done when:** COMPILE and `TmdbApiClientTest` pass; the grep is empty.

---

### Task 5: F3 — one shared `ApiDomainState.toDialogState()` (SOLID-10), with T7

**Covers:** AC-7; test design T7, reruns T4-T6
**Files:**
- Modify: `app/src/main/java/com/kino/puber/core/ui/uikit/model/ApiDomainDialogState.kt` [Source: app/src/main/java/com/kino/puber/core/ui/uikit/model/ApiDomainDialogState.kt:6]
  — add `import com.kino.puber.domain.interactor.api.ApiDomainState` and, below the class:
  ```kotlin
  internal fun ApiDomainState.toDialogState(): ApiDomainDialogState =
      ApiDomainDialogState(
          currentDomain = domain,
          customDomain = customDomain,
      )
  ```
  (`isDetecting` left at its default `false`, exactly as the three copies.)
- Modify: `app/src/main/java/com/kino/puber/ui/feature/home/vm/HomeVM.kt` — delete the private copy [Source: app/src/main/java/com/kino/puber/ui/feature/home/vm/HomeVM.kt:673]; add `import com.kino.puber.core.ui.uikit.model.toDialogState`; delete the now-unused `ApiDomainState` import [Source: app/src/main/java/com/kino/puber/ui/feature/home/vm/HomeVM.kt:34]. Keep the `ApiDomainDialogState` import (still used at line 535).
- Modify: `app/src/main/java/com/kino/puber/ui/feature/auth/vm/AuthVM.kt` — delete the private copy [Source: app/src/main/java/com/kino/puber/ui/feature/auth/vm/AuthVM.kt:291]; add the `toDialogState` import; delete the `ApiDomainState` import [Source: app/src/main/java/com/kino/puber/ui/feature/auth/vm/AuthVM.kt:13]. Keep `ApiDomainDialogState` (used at line 228).
- Modify: `app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVM.kt` — delete the private copy [Source: app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVM.kt:505]; add the `toDialogState` import; delete both the `ApiDomainState` import [Source: app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVM.kt:18] and the `ApiDomainDialogState` import [Source: app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVM.kt:10] — both are used only by the deleted copy. Keep the blank line before the class's closing brace tidy.
- Test (new): `app/src/test/kotlin/com/kino/puber/core/ui/uikit/model/ApiDomainDialogStateTest.kt`

**Consumes:** `ApiDomainState` [Source: app/src/main/java/com/kino/puber/domain/interactor/api/ApiDomainInteractor.kt:18].
**Produces:** `internal fun ApiDomainState.toDialogState(): ApiDomainDialogState` in package `com.kino.puber.core.ui.uikit.model`.

- [ ] **T7** `ApiDomainDialogStateTest` (plain JUnit 5, no dispatcher needed):
  - `toDialogState_mapsDomainAndCustomDomain`: `ApiDomainState("api.example", "custom.example").toDialogState()` ==
    `ApiDomainDialogState("api.example", "custom.example", isDetecting = false)` and `initialInput == "custom.example"`.
  - `toDialogState_leavesInitialInputEmpty_whenNoCustomDomain`: `customDomain = null` → `customDomain == null`, `initialInput == ""`.
  - `toDialogState_isNeverDetecting`: `isDetecting == false` for both shapes.
- [ ] Apply the production changes. Call sites stay textually the same
  (`getState().toDialogState()`, `result.state.toDialogState()`, `state.toDialogState()`).
- [ ] `cd <wt> && grep -rn "fun ApiDomainState.toDialogState" app/src/main` → exactly one hit, in `ApiDomainDialogState.kt`.
- [ ] COMPILE, then TEST `com.kino.puber.core.ui.uikit.model.ApiDomainDialogStateTest`
  `com.kino.puber.ui.feature.auth.vm.AuthVMTest` `com.kino.puber.ui.feature.home.vm.HomeVMTest`
  `com.kino.puber.ui.feature.device.settings.vm.*` — green, with T4-T6 unedited
  (`git diff HEAD~1 -- <those three test files>` empty after the commit, i.e. this commit does not touch them).
- [ ] Commit: `Share one ApiDomainState to dialog-state mapper across the three view models (SOLID-10)`.

**Done when:** the TEST command passes, the grep returns one line, and the
commit touches only the four production files and the new test.

---

### Task 6: F4 — one shared playback clock formatter (RW-7), with T2

**Covers:** AC-7; test design T2, reruns T1
**Files:**
- Create: `app/src/main/java/com/kino/puber/ui/feature/player/model/PlaybackClock.kt`:
  ```kotlin
  package com.kino.puber.ui.feature.player.model

  import java.util.Locale

  private const val MILLIS_PER_SECOND = 1_000L
  private const val SECONDS_PER_HOUR = 3_600L
  private const val SECONDS_PER_MINUTE = 60L

  /** `m:ss`, or `h:mm:ss` from one hour on; ASCII digits whatever the default locale. */
  internal fun formatPlaybackClock(positionMs: Long): String {
      val totalSeconds = positionMs / MILLIS_PER_SECOND
      val hours = totalSeconds / SECONDS_PER_HOUR
      val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
      val seconds = totalSeconds % SECONDS_PER_MINUTE
      return if (hours > 0) {
          String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
      } else {
          String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
      }
  }
  ```
  This is the body of `PlayerUIMapper.formatTime` moved verbatim
  [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:215].
- Modify: `app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt`
  - `fun formatTime(positionMs: Long): String = formatPlaybackClock(positionMs)` — signature unchanged, because VM tests mock it [Source: app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMTestFixture.kt:122] and `PlayerVM` calls it [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:377] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:640].
  - delete the three file-private constants [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:16] and the `java.util.Locale` import [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:14] (no other use in the file).
- Modify: `app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerProgressBar.kt`
  - delete `private fun formatTimeMs` [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerProgressBar.kt:325]
  - `currentTimeText = remember(currentPosition) { formatPlaybackClock(currentPosition) }` [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerProgressBar.kt:59]; line 60 (`formatRemainingTime`) unchanged
  - inside `formatRemainingTime` call `formatPlaybackClock(...)` instead of `formatTimeMs(...)` [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerProgressBar.kt:337]; keep `EMPTY_REMAINING_TIME`
  - delete `MILLIS_PER_SECOND`, `SECONDS_PER_MINUTE`, `SECONDS_PER_HOUR` [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerProgressBar.kt:355] and the `java.util.Locale` import [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerProgressBar.kt:43] (no other use in the file)
  - add `import com.kino.puber.ui.feature.player.model.formatPlaybackClock` (the file has no `player.model` import yet).
- Test (new): `app/src/test/kotlin/com/kino/puber/ui/feature/player/model/PlaybackClockTest.kt`

**Consumes:** nothing new. **Produces:** `internal fun formatPlaybackClock(positionMs: Long): String` in `com.kino.puber.ui.feature.player.model`.

- [ ] **T2** `PlaybackClockTest`: `formatPlaybackClock_matchesPlaybackClockTable` with
  the same ten pairs as T1, and `formatPlaybackClock_keepsAsciiDigitsUnderArabicDefaultLocale`
  with the same locale set/restore as T1. The tables must be identical to T1.
- [ ] Apply the production changes.
- [ ] Greps (detekt may not run, spec Detekt gate plan step 5):
  `grep -rn "formatTimeMs" app/src` → empty;
  `grep -n "Locale\|MILLIS_PER_SECOND\|SECONDS_PER" app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerProgressBar.kt app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt` → empty.
- [ ] COMPILE, then TEST `com.kino.puber.ui.feature.player.model.*`
  `com.kino.puber.ui.feature.player.vm.*` — green, with `PlayerUIMapperTest` unedited in this commit.
- [ ] Commit: `Share one playback clock formatter between the progress bar and the player mapper (RW-7)`.

**Done when:** the TEST command passes, the greps are empty, and the commit
touches only the three production files and the new test.

---

### Task 7: Detekt "after" and the static gate

**Covers:** AC-7 (scope script), AC-8 (after), AC-9
**Files:**
- Modify: `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md` — "After F1-F4" row and the "Status" line of the Detekt section.

- [ ] Host precondition as in Task 2 (`free -m`; no `--stop`).
- [ ] COMPILE, FULLTEST, DETEKT in the worktree (the "after" count has no
  main-checkout fallback; spec Detekt gate plan step 3).
- [ ] Record `HEAD` hash, `devDebug` and `devDebugUnitTest` totals and per
  rule set in the "After F1-F4" row. AC-9 holds when the `devDebug` total is
  no higher than the "Before" one; compare `devDebugUnitTest` too and explain
  any difference by the added/removed tests.
- [ ] If DETEKT cannot complete: write "not run, host fault" in the row, report
  AC-9 as unverified (not passed) to the conductor, and continue; do not touch
  `detekt-baseline.xml`.
- [ ] AC-7 scope checks (record results in the conductor reply, not in the repo):
  - `git diff --stat master..HEAD -- app/src/main` lists only:
    `core/system/IdGenerator.kt`, `data/api/TmdbApiClient.kt`,
    `data/api/models/TmdbCastModels.kt`, `data/api/config/TmdbImageConfig.kt`,
    `core/ui/uikit/model/ApiDomainDialogState.kt`, `ui/feature/home/vm/HomeVM.kt`,
    `ui/feature/auth/vm/AuthVM.kt`, `ui/feature/device/settings/vm/DeviceSettingsVM.kt`,
    `ui/feature/player/model/PlaybackClock.kt`, `ui/feature/player/model/PlayerUIMapper.kt`,
    `ui/feature/player/component/PlayerProgressBar.kt`.
  - `git diff master..HEAD -- gradle/libs.versions.toml '*.gradle.kts' config/detekt/` is empty.
  - `git log --oneline master..HEAD` shows one commit per fix, each naming its finding ID.
- [ ] Commit: `Record the detekt count after the code-quality-audit fixes`.

**Done when:** COMPILE and FULLTEST pass, the "After" row is filled (or marked
"not run, host fault"), and the three scope checks hold.

---

### Task 8: Mark the findings fixed in the report

**Covers:** AC-7 ("the report marks fixed in this task"), AC-2/AC-6 consistency
**Files:**
- Modify: `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md`:
  - finding rows: RW-5 (line 61), RW-7 (line 68), SOLID-10 (line 89), BP-2
    (line 110): replace "**fix in this task**" with "**fixed in this task** (`<short hash>`)".
  - STR-4 (line 43): "RW-5 part: fixed in this task (`<F1 hash>`); rest: follow-up `dead-code-sweep`".
  - Summary table rows 1, 2, 17, 21, 34 (lines 184, 185, 200, 204, 217): same wording.
  - "Fixed in this task" table (line 224): add a `Commit` column with the F1-F4 hashes.
  - Leave the audited-commit `[Source: …]` citations as they are: they refer
    to `36cda915`, where those lines exist (AC-2).

- [ ] Get hashes: `git log --oneline master..HEAD`.
- [ ] Edit the report.
- [ ] `grep -n "fix in this task" <report>` → no remaining unmarked entry for F1-F4.
- [ ] Commit: `Mark the code-quality-audit findings fixed on this branch`.

**Done when:** every F1-F4 finding and summary row in the report reads "fixed
in this task" with a commit hash that `git cat-file -e <hash>` accepts.

---

### Out of this plan (qa, `task-verify`)

Device checks D1-D4 (spec Test design, "Device checks"; AC-10) run on a leased
emulator with the worktree's own dev APK installed through
`tools/android-apk-install-preserve`, and are recorded in
`docs/superpowers/evidence/2026-10-12-code-quality-audit-device.md`. The
implementer does not install or touch a device.

---

## Implementation record

<!-- implementer -->

**Commits:**

**Deviations from the plan:**

**Files touched outside the plan:**

**For the reviewers:**

**Blocked:**

## Readiness

<!-- final-judge, readiness mode: READY | NOT READY and why. -->

**Verdict: READY** (final-judge, 2026-10-12, plan at `1a512109`).

**AC coverage.**

| AC | Plan task / check | Test design row |
|---|---|---|
| AC-1..AC-6, AC-11 | Already delivered by the report from the design phase (sections at report lines 31, 45, 73, 92, 105, 177, 233 confirmed). Task 8 keeps AC-2/AC-6 consistent. qa runs the script and review checks. | AC-1..AC-6, AC-11 rows |
| AC-7 | Tasks 1, 3-6 (one commit per fix, with its ID), Task 7 scope checks, Task 8 (report marked "fixed") | AC-7 script + unit (T1, T2, T4-T7) |
| AC-8 | Task 2 (before, with the Q5 fallback), Task 7 (after) | AC-8 tooling |
| AC-9 | Task 7 (COMPILE, FULLTEST, DETEKT) | AC-9 tooling + unit |
| AC-10 | "Out of this plan": qa runs D1-D4 on a leased emulator using the worktree's own APK | AC-10 device |

**Sources sampled (all resolve).** `IdGenerator.kt:8` (`object IdGenerator`).
`navigation.md:61` (the sentence). `TmdbApiClient.kt:4,27,29,84,105,113,121`
(imports, regex, helper, the four endpoints). `TmdbApiClientTest.kt:135` (the
cast test; six others remain). `EpisodeScheduleRepository.kt:55`
(`findTvByImdbId`). `SkipSegmentService.kt:62` (`findByImdbId`).
`ApiDomainDialogState.kt:6`. `ApiDomainInteractor.kt:18` (`ApiDomainState`).
`HomeVM.kt:34,527,535,673`, `AuthVM.kt:13,40,219,228,291`,
`DeviceSettingsVM.kt:10,18,64,444,505`. `PlayerUIMapper.kt:14,16,215`,
`PlayerProgressBar.kt:43,59,325,337,355`. `PlayerVMTestFixture.kt:122`,
`PlayerVM.kt:377,640`. `DetailsPrefetchSurfaceState.kt:9`. `app/build.gradle.kts:338`
(`detektAll`). `DeviceSettingsVMMenuSectionsTest.kt:107`. Report line anchors
used by Tasks 2 and 8 (119, 174, 43, 61, 68, 89, 110, 184, 185, 200, 204, 217,
224) match.

**Behaviour preservation (verified on the code).**
- F1/F2: every symbol grepped in `app/src`, `baselineprofile` and `docs/architecture`
  has no reference outside the files being changed. The cast test is the only test
  user. `IOException` stays used by `TmdbApiException`. The kept endpoints are the
  ones the repositories call.
- F3: the three private copies are byte-identical. The planned import removals are exact:
  Home and Auth still use `ApiDomainDialogState`, while Settings uses neither import
  once its copy is gone. Every call site resolves to the top-level import once the member
  extension is deleted. `core/ui` already imports `domain`.
- F4: the bodies are identical. The `Int` constants in the progress bar widen to
  `Long`, so the output is the same. `formatTime` keeps its signature (mocked in
  VM tests). The same constant names in `player/vm/PlaybackDebugFormat.kt` are
  private, in another package, and left alone. They are not a collision.
- T4-T6 can be reached without heavy setup: each `OpenApiDomainDialog` action is
  routed in `onAction` (Auth:150, Home:192, Settings:163), and `testStateValue`
  exists. Unit tests do not run in parallel (no `maxParallelForks` or JUnit parallel
  config), so `Locale.setDefault` in T1/T2 is safe. The precedent is
  `EpisodeScheduleUIMapperTest`. The new tests trip no detekt rule:
  `MagicNumber`, `LongMethod` and `TooManyFunctions` exclude `**/test/**`.

**Build order and compliance.** Every task ends compiled and green. The order is
tests, then the before count, then F1, F2, F3, F4, then the after count, then the
report. All worktree builds run as `AGENT_GRADLE_SERIAL=1 ./tools/agentw`. The plan
forbids `--stop` and killing foreign daemons, which **overrides** spec Detekt gate
plan step 1 ("`./tools/agentw --stop` if present"): the plan wins. The one
`./gradlew` is the main-checkout fallback that Q5 authorises for the before count only.
`libs.versions.toml`, `*.gradle.kts` and `detekt-baseline.xml` stay untouched. No
task goes beyond F1-F4 or into Out of scope. The files owned by in-flight branches
are excluded.

**Open questions.** Q1-Q5 are answered.

**Notes for the implementer and qa (not blocking):**
1. Task 7 scope check: use the three-dot form `git diff --stat master...HEAD -- app/src/main`
   (and three-dot for the build-file diff). `upstream-sync-triage` is in impl. If it
   merges first, the two-dot form also shows master's own changes, reversed, and looks
   like a false scope breach. `git log master..HEAD` is correct as written.
2. Task 2 fallback: if `git diff --stat 36cda915 HEAD -- app/src/main` in the main
   checkout is **not** empty (some branch merged), the main-checkout count is not the
   audited-commit count. Record "not obtained" and tell the conductor; do not use it.
3. Known host instability (Q5, report Detekt section): five JVM crashes, with swap at
   about 90 %. The "after" count has no fallback. If it cannot run, AC-9 is
   **unverified**, not passed, and the gate will mark it `NOT VERIFIED`. The
   report records that an earlier attempt used `--stop` once on the shared
   `~/.gradle-agents`. This must not happen again.
4. T5 asserts through `OpenApiDomainDialog` from `Loading`, not through the
   error-state path the spec names. Both go through the same `openApiDomainDialog()`.
   This is acceptable, but record it under "Deviations".
