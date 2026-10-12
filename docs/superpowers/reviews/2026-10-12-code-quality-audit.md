# Code quality audit — review

**Spec:** `docs/superpowers/specs/2026-10-12-code-quality-audit-design.md`
**Plan:** `docs/superpowers/plans/2026-10-12-code-quality-audit.md`
**Evidence:** `docs/superpowers/evidence/2026-10-12-code-quality-audit-device.md`
**Report:** `docs/superpowers/evidence/2026-10-12-code-quality-audit-report.md`
**Branch:** `code-quality-audit` at `115345a9`

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
| C8 | minor | report.md:98 (SOLID-12) | Only `DetailsVM` serialises mutations with a `Mutex`; `PlayerVM.kt:1785` and `CollectionDetailVM.kt:154` do not (PlayerVM also has an `onCreated` hook). A shared serialising queue would slow player toggles and collection mutations. Mark serialisation optional per caller. | to fix (architect) |
| C9 | minor | report.md:99 (SOLID-13) | `SkipSegmentService.kt:62` only calls `.getOrNull()`, so nothing logs the failure; the row must say the log has to be added. Call-site count is 12, not 15. | to fix (architect) |
| C10 | minor | report.md:68 (RW-6) | The redirect to auth is `LauncherVM.kt:35-50`, not `AuthVM.kt:56`; the domain switch also clears the prefetcher (`ApiDomainInteractor.kt:161-169`). The conclusion holds. | to fix (architect) |
| C11 | minor | report.md:68, :260 (RW-6, `session-expiry-delivery`) | The proposed `Channel(CONFLATED)` + `receiveAsFlow()` keeps a stale event until the next composition: after relaunch with no token, `LauncherVM` routes to auth and the handler navigates to auth a second time (possible second device-code request). Say how to suppress it, or prefer the application-scope collector. | to fix (architect) |

### test-reviewer

Verdict: adequate. No findings; T1 and T2 closed, T3–T6 rejections agreed. Accepted minor gap: the seek indicator (`PlayerVM.kt:640`) is covered only through a mocked mapper; the mapper path itself is pinned by T2.

## Gate

<!-- final-judge, gate mode. -->

**Decision:** PASS | CONCERNS | FAIL | WAIVED (user only)

### Traceability

| AC | Test / check | Evidence | Status |
|---|---|---|---|

### Non-functional

| Area | Assessment |
|---|---|
| Performance | |
| Reliability | |
| Maintainability | |
| Security / privacy | |
| TV (focus, D-pad, Back) | |

### Must fix

### Concerns for the user
