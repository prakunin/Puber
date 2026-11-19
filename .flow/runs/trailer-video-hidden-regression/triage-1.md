# Review triage - round 1

Run: trailer-video-hidden-regression   Base: 3ec85287   Reviewed head: 4c3248cc

Sources: review-blind-1.md, review-edge-1.md (no findings), review-tests-1.md, review-codex-1.md (ran: review, adversarial; verdict clean).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1, tests (note) | minor | `TrailerPreviewPlayer.kt:136-158` | The listener wiring (attach in `factory`, the post-invalidate, remove in `onRelease`) has no automated test; only `surfaceNeedsRedraw` is unit-tested | Read the test file. `grep -i robolectric gradle/libs.versions.toml app/build.gradle.kts` finds nothing, so a JVM View test would mean a new dependency, and `connected*AndroidTest` is barred by the guard hook | reject | The wiring is proven on the device instead. D1 on emulator-5554 (qa-report.md AC-1) shows a surface layer, growing frames and video for three trailers after card changes, which `diagnosis.md` shows fail without the fix. Adding Robolectric for one listener is out of proportion for an S-sized fix |
| 2 | tests F1, codex adversarial (rejected as code) | minor | spec AC-4, D3, T2 | Favourites never plays a trailer, so the favourites half of AC-4 cannot be met | `grep -c trailer .../favorites/content/FavoriteScreenContent.kt` prints 0 | fix (record) | The AC text is the analyst's and is not reworded. The conductor records a Decision in the spec and state.md: AC-4's favourites part is N/A because `FavoriteScreenContent.kt` passes no `trailerUrl`; AC-4 covers the details hero only. The owner skips favourites in T2 |
| 3 | tests F2 | minor | plan Implementation record :84-86 | The red run is recorded for one of four P0 red tests | The design marks four AC-6 rows red; all four reference the missing `surfaceNeedsRedraw` in the same file | fix | Implementer adds one line: all four P0 red tests share the same compile failure on base. Docs only, so per the test-runs table nothing re-runs and no incremental code review follows |

## Next
- Row 3: implementer in fix mode (docs only). Row 2: conductor Decision. Then the final judge.
