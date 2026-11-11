# Review triage - round 1

Run: carousel-row-duplicate-cards   Base: 901d820d   Reviewed head: afc1e706

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial), extras: none (track none, no Compose layout in the diff).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1, codex 2 | major | `docs/superpowers/evidence/2026-11-11-carousel-row-duplicate-cards-device.md` AC-7/AC-8 | The device runs never triggered the row refresh, so the restore path and focus after de-duplication were not exercised on device. | Read `ContentListVM.kt:147-159` and `SectionVM.kt:53-61`: a refresh starts only from a non-empty `ContentChangeSet` (save/unsave/watched) or player/history sync; open + BACK alone does not refresh. Holds. | fix (record) | Cannot be closed without an account mutation (save/unsave), which is not authorised (AGENTS.md: account mutations need explicit authorisation). AC-7 and AC-8 refresh half carried to the gate as NOT CHECKED and listed as an owner manual check; the unit tests carry the restore proof. Not rounded up. |
| 2 | tests F2 | minor | `ContentListPagingVM.kt:198`, `SectionVMRefreshDepthTest.kt` ceiling test | Dropping only the `MAX_RESTORED_PAGES` coercion is caught by no test. | Traced: with the counter, depth 12 and `current == requested` still stops at page 10; only a read-ahead deep row differs (2,4,6,8,10 vs also 12). Holds. | fix | implementer: add a read-ahead ceiling test asserting pages after the walk are exactly 2,4,6,8,10. |
| 3 | codex 1 | minor | `ShowAllVMTest.kt:272-307` | Polling a `synchronizedList` with `none{}`/`first{}` while the mapper appends on `Dispatchers.Default` can throw `ConcurrentModificationException`. | Read the test: iteration is not under `synchronized(mapped)`; the mapper runs off the test thread. Holds. | fix | implementer: `CopyOnWriteArrayList` or snapshot under lock. |
| 4 | tests F3 | nit | `test-design.md` AC-2 and AC-4 rows | Two mutation claims overstate what the tests catch (last-wins keeps insertion order; deleting the `maxOf` line is caught by the read-ahead test, not the lower-page test). | Checked `LinkedHashMap.put` semantics and the lower-page fixture. Holds. | fix | test-designer corrects the two rows and adds the row for #2. |

## Rejected codex claims

- Codex adversarial "needs-attention" on `restoreLoadedDepth` itself - the codex-reviewer traced it and found no defect; the verdict came only from the evidence gap (#1).

## Next

- Fix round: implementer (tests only: #2, #3), test-designer (#4). Tests-only change: run those tests and check.fast; re-review with lens tests on the incremental diff (codex and edge on the incremental diff per the round rule). Unproven lines: 6 (blind 2, edge 3, tests 1).
