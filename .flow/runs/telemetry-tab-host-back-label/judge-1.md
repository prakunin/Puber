# Gate - telemetry-tab-host-back-label - round 1

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** CONCERNS
**Head:** 69b74347   **Base:** bdd2c792

## Done as asked

I read `git diff bdd2c792...HEAD` myself. It changes three production files and nothing beyond what the spec asks for:

- `NavTelemetry.kt`: `Binding` gains `reappearingTop` and `bind` has a single 3-arg form with no default (A2). `unbind` keeps the handle-identity guard. When the handle is current it reads `reappearingTop()` once and stores a frozen `Binding(host, keep, keep)` (D1/D10/A1). `resetForTests()` is `@VisibleForTesting`. `topOf`, `navClassOf`, `recordNav` and `recordNavCommand` are unchanged, and `recordNav` is still the only writer of `Telemetry.screen`. The KDoc is updated.
- `FlowComponent.kt:589-599`: the only production `bind` caller (grep confirms). It passes `top = { navigator.lastItemOrNull }` and `reappearingTop = { navigator.items.firstOrNull() }`, and its comment names the follow-up run (D12).
- `TabComponent.kt:118`: `if (from !== event.tab) recordNav(...)` uses identity, with a comment explaining why. `navigator.current` is still assigned unconditionally (D5/D6). I confirmed that every `MainVM.openTab` call site (`:51,116,125,151,165`) builds a new `PuberTab`.

The tests are `NavTelemetryTest` (the old `clearedBindingGivesTheFallback` is replaced on purpose, plus 9 new cases) and the new `androidTest/.../TabHostTelemetryWiringTest.kt` (6 tests). The diff makes no Robolectric or build change, does not restore the tab stack, does not touch `act Resumed` or `TabFlowLifecycleTest`, and adds no UI change. Everything excluded by Out of scope stays out.

## Traceability

| AC | Test / manual check | Evidence | Status (PASS / FAIL / NOT CHECKED / NOT VERIFIED) |
|----|---------------------|----------|----------------------------------------------------|
| AC-1 | `NavTelemetryTest.disposedBindingKeepsTheTopForTheSameHost` | Read in the diff. My check.full run: NavTelemetryTest 18/18, 0 failures | PASS |
| AC-2 | `frozenBindingOfHostADoesNotLeakIntoFreshHostB` + existing `staleBinding...FreshHost` | Same run | PASS |
| AC-3 | JVM `liveBindReplacesAFrozenTop`, `tabSwitchUnbindOldThenBindNewShowsNewTop`, `tabSwitchBindNewThenUnbindOldKeepsNewTop`; itest `tabSwitchThenRootPushAndBackNamesTheNewTab` | JVM green (my run). The itest was red before the fix (qa-prefix-red.md, kt:175) and 6/6 green at b256e3a5 (evidence, "Review round 1 fixes") | PASS |
| AC-4 | itest `backOntoTheHostRecordsNoTabReannounce`; device M1 | Red before the fix (kt:113), green 6/6 at b256e3a5. M1 shows no `nav tab Home->Home` | PASS |
| AC-5 | itests `openingAnotherTab...`, `reselectingTheCurrentTabWithANewInstanceIsRecorded` (it asserts `==` and `!==`, so it pins identity); device M3 | Green 6/6. M3 recorded `ContentList->ContentList` on a rail reselect | PASS |
| AC-6 (rewritten) | itest `tabLocalPushThenRootPushThenBackNamesTheScreenShownAfterBack` (also asserts the tab-local PUSH `from=WiringChildScreen`); JVM `unbindFreezesTheReappearingTopNotTheLiveTop`; device M2 | The itest was red at 70200dee (`expected WiringHomeScreen but was WiringChildScreen`) and is green at e38d19fe and b256e3a5. M2 round 2: `nav back to=ContentList`, `scr=ContentList`, no ShowAll after Back | PASS |
| AC-7 | itest `backFromRootPushedScreenNeverRecordsMain` | Red before the fix (kt:100, host class), green 6/6 at b256e3a5 | PASS |
| AC-8 | check.full | I ran it myself at 69b74347: exit 0, 1996 tests, 0 failures, 0 skipped, detekt and lint green. The code inputs are unchanged since b256e3a5, so the tasks were up to date. The other NavTelemetryTest cases keep their bodies; only the helper is routed through `bindSame` | PASS |
| AC-9 | `nothingIsRecordedWhenOff` (primary guard), `unbindWithTelemetryOffRecordsNothing`, regression `unbindWhileOffThenEnabledThenBackNamesTheTabRoot` | JVM green. `unbind` has no record path (I read it). The AC wording "records anything" still holds, but the freeze now runs even when telemetry is off (see Concerns) | PASS |
| AC-10 | Device M1 | Rounds 1 and 2 and the review-fix round: `nav back from=Details to=Home`, `scr=Home`, `act Resumed scr=Main`, no `to=Main`, focus kept. One `frames scr=Details` window over Details closes after the Back line, which is by design | PASS |
| triage-1 #1 device path | none reachable | qa: NOT CHECKED (`MainVM.onSettingsClick` has no caller; I confirmed with grep). Only the JVM regression test covers it | NOT CHECKED (carried over) |

## Findings

