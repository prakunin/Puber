# Review triage - round 1

Run: subtitle-und-preferred-fallback   Base: 874b049f   Reviewed head: c417e5a5

Sources: review-blind-1.md (F1, F2 minor), review-tests-1.md (F1, F2 minor), review-codex-1.md (ran: review; 1 minor). Antigravity: skipped, quota exhausted (HTTP 429, resets in about 136h; owner: do not wait).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1, codex | minor | `app/src/main/java/com/kino/puber/ui/feature/player/model/SubtitleLanguage.kt:19-24`; comment at `PlaybackController.kt:907` | KDoc and comment claim an empty preferred language makes an undetermined row show nothing; the code only passes "" and does not disable text selection, so Media3 default-flag or forced selection may still pick a track | read the helper and the call; research.md: score 0 for every track with "", but default/forced flags still apply | fix | soften the wording to "prefers no language; Media3 default or forced selection may still apply" (comment only) |
| 2 | blind F2, tests F1 | minor | `SubtitleTrackIdentityTest.kt:92-105` | tests pin the helper, not the controller wiring | test-design.md R3/AC-3 records the call site as UNTESTABLE at JVM level, checked by code read; Q2 (owner, recommended) declined an instrumented case | reject | owner decision Q2 and test-design R3; codex and the tests lens confirm the call site uses the helper |
| 3 | tests F2 | minor | device evidence | AC-4 device smoke never reaches the "und" branch | qa-report.md already reports AC-4 as "PASS, neighbouring path only" | reject | already stated in the qa report and carried to the gate as is; no title with an "und" API row is known |

## Rejected codex claims

- Empty language should disable text selection - behaviour unchanged from base; not in scope (codex agrees).

## Next

- fix round 1: #1 (comment only, no test run needed by change class); size S, no blocker or major left, so no second review round; unproven lines: 1 (tests 1)
