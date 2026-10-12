# Code quality audit — review

**Spec:** `docs/superpowers/specs/2026-10-12-code-quality-audit-design.md`
**Plan:** `docs/superpowers/plans/2026-10-12-code-quality-audit.md`
**Evidence:** `docs/superpowers/evidence/2026-10-12-code-quality-audit-device.md`
**Report:** `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md`
**Branch:** `code-quality-audit` at `05248239` (gate)

<!-- The conductor writes the rounds from the reviewers' returns; the
final-judge writes the Gate. Each finding gets a disposition: fixed (<sha>),
rejected (<why>), or deferred (<board row>). Nothing stays open silently. -->

## Round 1

No defects in the production changes F1–F4 from any reviewer. All findings
concern the report, the evidence, or test coverage.

### code-reviewer

Verdict: changes needed (report and evidence corrections only).

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|
| C1 | minor | report.md:116 (BP-8) | Source cites `Errors.kt:59` (`ListItemError`), not the shared component; `FullScreenError` is `Errors.kt:25`, `FullScreenProgressIndicator` is `core/ui/uikit/component/Loading.kt:11`. | fixed (39a735b4) |
| C2 | minor | report.md:62 (RW-6) | "Keep" reasoning for `SessionEventBus` ignores that `tryEmit` without replay drops a 401 when no subscriber is composed (before root composition / between teardown and recompose). | fixed (39a735b4) |
| C3 | minor | report.md, Reinvented wheels | Missed: `AppUpdateDownload.kt:142,148` hand-writes hex encoding and the sha256 check; stdlib `toHexString()`/`HexFormat` (Kotlin 2.4.10) or Okio `ByteString.sha256().hex()` cover it. | fixed (39a735b4) |
| C4 | minor | report.md, Structure/SOLID | Missed: `TmdbApiClient.apiResult` (`TmdbApiClient.kt:70`) duplicates `runCatchingCancellable` (`CoroutinesErrorHandler.kt:37`) and catches only `Exception`; `findTvByImdbId`/`findByImdbId` hit the same endpoint with different failure handling. | fixed (39a735b4) |
| C5 | minor | report.md:68, spec:96 | "byte-identical" formatters were `Int` vs `Long` constants; equivalent only by widening. Say "semantically identical". | fixed (39a735b4) |
| C6 | minor | report.md, SOLID | `DetailsVM` (698 lines, `@Suppress("LargeClass")`) has no finding or "acceptable" verdict. | fixed (39a735b4) |
| C7 | minor | device.md:34,37 | AC-10 only partly verified (D4 NOT CHECKED, D2 only no-custom-domain). Evidence contradicts itself: progress reportedly written, yet no saved progress and no resume dialog after a ~22-min seek; never compared with master. | D4 carried to the gate as NOT CHECKED; the save/resume anomaly deferred to new board row `resume-progress-anomaly` (F1–F4 do not touch the save path); evidence contradiction reconciled by qa (see commit below) |

### test-reviewer

Verdict: adequate.

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|
| T1 | major (spec-accepted gap) | `PlayerProgressBar.kt:325` | `formatRemainingTime` (minus prefix, `-0:00`, `coerceAtLeast(0)` clamp) is pinned by no unit test; zero-duration case not seen on device. | fixed (cae60189: `internal`, body unchanged; `FormatRemainingTimeTest`) |
| T2 | minor | `PlayerVM.kt:377,640` | Resume dialog / seek indicator wiring to the formatter is only indirectly covered; every PlayerVM test mocks `mapper.formatTime`. | fixed (f3c0437e, ae4fd9fa: `PlayerStartModeTest.resumeDialogFormatsSavedPositionWithRealMapper`, "1:01:01") |
| T3 | minor | `PlayerUIMapperTest.kt:62`, `PlaybackClockTest.kt` | `Locale.setDefault` is JVM-global; parallel tests could flake. | rejected: JUnit parallel execution is not enabled (`app/build.gradle.kts` sets `parallel = true` only for detekt; no `junit.jupiter.execution.parallel` config), and both tests restore the locale in `finally` |
| T4 | minor | `AuthVMTest.kt` | Startup path `AuthVM.kt:85` untested; HomeVM uses the open-dialog action, not the error path. | rejected: every path calls the one shared `toDialogState()`, which T7 tests directly with full-equality assertions; the deviation is recorded in the plan |
| T5 | minor | `DeviceSettingsVMMenuSectionsTest.kt` | Post-open `apiDomain` assertion repeats the initial one. | rejected: harmless redundancy, `isApiDomainDialogOpen` carries the new information; no correctness impact |
| T6 | minor | commit `72a2da6d` | T2/T7 not in the characterisation commit. | rejected: matches the plan (T2/T7 are written with F4/F3) |

