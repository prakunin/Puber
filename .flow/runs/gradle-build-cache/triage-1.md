# Review triage - round 1

Run: gradle-build-cache   Base: 8c3117ea   Reviewed head: b622364e

Sources: review-blind-1.md, review-tests-1.md, review-codex-1.md (ran: review), review-antigravity-1.md (skipped: quota reached, owner: do not wait).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1, codex | major | `app/build.gradle.kts:468-480`, `SubtitleLabelerTest.kt:82-83` | Test inputs miss `src/main/res/values/player.xml` and `values-en/strings.xml`, which SubtitleLabelerTest reads | Read the test: `variantFormat("src/main/res/values/player.xml")` and `values-en/strings.xml` are file reads; neither is in `projectFilesReadByTests` | fix | Declare the whole `src/main` directory (covers res, java, generated baselineProfiles) plus `proguard-rules.pro` |
| 2 | blind F2, tests F5 | minor | `app/build.gradle.kts:471-480` | Hand-kept input list drifts | Follows from #1 | fix (cheap) | Same change as #1: one directory input instead of a file list |
| 3 | tests F1 | major | brief.md Implementation record | P0 red `AC4-edit-strings` and `AC1-clean-rebuild` have no recorded red run | Record shows red only for proguard | fix | Run and record both on base + caching without inputs (strings) and without the property (AC-1) |
| 4 | tests F2 | minor | evidence line 9 | AC-3 warm run never loaded from cache | Evidence says UP-TO-DATE, not FROM-CACHE | fix (cheap) | Run check.full after `clean` and record FROM-CACHE count and exit 0 |
| 5 | tests F3 | minor | evidence lines 11-13 | AC4-edit-others has no red run | Evidence shows post-change runs only | fix (cheap) | Record one red run per directory input when re-running #3 |
| 6 | tests F4 | minor | qa-report AC-2 | AC-2/AC-5 on a git-archive copy, not an Orca worktree | qa states the caveat | reject | The copy differs from a worktree by path, which is the input AC-2 tests; `local.properties` sdk.dir is not a task input of compile/ksp. Listed for the owner at handover |

## Rejected codex claims

- none

## Next

- Fix round: implementer fixes #1-#5; then check.fast, review edge + codex (+ tests, since evidence/tests change) on the incremental diff, qa on AC-3/AC-4; unproven lines: 3
