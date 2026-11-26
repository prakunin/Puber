# Gate - main-dead-code-leftovers - round 1

**Decision:** PASS
**Head:** 2f78b8af   **Base:** 55bf932d

## Done as asked

The diff (`git diff 55bf932d...HEAD -- app`) deletes `PagingColumn`, `MainVM.onSearchClick` / `onSettingsClick`, and sets `remember(vm) { vm::onAction }` at `MainScreenComponent.kt:77`, as brief.md#Plan steps 1-3 ask. It also deletes `PagingListState` and `PagingListPageError` (2e3ef77c), which the brief's findings said stay; triage-1 #1 widened the scope because they had no caller left. Accepted: same goal (dead code), `rg` over `app` and `baselineprofile` finds no reference at head. `router` stays used (`MainVM.kt:38`); `LoadMoreHandler` and `ListItemError` stay used (`SectionRow.kt`, `HistoryScreenContent.kt`). Nothing excluded is touched.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | brief AC-1 `rg`, widened to the two deleted types | re-run by the judge at 2f78b8af: no match over `app`, `baselineprofile` | PASS |
| AC-2 | read `MainScreenComponent.kt:77` | diff hunk: `remember(vm) { vm::onAction }` | PASS |
| AC-3 | `check.full` | re-run by the judge at 2f78b8af: BUILD SUCCESSFUL, exit 0 | PASS |
| AC-4 | box smoke on 192.168.1.110:5555 | `docs/superpowers/evidence/2026-11-26-main-dead-code-leftovers-device.md`; screenshots `5-cold-t3` (Main before HOME), `5-relaunch`, `4-rail-tab` (Bookmarks selected) checked by the judge | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| `PagingListState` / `PagingListPageError` unused after the change | review-blind-1 (unproven) | fixed | 2e3ef77c |
| Cold-launch evidence row showed only a black frame before HOME | review-codex-1 (minor) | fixed | 2f78b8af, screenshot `5-cold-t3` |
| `remember(vm)` key covered only by the smoke | review-tests-1 (unproven) | rejected | accepted: no behaviour to test while `vm` is stable in its `DIScope` |
| Historical 2026-10-12 spec and plan name the deleted types | review-edge-2 (unproven) | rejected | accepted: dated records, not live references |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | No runtime path changed; `baseline-prof.txt` names no removed member. |
| Reliability | No state, error or lifecycle path touched. |
| Maintainability | Matches the `remember(vm)` convention (`docs/architecture/ui-and-compose.md:63`); no import or helper left orphaned. |
| Security / privacy | Evidence holds no tokens or account data; qa reports `mobile-evidence-audit.sh` passed. |

## Manual checks for the owner

None.

## Must fix

None.

## Concerns for the owner

None. The D-pad rail-tab row of AC-4 was run on the round-1 APK; the only change since is deletion of uncalled types, which the green compile covers.
