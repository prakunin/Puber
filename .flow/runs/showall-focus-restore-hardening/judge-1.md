# Gate - showall-focus-restore-hardening - round 1

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** CONCERNS
**Head:** 3b0aeb04   **Base:** 35bfbbec

## Done as asked

Read `git diff 35bfbbec...HEAD` myself. Production change is confined to
`ShowAllScreenContent.kt`, as the plan scoped it:

- `restoreOnEntry` and `restoreWhenFocusedCardLeft` are merged into one `reconcile()`.
  It re-resolves `restoreId` on every run while `pendingEntry` is true, which is the race fix
  (AC-1). The live path (`inGrid && focusedCardLeft`) is unchanged.
- `pendingEntry` is read unobserved at construction and cleared on the first grid focus.
  Nothing re-arms it.
- `requestRestoreFocus()` reads `contentFocusActive()` on every attempt and returns a Boolean
  (AC-4).
- Fallback C (`requestGrid()`) runs once, only after all targeted attempts fail, only while
  `pendingEntry` is set, and only while content focus is active (AC-5). The live path stays
  without C, as Out of scope requires.
- The requesters are injected (`requestRestore`, `requestGrid`), and `GridFocusMemory` and
  `resolveRestoreId` are now `internal` so the JVM tests can reach them.
- `LaunchedEffect(items, contentFocusActive)` replaces the two effects. The extra key comes from
  triage-1 #1.

Tests: there is a new `GridFocusMemoryTest`. Its stored result has 23 tests and 0 failures,
timestamped at cc66ec96. In `ShowAllScreenContentFocusTest`, AC-2 now uses a plain var, so the
entry fallback really runs, and it asserts that the removed card does not exist. AC-7 has the
`>= DeepRows * GridColumns` guard and the "card 1 not displayed" assertion.

Nothing excluded was touched. There are no changes to the VM, DI, other grids, the first-entry
rail or a shared helper, and `resolveFocusedItemId` / `rememberReconciledItemFocus` are not used
(D8).

## Traceability

| AC | Test / manual check | Evidence | Status (PASS / FAIL / NOT CHECKED / NOT VERIFIED) |
|----|---------------------|----------|----------------------------------------------------|
| AC-1 | U-AC1a/b/c (`GridFocusMemoryTest`) | 23/23 green. Red on Step A recorded in the plan's Implementation record (lines 391-393: `restore:5:false` instead of `restore:6:true`). The race cannot be forced on a device | PASS (JVM only) |
| AC-2 | I-AC2 rewrite + U-AC2a | qa: class 5/5 green; M-AC2 red by `ComposeTimeoutException`. I checked the diff: plain `var shown` plus `assertDoesNotExist` | PASS |
| AC-3 | 7 `resolveRestoreId_*` cases | green; all branches present (sampled names, lines 32-62) | PASS |
| AC-4 | U-AC4 + companion | green; red-once recorded (`expected: <1> but was: <3>`, plan line 400). qa did not re-run it | PASS |
| AC-5 | U-AC5a/b/c | green; red on Step A recorded (plan line 394). Compose wiring of `requestGrid` is checked by review only (plan line 410) | PASS |
| AC-6 | U-AC6a-e + live-path no-C case | green | PASS |
| AC-7 | I-AC7 rewrite | qa: green; M-AC7 (`repeat(2)`) red, "focus moved below the first viewport" | PASS |
| AC-8 | instrumented class | 5/5 at d6b51a2e and again at cc66ec96 (round1-fixes evidence) | PASS |
| AC-9 | D-M1, D-M2, D-M3 | D-M1 and D-M2 PASS twice (both evidence files). D-M3: trigger found, a card was focused, but the exact clamped neighbour could not be read on the device | PASS (M1/M2); opener-removed exact neighbour PARTIAL, covered by JVM and I-AC2 as AC-9 allows |
| AC-10 | check.full | qa ran it at cc66ec96: BUILD SUCCESSFUL. HEAD 3b0aeb04 adds only an evidence `.md`. qa-report.md (13:45) is newer than the last commit (13:38), so I did not re-run it | PASS |

## Findings

