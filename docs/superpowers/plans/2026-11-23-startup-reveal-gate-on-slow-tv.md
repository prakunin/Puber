# Bounded image hold after ready in the startup reveal gate - plan

**Spec:** `docs/superpowers/specs/2026-11-23-startup-reveal-gate-on-slow-tv.md`
**Goal:** Hold the splash after `ready` only for the image requests in flight at `ready`, and at most `REVEAL_GRACE_MS` (300 ms), revealing with the new cause `grace` when that time ends the hold; cap, forced reveal and S0 starts unchanged.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Both run in `/home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv` (`cd <wt> && ...`); never `./gradlew` there.
- Touched tests: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<fqcn>'` (one `--tests` per class).
- One task = green fast gate + green tests of the touched code + one commit; message is one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Paths: main sources under `app/src/main/java/com/kino/puber/`, unit tests under `app/src/test/kotlin/com/kino/puber/`; both prefixes are dropped below. Line numbers are at base `9158cf82`.
- Test names, priorities and red/guard status: spec section Test design (Traceability). A test named there is written with exactly that name. A P0 `red` test is run before its production code and fails (not compiling on base counts as red); the Implementation record lists each red result.
- Device checks (spec Test design: Manual checks, Perf track; AC-6 to AC-10) belong to `qa-device` after Task 3; the implementer does no device work. If verification fixes G at a value other than 300 ms, only `REVEAL_GRACE_MS` changes.
- No baseline profile regeneration (owner, design Q1); `app/src/main/generated/baselineProfiles/` stays untouched.
- Out of scope (spec, Out of scope): cap value (`REVEAL_CAP_MS`, `core/startup/RevealGate.kt:9`), `MainActivity.kt`, `StartupSplashHost.kt`, `StartupSettled` and the deferred-work slots, `StartupMarks`/`TelemetryEncoder` production code.

---

## Tasks

### Task 1: Add the reveal cause `grace`

**Covers:** AC-5
**Files:**
- Modify: `core/startup/RevealObserver.kt:4-8` - add `GRACE("grace")` after `FORCED`; KDoc line 3 unchanged in meaning (`wire` is the `cause` value).
- Test: `core/telemetry/TelemetryEncoderTest.kt` - add `startupWritesGraceCause`: the `Startup` of `startupWritesRevealCauseAndImageFields` (`:136-162`) with `cause = RevealCause.GRACE`; assert `cause` = `"grace"`, and `ready`, `ready_drained`, `in_flight` as there (900, 1_150, 3).
- Test: `core/telemetry/StartupMarksTest.kt` - add `graceRevealLineWaitsForHeldDrain` after `lineWaitsForHeldDrainWhenReadyHeldRequests` (`:217-230`), same steps with `markRevealCause(RevealCause.GRACE, 2)`, `queuedMarks` and `runQueued` (`:14-20,31-35`): no line after `markReady(2)` + `runQueued(0)`; after `markHeldDrained()` + `runQueued(0)` one line with `cause` `GRACE` and `readyDrainedMs` 1_300.

**Consumes:** `StartupMarks.markRevealCause` (`core/telemetry/StartupMarks.kt:84`), `emitWhenComplete` (`StartupMarks.kt:109-115`, only `FORCED` emits without ready), `TelemetryEncoder` `cause.wire` (`core/telemetry/TelemetryEncoder.kt:73`).
**Produces:** `RevealCause.GRACE` with wire `grace`.

- [x] Write both tests; run `TelemetryEncoderTest` red (does not compile on base); record
- [x] Add the enum value
- [x] Fast gate; `TelemetryEncoderTest`, `StartupMarksTest` green (guards `startupWritesRevealCauseAndImageFields`, `startupOmitsImageFieldsNotReached` unchanged); commit "Add the grace cause to the startup reveal"

**Done when:** both classes pass and `git diff 9158cf82 -- app/src/main/java/com/kino/puber/core/telemetry` is empty.

---

### Task 2: Hold the splash only for requests held at ready, at most G