| Finding | Source | Disposition | Proof (commit, new run, accepted reason) |
|---------|--------|-------------|------------------------------------------|
| Telemetry enabled while the host is covered gives Back `Main`, uncorrected because of the replay guard (major) | review-edge-1 F1 / triage-1 #1 | fixed | 572e252a. `unbind` has no `isEnabled` branch (read in the diff). The regression test is green |
| Live `top` wiring untested (minor) | review-tests-1 F1 / triage-1 #2 | fixed | b256e3a5. The itest asserts the PUSH `from=WiringChildScreen` (read in the test) |
| tearDown leaks frozen bindings (nit) | review-tests-1 F2 / triage-1 #3 | fixed | 572e252a. `TabHostTelemetry.resetForTests()` is called in the JVM tearDown |
| 2-arg `bind` is test-only with different semantics (nit) | review-blind-1 F3 / triage-1 #4 | fixed | 572e252a. The overload is gone and tests use `bindSame` |
| Tab changed while covered: Back names the old tab root briefly (minor) | review-blind-1 F1 / triage-1 #5 | rejected, accepted | Spot-checked. `MainVM.kt:55-60` collectors can `openTab` while the host is covered. The replayed Open is a new instance, so the `nav tab old->new` line follows in the same re-entry and corrects the label. This is design risk R3, accepted at gate 1. Base logged `Main` here, which was just as wrong. I accept it |
| Frozen binding retains a dead host plus one screen (nit) | review-blind-1 F2 / triage-1 #6 | rejected, accepted | Spot-checked. `topOf` checks host identity, so the binding is inert. It holds one screen object, replaced at the next bind, and the live binding already pinned the host on base. This is R5. I accept it |
| Spec still described the removed gate (minor) | review-edge-2 F1, review-tests-2 F1 / triage-2 #1 | fixed | 69b74347, design amendment A1-A3 plus Superseded list (read in the spec) |
| `unbindWithTelemetryOffRecordsNothing` cannot fail (nit) | review-tests-2 F2 / triage-2 #2 | rejected | It is harmless. The spec now points AC-9 at `nothingIsRecordedWhenOff`. I accept it |
| itest tearDown does not reset `TabHostTelemetry` (nit) | review-tests-2 F3 / triage-2 #3 | rejected | Every test method composes a fresh `WiringTabHostScreen()`, so host identity keeps a leftover binding inert. I accept it |
| Codex rounds 1-2 | review-codex-1/2 | clean | n/a |

No blocker or major was silently dropped.

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Tab-flow dispose now does one snapshot read and one small allocation, even when telemetry is off. The replay guard removes one record. No composable signature or state-read change, so recomposition and the baseline profile are not affected. |
| Reliability | Empty stack: `items.firstOrNull()` is null-safe and falls back to the host class as before. Tab-switch dispose order is safe in both orders (handle guard, tested). Process death: the binding is lost, and MainVM's init `openTab` builds a new instance, so a TAB line corrects the label. Configuration change: the design accepts it (R2, re-bind before any Back). All callers run on the main thread, and `@Volatile` stays. |
| Maintainability | Follows the conventions: `internal`, a `@VisibleForTesting` reset, JUnit 5 for JVM, and the itest in its own class with no shared fixtures. No dead code is left: the 2-arg `bind` was removed and grep finds no stray callers. The coupling to Voyager's dispose of the covered tab is documented at the single bind site, and the AC-6 itest guards it. The architecture doc does not describe telemetry, so no doc drift. |
| Security / privacy | No secrets or account data in code or tests. The evidence keeps only `nav`/`scr`/`act` fields and hashed APK and signer prefixes. I re-ran `tools/mobile-evidence-audit.sh` on the evidence dir at HEAD: passed (19 files). Commits have no Co-Authored-By. |
| TV | Focus after Back was checked on the device (same tile focused in M1 and M2). There is no D-pad, overlay or overscan change. |

## Must fix

none

## Concerns for the owner

1. AC-9 now means less than gate 1 approved. Under D8, nothing was read and no state was frozen while telemetry was off. The conductor changed this in review round 1 (amendment A1, triage-1 #1): `unbind` now freezes one screen object even with telemetry off. The AC text still holds because nothing is recorded, but this design change was made by the conductor, not by the owner.
2. The fix for triage-1 #1 is covered only by a JVM test. Its device path is NOT CHECKED because no UI path reaches it today. If a root-pushed screen with the session-log toggle is added later (`MainVM.onSettingsClick` exists but has no caller), that path should be checked on a device.
3. AC-6 deliberately pins today's lost tab stack. When run `tab-stack-lost-on-root-push` lands, the AC-6 itest goes red, and that run must switch `reappearingTop` to the live top (FlowComponent.kt bind site) and restore the original AC-6.
4. `TabHostTelemetryWiringTest` is outside `check.full`. It runs only via `make DEVICE=<serial> itest TESTS=...`, and it was last run green at b256e3a5 on emulator-5554. The code has not changed since then.
5. All device installs were same-signer `install -r` through `tools/android-apk-install-preserve` (`compatible_replace`, non-destructive). None needed the owner's separate authorisation, and there was no uninstall, clear or downgrade. On the device, one stray D-pad sequence opened a player at position 0:00 for a few seconds. qa reports no state change.
6. The branch has 21 commits. The owner's preference is fewer than 5 per run, so squash before `/task:ship`.
