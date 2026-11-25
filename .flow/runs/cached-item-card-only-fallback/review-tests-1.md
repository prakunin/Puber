LENS: tests  ROUND: 1  BASE: 4db1b061

Suite green: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug (AGENT_GRADLE_SERIAL=1), all UP-TO-DATE since 6111240e (only evidence changed); 2574 tests, 0 failures, 0 errors, 0 skipped; no @Disabled left in data/.

Every P0 row has a test under its name. The plan's Implementation record shows a red run on base for each red and red (compile) row. Guard rows match the test design. The two renamed PersistentPayloadStoreTest tests only flip the delete assertion that AC-1 reverses; nothing else weakened.

### F1 [minor] No test checks that putAll only writes the fallback for keys it still owns
- Where: app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt (persistRefused, the line val owned = refused.filter { epochOf(it) == epochs.getValue(it) })
- Problem: Only the load path is tested for "write the fallback only while the key is still owned" (aRefusedLoadCrossedByABulkWriteWritesNoFallbackOverTheNewerRow). The putAll crossing test (aRefusedBulkWriteCrossedByANewerWriteKeepsTheNewerRow) uses no fallback; its second filter still blocks the delete, so it passes even if the owned filter is removed. With a fallback set, removing the filter makes the fallback overwrite the newer writer's row, and no test fails.
- Failure scenario: two home sections both run mergeItems for the same oversized item; the second putAll lands while the first is suspended in store.writeAll. If someone drops the owned filter, the first section's card-only fallback overwrites the newer card row; suite stays green.
- Fix: a CachedFeedTest row: feed(persistFallback = { "small" }), store.maxPayloadLength = REFUSAL_LIMIT, store.onWriteAll = { subject.putAll(mapOf("k" to "newer")) }, then subject.putAll(mapOf("k" to "v".repeat(REFUSAL_LIMIT))), assert the row is "\"newer\"".

### F2 [minor] Manual check 1 (AC-7) is a P0 red row but was never run red, and its logcat half cannot fail
- Where: docs/superpowers/evidence/2026-11-24-cached-item-card-only-fallback-device.md (AC-7 table) vs test-design row "AC-7 | manual | P0 | red"
- Problem: device check only at HEAD 6111240e; no run on 4db1b061. "0 Refused cached payload lines" has no positive control; the fixed code logs nothing on the success path. Only the row-size result proves anything. The unit red rows (AC-2, AC-3, AC-4) cover the defect, so this is an evidence gap only.
- Failure scenario: a build without a Timber tree, or a different tag, would still show 0 lines and PASS.
- Fix: mark the logcat half "not falsifying, no positive control" and lower the manual row to guard, or show the line once on a base build.

### Unproven
- CachedFeed.kt persistLoaded, the branch where the fallback is refused too: only the put path is tested (aPutWhoseFallbackIsAlsoRefusedLeavesNoRow). Matters only if a load can produce an item whose card-only copy is still over 1 MiB.