**Covers:** AC-1, AC-2, AC-3, AC-4
**Files:**
- Modify: `core/startup/RevealGate.kt`:
  - beside `REVEAL_CAP_MS` (`:9`): `internal const val REVEAL_GRACE_MS = 300L`;
  - constructor (`:16-22`): append `private val graceMs: Long = REVEAL_GRACE_MS` and `private val scheduleGrace: (delayMs: Long, task: () -> Unit) -> Unit = { _, _ -> }` after `observer`, KDoc on the latter: called inside the gate lock, must only enqueue, never run `task` inline;
  - `markReady` frame block (`:60-67`): after `held.addAll(inFlight)` and `observer.onReady(held.size)`, `if (forcedCause == null && held.isNotEmpty()) scheduleGrace(graceMs) { onGrace(markedIn) }` (inside `if (begun)`, so `begun` holds);
  - new `private fun onGrace(armedIn: Int) = update { if (generation == armedIn && ready && forcedCause == null) forcedCause = RevealCause.GRACE }`;
  - cover rule (`:109`): `!(ready && inFlight.isEmpty())` becomes `!(ready && held.isEmpty())`; `onRevealed(..., inFlight.size)` (`:113`) unchanged;
  - KDoc of the class (`:11-15`) and of `markReady` (`:49-53`): the splash waits for the requests in flight when ready takes effect, at most `graceMs`; requests started later do not hold it.
- Test: `core/startup/RevealGateTest.kt`:
  - fixture: `frames` (`MutableList<() -> Unit>`), `graces` (`MutableList<Pair<Long, () -> Unit>>`), and `RevealGate(clock = { now }, afterFrame = { frames += it }, observer = observed, graceMs = ..., scheduleGrace = { delay, task -> graces += delay to task })` with a `RecordingRevealObserver` (`core/startup/RecordingRevealObserver.kt:4-28`, unchanged; logs `revealed:GRACE:n`); existing tests keep their gates;
  - rewrite `heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd` (`:231-251`): after `onImageFinished(b)` the log is `ready:2, drained, revealed:READY:1` and `covered` is false, before `c` ends; ending `c` adds no `revealed` entry;
  - add the nine new `RevealGateTest` cases of the Traceability table (AC-1 to AC-4) with the names and assertions written there. In `graceAfterCapReveal_keepsCauseCap` the cap comes after the ready frame (a cap before it arms no G), and "log holds only" means the `revealed` entries of the log.
- Test (guards, unchanged): the other `RevealGateTest` cases, `StartupImageActivityTest`, `StartupImageActivityUnmeasuredTest`, `StartupRevealTest`. If one of them asserts that a request started after ready holds the splash, it is rewritten to the new rule, not deleted, and listed in the Implementation record.

**Consumes:** `RevealCause.GRACE` (Task 1); `update` and its first-cause-wins reveal (`RevealGate.kt:107-116`); generation guard of `markReady` (`:55-61`); `held` drain in `onImageFinished` (`:81-86`).
**Produces:** `RevealGate(graceMs, scheduleGrace)`, `REVEAL_GRACE_MS`.

- [x] Rewrite `heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd` and add the new cases; run red (P0 red: the rewritten test, `graceWithHeldInFlight_revealsWithCauseGrace`, `graceFromBeforeAnotherBegin_isDropped`); record
- [x] Implement the gate changes
- [x] Fast gate; `RevealGateTest`, `StartupImageActivityTest`, `StartupImageActivityUnmeasuredTest`, `StartupRevealTest`; `:app:detektAll`; commit "Hold the startup splash only for images in flight at ready, at most a grace time"

**Done when:** the four classes and detekt pass; `rg -n 'inFlight.isEmpty' app/src/main/java/com/kino/puber/core/startup/RevealGate.kt` shows only the `onImagesIdle` line (`:84`).

---

### Task 3: Run the grace timer on the main looper in production

