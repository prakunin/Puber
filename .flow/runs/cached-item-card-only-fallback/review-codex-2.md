CODEX: ran: review, adversarial
CONFIRMED:
- [major] app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt:447-456 and :485-493 - The new `Timber.w("Refused cached payload %s: ... fallback persisted ...")` in `persistLoaded` and `persistRefused` runs on the normal card-only success path. It contradicts the approved spec: the table row at line 84 says the refusal log is emitted only when nothing was persisted for an owned key. AC-7 and manual check 1 require zero logcat lines matching `Refused cached payload content:v2:item:<id>`. Scenario: on the box, open an over-1 MiB series, the fallback persists correctly, and the log line is still emitted. The AC-7 log check then fails even though Room holds the right card-only row. The same prefix also stops separating a successful recovery from a failed persist. Fix: drop the log, or use a different prefix at debug level in both paths, and update plan T3.
REJECTED:
- Plain review: no findings. The per-key ownership re-check before `store.remove` and the added test hold up, so I did not dispute them.
VERDICT: changes needed