### codex-reviewer

Ran: review, adversarial

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|
| X1 | minor | device.md:34 | D4 resume-dialog check NOT CHECKED; AC-10 partial. | same as C7: carried to the gate as NOT CHECKED; T2 adds unit coverage of the resume-dialog wiring |

Rejected Codex claims:

- None. Codex's plain review found no defects; its check of the report verdicts (keep Paginator, TypedTtlCache, ContentUriCodec, SessionEventBus; no new dependency) raised no conflict.

### Fixes outside the review rows

- Round 1 fixes touched runtime code only by `private` → `internal` on `formatRemainingTime` (cae60189); no runtime behaviour change, so qa does not re-check device ACs. Implementer static gate after the fixes: `testDevDebugUnitTest`, `detektAll`, `compileDevDebugKotlin` green.
- Codex is not re-run in round 2: its only finding (X1) is the device-coverage gap, which no code fix changes.

### ux-designer (visual changes)

Not run: the diff changes no Compose layout (F4 replaces a private formatter call in `PlayerProgressBar.kt`).

## Round 2

Re-run: code-reviewer, test-reviewer (codex not re-run: its only finding X1 is the device gap). qa reconciled the evidence contradiction from C7 in 45cb3df1.

### code-reviewer

Verdict: changes needed (report text only). C1, C3, C5 confirmed; fix commits cae60189, f3c0437e, ae4fd9fa clean.

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|
| C8 | minor | report.md:98 (SOLID-12) | Only `DetailsVM` serialises mutations with a `Mutex`; `PlayerVM.kt:1785` and `CollectionDetailVM.kt:154` do not (PlayerVM also has an `onCreated` hook). A shared serialising queue would slow player toggles and collection mutations. Mark serialisation optional per caller. | fixed (2f246f31) |
| C9 | minor | report.md:99 (SOLID-13) | `SkipSegmentService.kt:62` only calls `.getOrNull()`, so nothing logs the failure; the row must say the log has to be added. Call-site count is 12, not 15. | fixed (2f246f31) |
| C10 | minor | report.md:68 (RW-6) | The redirect to auth is `LauncherVM.kt:35-50`, not `AuthVM.kt:56`; the domain switch also clears the prefetcher (`ApiDomainInteractor.kt:161-169`). The conclusion holds. | fixed (2f246f31) |
| C11 | minor | report.md:68, :260 (RW-6, `session-expiry-delivery`) | The proposed `Channel(CONFLATED)` + `receiveAsFlow()` keeps a stale event until the next composition: after relaunch with no token, `LauncherVM` routes to auth and the handler navigates to auth a second time (possible second device-code request). Say how to suppress it, or prefer the application-scope collector. | fixed (2f246f31) |

### test-reviewer

Verdict: adequate. No findings; T1 and T2 closed, T3–T6 rejections agreed. Accepted minor gap: the seek indicator (`PlayerVM.kt:640`) is covered only through a mocked mapper; the mapper path itself is pinned by T2.

## Gate

<!-- final-judge, gate mode. -->

**Decision:** CONCERNS

Judged at branch HEAD `05248239` (final-judge, 2026-10-12). Sources: `git diff
master...HEAD`, the build outputs in the worktree, a scripted check of the
report's citations, and the evidence files. No Gradle run of my own: host swap
was 97 % used (7944/8191 MB), and the spec's detekt gate plan forbids starting
above ~50 %. The existing outputs were checked for freshness instead (below).