**Covers:** AC-2, AC-6 (production wiring, R3; proved on device)
**Files:**
- Modify: `core/startup/StartupReveal.kt`:
  - beside `frameHook` (`:13`): `private var graceHook: (Long, () -> Unit) -> Unit = { _, _ -> }`, comment as `:12` (no-op until production startup installs it);
  - gate constructor (`:15-39`): `scheduleGrace = { delayMs, task -> graceHook(delayMs, task) }`;
  - `installFrameWait()` (`:49-51`, called at `PuberApp.kt:82`): also `graceHook = ::postGrace`; KDoc: installs the frame wait and the grace timer;
  - new `private fun postGrace(delayMs: Long, task: () -> Unit)`: `HandlerCompat.createAsync(Looper.getMainLooper()).postDelayed(task, delayMs)` (`androidx.core.os.HandlerCompat`, from `libs.androidx.core.ktx`, `app/build.gradle.kts:352`); one handler kept in a `by lazy` field; comment: async, so the message is not held behind the Choreographer sync barrier;
  - `resetForTests()` (`:75-79`): also `graceHook = { _, _ -> }`.

**Consumes:** `RevealGate(scheduleGrace)` (Task 2).
**Produces:** production G timer; no new public API.

- [x] Implement the hook, install and reset
- [x] Fast gate; full gate; commit "Run the startup grace timer on the main looper"

**Done when:** the full gate is green, and `rg -n 'graceHook' app/src/main/java/com/kino/puber/core/startup/StartupReveal.kt` shows the field, the gate wiring, the install and the reset.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

- 49179d55 Add the grace cause to the startup reveal
- 44b57e6a Hold the startup splash only for images in flight at ready, at most a grace time
- (Task 3) Run the startup grace timer on the main looper
- Round 2, triage-2 item 4 (F4): `RevealObserver.onRevealed` and `StartupMarks` KDoc now say `in_flight` can be above 0 for `ready` and is always above 0 for `grace`; comment-only.

**Red run** (P0 `red` tests on the unchanged code):

- red: `TelemetryEncoderTest.startupWritesGraceCause` - does not compile on base (`Unresolved reference 'GRACE'`)
- red: `RevealGateTest.heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd` (rewritten) - AssertionFailedError at the log check after `b` ends (base reveals only when `c` ends); run against the constructor-param stub with the base cover rule
- red: `RevealGateTest.graceWithHeldInFlight_revealsWithCauseGrace` - NoSuchElementException on `graces.single()` (no grace armed on base)
- red: `RevealGateTest.graceFromBeforeAnotherBegin_isDropped` - NoSuchElementException on `graces.single()` (no grace armed on base)
- the stub for the RevealGate run was only the two constructor parameters with `graceMs` unused and a no-op scheduler; `RevealGateTest` did not compile without them

**Deviations from the plan:** none.

**Files touched outside the plan:** none.

**For the reviewers:**

- `graceAfterHeldDrained_changesNothing` expects `idle` before `drained` (the last in-flight end fires both), so its log is `ready:2, idle, drained, revealed:READY:0`.
- `onGrace` may set `forcedCause` to GRACE after a READY reveal; `revealedOnce` blocks a second report, as the spec's R10 accepts.
- `detektDevDebugUnitTest` prints "7 compiler errors found during analysis" (type resolution warning); the task passes.
- Production wiring of `graceHook` has no JVM seam; proved on device only (R3).

**Fix round 1:** triage item 1: KDoc of `StartupMarks` and `RevealObserver` states what `img_idle` and `in_flight` mean for causes `ready` and `grace`; no behaviour change; no document under `docs/architecture` lists the startup fields.

**Blocked:** none.

## Readiness

# Readiness 1: startup-reveal-gate-on-slow-tv

Verdict: **READY**

Plan: `docs/superpowers/plans/2026-11-23-startup-reveal-gate-on-slow-tv.md` (untracked in the worktree at the time of reading). Spec: `docs/superpowers/specs/2026-11-23-startup-reveal-gate-on-slow-tv.md`. Base `9158cf82`.

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | Task 2 | `imagesSettlingAfterReadyUncovers`, `sameRequestFinishedTwice_doesNotUncoverOthers` (P0 guard), `graceAfterHeldDrained_changesNothing` |
| AC-2 | Task 2, Task 3 | `graceWithHeldInFlight_revealsWithCauseGrace` (P0 red), `markReady_withHeldRequests_armsOneGraceOfGraceMs` |
| AC-3 | Task 2 | rewritten `heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd` (P0 red), two P0 guards, `markReady_withNothingHeld_armsNoGrace` |
| AC-4 | Task 2 | `graceFromBeforeAnotherBegin_isDropped` (P0 red), six `RevealGateTest` guards and `StartupRevealTest`, four P1/P2 cases |
| AC-5 | Task 1 | `TelemetryEncoderTest.startupWritesGraceCause` (P0 red), two guards, `StartupMarksTest.graceRevealLineWaitsForHeldDrain` |
| AC-6, AC-7, AC-10 | qa-device after Task 3 | Manual checks steps 1-7, Perf track |
| AC-8 | qa-device, owner | Manual check step 6 |
| AC-9 | qa-device | Manual check step 8 |

