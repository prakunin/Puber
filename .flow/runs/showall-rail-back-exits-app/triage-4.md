# Triage round 4: showall-rail-back-exits-app

Reviewed incrementally: 3a536877..73371c73 (C2 4d64a1b7, C3 6c44c4e9). Lenses: edge (no findings), tests, codex (ran), antigravity (ran, clean), state-path. qa round 7 at 73371c73 (evidence 8721db44): MainRailBackTest 14/14 and guards green; C3 and C2/B3 mutations killed; rail picks and M1 PASS on the box; quick double pick NOT CHECKED on device (tabs cached).

| ID | Source | Severity | Finding | Decision | Reason |
|----|--------|----------|---------|----------|--------|
| D1 | tests F2 | major | C2/C3 test changes never ran | resolved | qa round 7: green at HEAD, C3 mutation killed at MainRailBackTest.kt:223, C2/B3 mutation killed at :203 |
| D2 | tests F1, codex minor 2 | major → minor | The !tab.isSelected clause in acceptsPickOf is untested (the probe never updates the selection) | accept | Regraded: the worst outcome without the clause is a duplicate ItemSelected and a repeated catch-up refresh, which base already did on every click. Listed for the owner |
| D3 | tests F3 | minor | The corrective test does not assert Down reached Series | accept | The C3 mutation run shows the test fails when the pick is refused; a clearer failure message only |
| D4 | tests F4 | nit | Evidence caveat and test count stale | accept | qa round 7 appended the current count; earlier round text is historical |
| D5 | codex minor 1 | — | Corrective pick keeps the first request id; History's timeout could reopen the rail over Series | reject | Refuted by antigravity and edge: the outgoing tab's ContentFocusHandoffEffect is cancelled when key(currentTab.key) disposes it, and the new tab starts a fresh 5 s window and settles the pending id |
| D6 | state-path F1 | minor | The cold-start (initial) handoff refuses a rail pick while the first tab loads; the rail reopens after 5 s | defer | Narrow, recoverable; a handoff-kind change in DrawerState is beyond this fix loop. New run rail-pick-during-initial-handoff |
| D7 | edge unproven | — | Double press within one frame; timeout racing a correction | reject | Unproven per the reviewer's own conditions |

Nothing left to fix. Stage -> judge.