**Done as asked.** The production diff is exactly F1-F4. `IdGenerator.kt`,
`TmdbImageConfig.kt` and `TmdbCastModels.kt` are deleted. The four TMDB methods
and the regex helper are removed. One `toDialogState()` sits next to
`ApiDomainDialogState`. One `formatPlaybackClock` serves both the mapper and the
progress bar. The `navigation.md` sentence is dropped. No reference to a
deleted symbol remains in `app/`, `baselineprofile/` or `docs/architecture/`
(grep). The changed files have no unused imports. `gradle/`, `*.gradle.kts`
and `config/detekt/` are unchanged. The one production change outside F1-F4 is
cae60189 (`formatRemainingTime` `private` → `internal`, body unchanged). It
came from review T1 and is recorded in the plan (line 501) and in this file,
but not in the report's "Fixed in this task" table (concern 3).

**Static gate freshness.** No qa re-run followed the round-1 fixes. The worktree
outputs postdate the last source edit (`PlayerStartModeTest.kt` 09:04:28), and
`app/` has no change after `ae4fd9fa`:
- test results 09:05:06: 1432 tests, 0 failures, errors or skips. That is qa's
  1427 plus the 5 added in round 1, and includes
  `resumeDialogFormatsSavedPositionWithRealMapper` and `FormatRemainingTimeTest`.
- detekt `devDebug` 09:03:33 and `devDebugUnitTest` 09:05:16: 0 findings each.
- Compilation is implied by the test run.

This rests on the files on disk, not on a fresh run.

### Traceability

| AC | Test / check | Evidence | Status |
|---|---|---|---|
| AC-1 | Report headings | `## Structure`, `## Reinvented wheels`, `## SOLID`, `## Performance`, `## Best practices` present (report lines 37-114) | PASS |
| AC-2 | Script: every `[Source: path:line]` resolved against audited commit `36cda915` (file exists, line ≤ length); 8 lines spot-read for the symbol | 131 citations, 0 unresolved (2 hits are the literal `path:line` template text). Spot reads match: `SessionEventBus.kt:12`, `TmdbApiClient.kt:127` `apiResult`, `CoroutinesErrorHandler.kt:37` `runCatchingCancellable`, `Paginator.kt:542`, `PlayerVM.kt:1832` `getExoPlayer`, `AppUpdateDownload.kt:142` `isSha256`, `LauncherVM.kt:35`, `PuberApp.kt:94` `newImageLoader`. All 42 finding rows carry sev / effort / approach | PASS |
| AC-3 | Read "Named candidates" | RW-1..RW-6: Paginator keep, TypedTtlCache keep, CachedFeed/ContentCacheRepository defer, ContentUriCodec keep, IdGenerator replace → delete, SessionEventBus keep plus a delivery fix, each with a reason; no library named as a replacement outside the catalogue | PASS |
| AC-4 | Read SOLID | SOLID-1, -3, -4, -5, -6, -7, -8, -9 cover the 8 files (split or "acceptable" with a reason); DetailsVM added as SOLID-12 (C6) | PASS |
| AC-5 | Read Performance | Header "static reading, unmeasured"; each row has a "Related" column (P2, M2) and none repeats P3/M4/P5/P8-P12 | PASS |
| AC-6 | Read Summary + follow-up rows | Ordered libraries → SOLID/structure → performance → best practices, then sev, then effort; 20 rows with slug, scope, findings, kind, size, dimension | PASS |
| AC-7 | `git diff master...HEAD -- app/src/main`, `gradle/`, `*.gradle.kts`; `git log` | Only F1-F4 files plus cae60189's visibility change; build files empty diff; commits ee4da3ce (RW-5, STR-4), 2342ea57 (BP-2), 93a06ca2 (SOLID-10), c07d807d (RW-7) one per fix; characterisation tests T1, T4-T6 committed before the fixes (72a2da6d), T2 and T7 green | PASS (see concern 3) |
| AC-8 | Report Detekt section | Before 0 / 0 (worktree, `00769aee`, before F1), after 0 / 0 (`1003aa31`); "per rule family" is trivially all zero | PASS |
| AC-9 | Compile, `testDevDebugUnitTest`, `detektAll` via `agentw` | qa run at `abe63f00` green (1427 tests). After round 1: implementer's run, confirmed by worktree outputs newer than every source edit (1432/0, detekt 0/0). No fresh qa run | PASS (artifact-backed, see concern 4) |
| AC-10 | Device D1-D4, `emulator-5554`, worktree APK sha256-matched | device.md run 3. D1 PASS. D2 PASS for the no-custom-domain shape only. D3 PASS, but the zero-duration `-0:00` case was not reachable on the device (it is unit-pinned by `FormatRemainingTimeTest`). D4 resume dialog **NOT CHECKED**: no saved progress, dialog never rendered | **PARTIAL: D4 NOT VERIFIED** |
| AC-11 | Read Reinvented wheels | The report recommends no new dependency and says so explicitly; no finding carries the flag | PASS (n/a) |