## Citations sampled (worktree at base)

| Citation | Resolves to | OK |
|---|---|---|
| `RevealGate.kt:9` | `REVEAL_CAP_MS = 2_500L` | yes |
| `RevealGate.kt:60-67`, `:64` | `markReady` frame block, `held.addAll(inFlight)` | yes |
| `RevealGate.kt:72,75` | `onCap`, `revealNow` first-cause-wins | yes |
| `RevealGate.kt:81-86`, `:84` | `onImageFinished`, `onImagesIdle` line with `inFlight.isEmpty()` | yes |
| `RevealGate.kt:109`, `:113` | cover rule with `inFlight.isEmpty()`, `onRevealed(..., inFlight.size)` | yes |
| `RevealObserver.kt:4-8` | `RevealCause` enum | yes |
| `StartupReveal.kt:13`, `:49-51`, `:75-79` | `frameHook`, `installFrameWait`, `resetForTests` | yes |
| `PuberApp.kt:82` | `StartupReveal.installFrameWait()` | yes |
| `app/build.gradle.kts:352` | `implementation(libs.androidx.core.ktx)` (core 1.19.1, has `HandlerCompat.createAsync`) | yes |
| `StartupMarks.kt:31`, `:84`, `:109-115` | `schedule` param, `markRevealCause`, `emitWhenComplete` (only `FORCED` emits without ready) | yes |
| `TelemetryEncoder.kt:73` | `put("cause", it.wire)` | yes |
| `TelemetryEncoderTest.kt:136`, `StartupMarksTest.kt:14-20,31-35,217` | named tests and fixtures | yes |
| `RevealGateTest.kt:231-251` | `heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd` (expects `revealed:READY:0` after `c` ends) | yes |
| `MainActivity.kt:83-86` | cap `LaunchedEffect` | yes |

## Checks

| Check | Result |
|---|---|
| Build green per task | Task 1 adds an enum value; the only `RevealCause` branch in main is the `==` at `StartupMarks.kt:115`, no exhaustive `when`. Task 2 adds defaulted constructor params, existing callers compile. Task 3 consumes Task 2. |
| Order | Task 1 -> 2 -> 3 by `Consumes`/`Produces` |
| Existing tests under the new cover rule | Every other `RevealGateTest`, `StartupImageActivityTest` and `StartupImageActivityUnmeasuredTest` case starts its requests before the ready frame, so they stay valid; only the rewritten test asserted waiting on a post-ready request |
| P0 unit rows | Each names the mutation it catches, is marked `red` or `guard`, names a backticked test |
| P0 device rows (AC-6, AC-7) | Manual by nature; defects named as R3, R4, R7 in the Risk profile, pass bounds in the Perf track |
| Detekt | `LongParameterList` is inactive (`config/detekt/detekt.yml:121-122`); the constructor reaches 6 params |
| Out of scope | Plan leaves `REVEAL_CAP_MS`, `MainActivity.kt`, `StartupSplashHost.kt`, `StartupSettled`, `StartupMarks`/`TelemetryEncoder` production code and the baseline profiles untouched |
| Blocking open questions | None; Q1-Q4 answered |

## Notes for the implementer and qa

| # | Note |
|---|---|
| 1 | The spec cites the baseline as `release-startup-compilation-device.md:139-260`; the file has 259 lines. |
| 2 | Task 3 has no JVM test of the `graceHook` wiring; R3 is proved only by `grace` causes and the AC-6 max bound on the box. |
