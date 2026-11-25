# Triage round 1: cached-item-card-only-fallback

Reviewed: 4db1b061..b25678c6. Lenses: blind, edge, tests, codex (ran: review + adversarial), antigravity (ran, clean), state-path (no findings). Each finding checked against the code by the conductor.

| ID | Source | Severity | Finding | Decision | Reason |
|----|--------|----------|---------|----------|--------|
| T1 | codex minor 1 | minor | `persistRefused` filters owned keys once, before the first suspending `store.remove`, so a key taken over during an earlier delete in the same loop is still deleted (CachedFeed.kt:475-477) | fix | Confirmed: the snapshot is taken before the loop. Cheap: re-check `epochOf(key) == epochs[key]` immediately before each remove, with a test |
| T2 | tests F1 | minor | No test pins that `putAll` writes the fallback only for keys it still owns | fix | Confirmed: the crossing putAll test sets no fallback. Cheap test row as proposed |
| T3 | blind F1 | nit | A refusal saved by the fallback is no longer logged, so a details refetch after restart cannot be traced | fix | Confirmed; cheap. Log (warn) when the fallback is what got persisted, without the payload. It also gives AC-7 a positive control on the box |
| T4 | tests F2 | minor | The AC-7 logcat half has no positive control and the manual row was never run red | fix (evidence) | qa round 2: with T3 the log line is expected for an oversized title; record it, and mark the manual row as guard on base (unit red rows AC-2..AC-4 carry the red) |
| T5 | codex minor 2, edge unproven 1 | minor | Fallback upsert after the ownership check is not atomic; a newer row could be overwritten by an older card-only copy | reject | Pre-existing window: base has the same check-then-write gap for the main write (CachedFeed load); outcome is a stale card-only row that the next merge or refetch repairs. A conditional store write is out of scope |
| T6 | codex rejected 1 | — | Eviction between two reads in observeItemDetails | reject | Unproven (Codex itself) |
| T7 | edge unproven 2 | — | cardOnly drops a movie's own watch fields in videos[0] | reject | Unproven: only movie records over 1 MiB; none seen (box oversized rows were series) |
| T8 | blind unproven 2 | — | Relaxed mockk store returns false from write in HomeInteractorTest | reject | Known and harmless (design risk 5); the mock stores nothing |

Fix round: T1-T3 to the implementer, T4 to qa-device round 2. Then incremental review (edge, tests, codex, antigravity, state-path) and qa on AC-5/AC-7.
