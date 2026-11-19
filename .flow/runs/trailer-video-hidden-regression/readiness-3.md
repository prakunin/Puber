# Readiness 3: trailer-video-hidden-regression

Verdict: **READY**

Spec and plan at worktree HEAD 3e70c43f, base 3ec85287. Rounds 1 and 2 (`readiness-1.md`, `readiness-2.md`) hold for AC coverage, citations, build order and scope; this round checks the round-2 fix only.

| Check | Evidence | Status |
|---|---|---|
| Stale copy removed | `git show --stat 3e70c43f`: spec only, 90 deletions, no additions | ok |
| One `## Test design`, one `## Open questions` | spec headings at lines 116 and 204 | ok |
| Kept Test design matches `test-design.md` | diff differs only in heading levels and the title | ok |
| P0 rows marked | spec lines 140-152: every P0 row is `red` or `guard` | ok |
| D4 includes M2 | spec D4 row and the headless note after it | ok |
| Q1/Q2 table kept, both answered | spec lines 206-209 | ok |
| Plan unchanged | `git diff --stat 84eced53 3e70c43f` touches no plan file | ok |

Must fix: none.
