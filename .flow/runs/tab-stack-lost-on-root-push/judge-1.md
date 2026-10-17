# Gate - tab-stack-lost-on-root-push - round 1

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** CONCERNS
**Head:** 62825360   **Base:** f14dcc84

## Done as asked

I read `git diff f14dcc84...HEAD` myself. The worktree was clean.

- D1/D2: `TabComponent.kt` creates one `rememberSaveableStateHolder()` and exposes it as `LocalTabSubtreeStateHolder`. `PuberCurrentTab` now uses `stateHolder.SaveableStateProvider(currentTab.key)` in place of `tabNavigator.saveableState`, with a `checkNotNull` that fails fast. This matches the spec.
- D3: the root navigator's dispose flag is untouched. `FlowComponent.kt` changes only the telemetry lambda.
- D4/D5: `reappearingTop = { navigator.lastItemOrNull }`, and the comment and the `NavTelemetry` KDoc are rewritten. `unbind` has no `isEnabled` gate. grep finds only `NavTelemetry.kt:63`, in `recordNav`.
- D6: `NavTelemetryTest` has the rename only.
- D7/D8: T1 asserts `WiringChildScreen` after Back and that no line names `WiringHomeScreen` (`TabHostTelemetryWiringTest.kt:162-182`). `TabFlowLifecycleTest` uses `substring = true` at :122, :131, :134, :137 and :175.
- D9: the ShowAll focus restore stays inside `ShowAllScreenContent.kt`, which is the only file it touches. The Task 5 commit (6b47873d) was +27 lines, within the 30-line budget. Fixing review round 1 (b5919b85) grew it to about 95 lines: a `GridFocusMemory` class, a neighbour fallback, and a re-check on each attempt. That is beyond the spec's budget wording, but triage chose the growth and it stays in the one file.
- AC-11: the Tabs section of `docs/architecture/navigation.md` names the holder's owner, its key path, the reason and its lifetime.
- Out of scope is respected. Removed tabs are not cleared. Grid focus is not generalised to other screens; it is recorded as `grid-focus-restore-after-back`. settings-tab-spinner-stuck is not fixed here. The nested-dispose flag is unchanged.

## Traceability

| AC | Test / manual check | Evidence | Status (PASS / FAIL / NOT CHECKED / NOT VERIFIED) |
|----|---------------------|----------|----------------------------------------------------|
| AC-1 | T1, T2 (itest); M1 on device | qa-prefix-red: T1/T2 red on the pre-fix build. Rounds 2 and 3 device evidence: grid shown at the same rows, and the round-1 shift is gone. TabHostTelemetryWiringTest 10/10 | PASS |
| AC-2 | T8 ShowAllScreenContentFocusTest (5 cases); M1 steps 7-8 | Round 1 was PARTIAL. Rounds 2 and 3 show the opener focused, including a deep card (Valeriy Leontiev). FocusTest 5/5 | PASS (opener-removed fallback: NOT CHECKED on device, itest only) |
| AC-3 | T2; M6 | Round 2: Movies/4K stacks kept across a root push and rail switches. T2 red before the fix | PASS |
| AC-4 | T1; NavTelemetryTest rename | I re-read T1: `navs.first().to == WiringChildScreen` and no Home lines. Device log shows `nav back from=Details to=ShowAll` | PASS |
| AC-5 | TabHostTelemetryWiringTest class | 10/10 on emulator-5554 in rounds 1, 2 and 3 | PASS |
| AC-6 | grep plus NavTelemetryTest | I re-ran the grep: the only `isEnabled` is at :63, outside `unbind`. My check.full run is green | PASS |
| AC-7 | T4 `replacedHostStartsAtTabRootsWhenShownAgain`; M4 device | T4 is green, and review-tests-1 shows it would catch a holder that lives too long. M4 (real logout/relogin) was not run because a worktree build cannot log in again | NOT CHECKED (device), carried over from qa; T4 stand-in green |
| AC-8 | T2, TabFlowLifecycleTest; M5 | Rail switches keep the stacks. Settings shows no spinner after a root push. The Settings sub-selection reset happens on base too (qa base comparison) | PASS |
| AC-9 | T5 (`resumeIsDelivered...`); M3 | T5 was red before the fix. On device, exactly one `act OnResume scr=Home` after Back | PASS |
| AC-10 | TabFlowLifecycleTest | 1/1 on device in rounds 1, 2 and 3; I sampled the substring fix in code | PASS |
| AC-11 | check.full plus a docs read | I ran check.full myself at 62825360: BUILD SUCCESSFUL, exit 0. Docs read | PASS |

## Findings

