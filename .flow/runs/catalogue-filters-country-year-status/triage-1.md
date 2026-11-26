# Review triage - round 1

Run: catalogue-filters-country-year-status   Base: 90e69aff   Reviewed head: 1f6b03b0

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md (ran: review, adversarial), extras: review-ux-1.md, review-antigravity-1.md (clean), review-state-path-1.md (no findings).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1 | major | qa-report.md; spec M1-M6 | AC-11 and the device halves of AC-3/5/7 (M1-M6) not run: box account returns 403 user_banned | qa-report and device evidence read; environmental, no code defect | fix | qa-device re-run of M1-M6 + AC-11 screenshots once the owner restores the box session; carried as NOT CHECKED until then |
| 2 | codex | minor | `ContentListInteractor.kt:120-138` | cacheKey always appends country/year/finished, so every pre-upgrade cached first page misses once (offline first launch shows nothing) | read the key builder: three fields appended unconditionally | fix | keep the old key when all three are null; append only when set |
| 3 | ux F1 (blind unproven 4, edge note) | minor | `ShowAllFilterPanel.kt:243-262` | a stored year no longer in the presets: door shows it, page has no checked row and focuses «Любой год» | read YearPage: no row for `chosen !in yearOptions` | fix | add one selected row `yearLabel(chosen)` after «Любой год» with first focus |
| 4 | tests F2 | minor | `ShowAllVM.kt` StatusChosen branch | «Любой статус» on a series grid untested | grep of ShowAllVMTest: StatusChosen(false) only on allMovie | fix | add the VM test on allSerial |
| 5 | tests F3 | nit | `ShowAllFilterFocusTest.kt` countryPage_downReachesTheLastCountry | last assertion cannot fail on its own | read | fix | assert focus on the last country's row by text |

## Rejected codex claims

- Raw 403 body quoted in the evidence - only `{"status":403,"error":"user_banned"}`, no token or account data (codex-reviewer rejected it too).
- "Run the live checks" - not a code defect; tracked as row 1.

## Next

- Fix round: rows 2-5 by implementer (fix mode), then check.fast + touched tests, incremental review (edge, codex, antigravity, tests); row 1 waits on the owner's box session. Unproven lines: 8 (blind 4, edge 4).
