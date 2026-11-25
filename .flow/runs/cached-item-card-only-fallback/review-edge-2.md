LENS: edge  ROUND: 2  BASE: 4db1b061

No findings.

### Unproven
- app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt:484-491 After the bulk fallback `writeAll`, the "fallback persisted" line is logged for every key in `owned - unsaved`, and the epoch is not checked again first. If a newer writer takes a key over while that `writeAll` is suspended, the log still says the fallback was persisted, though the newer row may be what is now stored. Only the log is wrong, not the stored data. This becomes a finding only if a device trace for AC-7 relies on that line being the final state of the row.