### Findings disposition

All review findings have a disposition. None was silently dropped.
- Fixed: C1-C6, C8-C11 (39a735b4, 2f246f31), T1 (cae60189), T2 (f3c0437e, ae4fd9fa).
- Rejected with a reason: T3-T6.
- C7 / X1: D4 carried here as NOT CHECKED. The save/resume anomaly is deferred
  to board row `resume-progress-anomaly` (BOARD.md:19, stage `idea`).

The one major (T1) is fixed. There were no blockers.

### Non-functional

| Area | Assessment |
|---|---|
| Performance | Neutral. `formatPlaybackClock` does the same arithmetic and one `String.format` as each removed copy, inside the same `remember(currentPosition)` / `remember(currentPosition, duration)` keys. No new composition reads and no allocation beyond what the old code made. Deleted classes were never loaded. `PlaybackClockKt` is not in the baseline profile until the next regeneration: one small class on first player open, negligible. No perf claim is made (standard route). |
| Reliability | No error, empty or cancellation path changes. Deleted TMDB methods had no callers; the kept `findTvByImdbId`, `findByImdbId` and schedule endpoints and their tests are untouched. Zero-duration and clamped remaining time are pinned by unit tests. No persistence or process-death surface touched. |
| Maintainability | Two duplicated helpers removed, no new duplicate. Files left with no use are deleted, and no unused constants or imports remain (checked). AGENTS.md conventions hold: JUnit 5 tests, `internal` visibility, no Koin change. The new file is in `player/model`, next to its caller. |
| Security / privacy | No secrets in the diff. `tools/mobile-evidence-audit.sh` passed on the report and the device evidence. No raw logs or screenshots committed. `service-kp.com` is the public default API domain, not account data. The install was a conductor-run `install -r` under the user's own words, with no uninstall/clear/downgrade and `auth-save` first. Playback on the test account may have sent progress; this is stated in the evidence. |
| TV (focus, D-pad, Back) | No focus requester, modifier, key handler or layout change. On the device (D1, D2) D-pad moved focus on Home, and BACK closed the API-domain dialog with focus returned to its row. No overscan-relevant change. |

### Must fix

None.

### Concerns for the user

1. **AC-10 is only partly verified on the device.** D4 (resume dialog time
   after F4) was never rendered on `emulator-5554`: the account showed no saved
   progress after a ~22-min seek. The string comes from `PlayerUIMapper.formatTime`.
   That function is verified on the device through D3 and pinned with a real
   mapper by `resumeDialogFormatsSavedPositionWithRealMapper`, so the regression
   risk from this branch is low. Still, it is unit coverage, not device evidence.
   The anomaly itself was not compared with master. It is tracked as
   `resume-progress-anomaly` and may be a pre-existing bug.
2. **Partial device shapes.** D2 covered only "no custom domain". The Auth and
   Home-error dialog paths, the "with custom domain" shape and the zero-duration
   remaining time are covered by unit tests only. "As on master" rests on the
   row and dialog agreeing, because no master build was installed for
   side-by-side comparison.
3. **One production change outside F1-F4.** cae60189 widens `formatRemainingTime`
   from `private` to `internal` for a test. Behaviour is unchanged, but the commit
   cites no finding ID, and the report's "Fixed in this task" table does not list
   it, which a strict reading of AC-7 asks for. It is recorded in the plan and in
   this review.
4. **Final static gate is artifact-backed.** After the round-1 fixes no qa run
   followed. I accepted the implementer's run on the strength of the outputs on
   disk (timestamps after every source edit, 1432/0 tests, detekt 0/0). I did not
   re-run it: host swap was at 97 %, and the same condition crashed detekt five
   times earlier. Detekt also reports "83 / 7 compiler errors during analysis"
   alongside 0 findings, which it says lowers accuracy.
5. Housekeeping: this file's header still says `Branch ... at 115345a9`, and the
   `code-quality-audit` board row still reads "round 2 ... in progress". The
   conductor should update both at merge.