| Finding | Source | Disposition | Proof (commit, new run, accepted reason) |
|---------|--------|-------------|------------------------------------------|
| T1-1 contentFocusActive captured once (rail race) | codex-1 | fixed | b5919b85 (`rememberUpdatedState`, checked on each attempt); edge-2 confirmed it |
| T1-2 no fallback when the opener leaves the list | edge-1 F2 | fixed | b5919b85 (`resolveRestoreId` plus `restoreWhenFocusedCardLeft`); itest only |
| T1-3 focus test never covers a scrolled card or a red run | tests-1 F1 | fixed | b5919b85 (new deep-card case); the red half was argued, not run |
| T1-4 Settings sub-selection reset as a possible regression | tests-1 F2 | rejected (pre-existing) | Spot-checked: qa-report "Base comparison" shows base f14dcc84 with "General" twice. Reason accepted |
| T1-5 refreshed-while-covered flash and stale Back line | edge-1 F1 | deferred | New run `tab-refresh-while-covered-flash` (stage idea) exists |
| T2-1 reload inside the 3-frame entry window leaves no focus | edge-2 F1, codex-2 | deferred | New run `showall-focus-restore-hardening` exists. Spot-checked codex's "do not ship" (medium) regraded to minor: it needs an async reload that is in flight across the push and publishes within about 50 ms of re-entry, and the cost is focus only. Reason accepted |
| T2-2..T2-4 test-strength gaps (entry fallback, mid-retry check, deep guard) | tests-2 F1-F3 | deferred | `showall-focus-restore-hardening` |
| codex-1 rejected "key the effect on contentFocusActive" | codex-1 | rejected | Spot-checked: re-keying would relaunch the restore whenever the rail closes and steal focus. Reason accepted |

None of the findings is a blocker or a major. Nothing was silently dropped.

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | The holder is one `SaveableStateHolder` per host. The focus memory reads its saved values with `Snapshot.withoutReadObservation`, so a focus move only writes saveable state and the grid does not recompose. `LaunchedEffect(items)` restarts on each page append; it is cheap (an `any`/`none` scan) and does nothing while the focused id is present. Acceptable. |
| Reliability | Logout replaces the host and the holder with it (T4). Process death works: the holder lives in MainScreen's saveable slot and the focus id/index are saveable. Empty state: the body leaves composition and `resolveRestoreId` guards an empty list. Known gaps, deferred: the 3-frame reload race, and the refresh-while-covered flash. The per-tab Voyager `ScreenLifecycleOwner` is gone, but ON_RESUME delivery is guarded (T5, AC-9, AC-10). |
| Maintainability | The change follows AGENTS conventions: `staticCompositionLocalOf` next to `LocalTabAppRouterHolder`, the `checkNotNull` pattern, and the change kept in ShowAll. `GridFocusMemory` is close to `rememberReconciledItemFocus` / `resolveFocusedItemId` in `FocusTargetState.kt` but does not reuse them: some parallel logic, but no copied helper. `grid-focus-restore-after-back` is the place to unify it. No dead code is left: the old comment and KDoc are updated, and `tabNavigator.saveableState` has no other callers in the diff. |
| Security / privacy | The diff adds no secrets or account data. `tools/mobile-evidence-audit.sh` on `docs/superpowers/evidence`: passed, 22 files. Evidence names public catalogue titles only. |

## Must fix

none

## Concerns for the owner

1. AC-7 is NOT CHECKED on a device. Real logout/relogin (M4) needs a build that can log in. Only the T4 itest stand-in is green.
2. The opener-removed fallback (AC-2 edge) was not checked on a device, because it needs playback to the end. Only the itest is green, and tests-2 F1 shows its entry-path case actually runs the live path.
3. D9 budget: the ShowAll focus fix was about 30 lines at Task 5 and grew to about 95 lines in that one file through review fixes. That is beyond the spec's "about 30 lines, otherwise defer" wording, but the scope is unchanged.
4. Deferred to new runs: `tab-refresh-while-covered-flash` (a visible flash plus one stale Back telemetry line after a content-preference change while covered; before this run the frozen line matched the final screen) and `showall-focus-restore-hardening` (the 3-frame reload race and test-strength gaps). Other ideas recorded: `grid-focus-restore-after-back`, `showall-first-entry-rail-focus`, `settings-subselection-reset`. Their run folders in the main checkout are untracked.
5. Device actions to know about: for the base comparison, qa installed a main-checkout base APK on emulator-5554 with install-preserve (`compatible_replace`, data preserved), then reinstalled the worktree APK (sha256 verified). During that comparison a scripted mis-navigation briefly opened a History detail and started a movie for a few seconds before BACK. The account may have recorded that as watch progress.
6. The squash-before-ship preference applies: the branch has 13 commits.
