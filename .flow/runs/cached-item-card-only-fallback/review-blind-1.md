LENS: blind  ROUND: 1  BASE: 4db1b061

### F1 [nit] Refusals saved by the fallback are no longer logged
- Where: app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt:444-479, and refuse() removed from app/src/main/java/com/kino/puber/data/repository/PersistentPayloadStore.kt
- Problem: before this diff, RoomPersistentPayloadStore.refuse logged every payload over MAX_ROW_BYTES. Now CachedFeed logs only when nothing was persisted ("... nothing persisted"). When the card-only fallback is written, no log line says a full details record went over the row limit.
- Failure scenario: a long series' details record goes over 1 MiB on every merge. Its card-only row is written each time and nothing appears in logcat. The extra network reload of details after every restart cannot be traced to its cause from logs.
- Fix: in persistLoaded and persistRefused, also log at debug or warn level when the fallback is the row that was persisted.

### Unproven
- CachedFeed.kt:445 A refused write whose key moved through a newer write, not an invalidation, keeps the older row. That row becomes stale only if the crossing writer's own write is also refused. Real given evidence that two oversized writers race on one key, and that the second has no fallback.
- app/src/test/kotlin/com/kino/puber/domain/interactor/home/HomeInteractorTest.kt:50 With the relaxed mockk<PersistentPayloadStore>, write now returns false. Every write in that test is then treated as refused, which leads to a fallback write and a remove. The test could become misleading if it ever starts to assert on persisted rows through that mock.