| Finding | Source | Disposition | Proof (commit, new run, accepted reason) |
|---------|--------|-------------|------------------------------------------|
| No blockers or majors in any review | review-*-1/2 | - | grep found no blocker or major |
| Pending entry restore not retried when content focus turns active | edge-1 F1 (minor) | fixed | cc66ec96 (effect key + JVM case `reconcile_entryWithContentInactiveRestores...`) |
| `pendingEntry` gate on C untested for the live path | tests-1 F1 (minor) | fixed | cc66ec96 (`reconcile_liveGridFocusedCardRemovedAllAttemptsFailingNeverFocusesTheGrid`) |
| `requestGrid` Compose wiring untested | tests-1 F2 (minor) | fixed (docs) | plan line 410 |
| Foreign focus landing + list change clears `pendingEntry` | edge-1 F2 (minor) | deferred | run `showall-entry-restore-foreign-landing` exists |
| Effect key `contentFocusActive` not pinned by a test | tests-2 F1 (minor) | deferred | folded into `showall-entry-restore-foreign-landing` (state.md line 33) |
| Rail close lands one row above | qa observation | deferred, pre-existing | base comparison 3/3 on 35bfbbec; run `showall-rail-close-focus-shift` exists |
| Codex-2 rejection: Compose-level test for the effect key | codex-2 | rejected by codex-reviewer | Accepted. It is the same item as tests-2 F1, which is tracked in a deferred run, so nothing is lost |
| Codex-2 rejection: device evidence predates the increment | codex-2 | rejected as a process note | Accepted. qa re-ran the device checks at cc66ec96 (round1-fixes evidence, commit 3b0aeb04) |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | No new recomposition source. The saved values and `pendingEntry` are read with `Snapshot.withoutReadObservation`. The extra effect key restarts `reconcile()` on each rail open or close, and it returns at once when nothing is pending: O(n) `items().none` only when `inGrid`. Negligible |
| Reliability | An empty list gives a null `restoreId`, so no request and no C. Cancellation: a list change cancels the loop before C, so the budget is per change. Process death: the saveable id and index are restored and `pendingEntry` is recomputed from them. `requestFocus` exceptions are still swallowed via `tryRequestFocus`. One known narrow race is deferred (foreign landing) |
| Maintainability | Follows the plan's scope and AGENTS conventions (JUnit 5 tests, single file). Dead code is removed (`restoreOnEntry`, `restoreWhenFocusedCardLeft`, member `restoreFocus`). `tryRequestFocus` is a private local extension, and the shared helper is out of scope (focus-request-helper). The `internal` visibility exists only for tests, which is acceptable |
| Security / privacy | No secrets or account data in the diff. I re-ran `mobile-evidence-audit.sh` on both evidence files: `passed`. Screenshots were not stored |
| TV | Focus and Back are verified on the emulator (D-M1, D-M2, rail open/close x5, no older-card jump). Overscan is untouched |

## Must fix

none

## Concerns for the owner

1. AC-1 (the reload-in-entry-window race) is proven only on the JVM seam. A device cannot force it.
2. AC-9: on the device, qa saw that a card took focus after the opener was removed, but could
   not confirm it was the exact clamped neighbour, because the grid re-paged. The neighbour logic
   is covered by the JVM tests and I-AC2.
3. Two things have no automated test: the Compose wiring of fallback C (`requestGrid` ->
   `mainContentFocus`) and the `contentFocusActive` effect key. Both were checked only by review
   and device behaviour. The effect-key test is deferred to `showall-entry-restore-foreign-landing`.
4. Deferred runs: `showall-entry-restore-foreign-landing` (edge-1 F2 + tests-2 F1) and
   `showall-rail-close-focus-shift` (pre-existing one-row-up landing on rail close, same on base).
5. Installs on emulator-5554 used `install-preserve` (`compatible_replace`, same signer, not
   destructive). This includes a base 35bfbbec APK installed for the comparison, then the
   worktree APK reinstalled. The on-device sha256 equals the cc66ec96 build. No uninstall, data
   clear or downgrade. The "Hide watched content" preference was toggled for D-M3 and restored to
   OFF.
6. The branch has 9 commits. Squash it to fewer than 5 before `/task:ship`, per the owner's
   standing preference.
