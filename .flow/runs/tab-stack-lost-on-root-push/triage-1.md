# Review triage - round 1

Run: tab-stack-lost-on-root-push   Base: f14dcc84   Reviewed head: 59cdb9c3

Sources: review-blind-1.md (no findings), review-edge-1.md, review-tests-1.md, review-codex-1.md (ran: review, adversarial). qa round 2: all ACs PASS except AC-7 NOT CHECKED (T4 stand-in).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | codex | minor | `ShowAllScreenContent.kt:86-96` | `contentFocusActive` is captured once; a retry after the rail opened in the first frames pulls focus back into the grid | Read the effect: the flag is read at composition and never re-checked in the loop; holds | fix | Read the current flag (`rememberUpdatedState`) before each `requestFocus` |
| 2 | edge F2 | minor | `ShowAllScreenContent.kt:84-97,130-131`, `ShowAllVM.kt:98-107` | No fallback when the opener card leaves the list after Back (hide-watched reload); focus is lost or jumps to the rail | Read the effect and `onReturnedContentChanges` (reload on a non-empty change set); holds; new path because the grid now survives the root push | fix | Keep the focused index with the id; when the id is missing, focus the item at `min(index, lastIndex)` (or reuse `resolveFocusedItemId`) |
| 3 | tests F1 | minor | `ShowAllScreenContentFocusTest.kt:44-56` | The focus test never restores a card outside the first viewport and was never seen red | Read the test; holds | fix | Add a scrolled case (card below the first viewport); qa records a red half by running the new case with the restore effect disabled only if cheap, otherwise the reasoning stands |
| 4 | tests F2 | minor | `TabComponent.kt:139-153`; qa observation | Settings sub-selection resets after a tab switch; not compared with base, could be a regression of this run (AC-8) | Not decidable from code: the per-tab Voyager `saveableState` also provided a lifecycle owner | reject (pre-existing) | qa base comparison (qa-report.md "Base comparison"): base f14dcc84 also shows General after a tab switch; settings-subselection-reset stays a separate idea |
| 5 | edge F1 | minor | `FlowComponent.kt:591-596`, `TabComponent.kt:117-131` | A tab refreshed while covered flashes its restored stack on Back, and the Back line names the stale screen | Read `MainVM.onContentPreferencesChanged` path and the replayed Open; holds; needs a content-preference change while covered | defer | New run tab-refresh-while-covered-flash (stage idea) |

## Next

- Implementer fix mode for #1-#3; qa base-vs-HEAD Settings comparison (#4) plus affected ACs (AC-2, M2); then lens edge + tests + codex on the increment.
