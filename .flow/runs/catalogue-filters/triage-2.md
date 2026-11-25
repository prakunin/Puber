# Triage round 2: catalogue-filters

Incremental 027ed8d3..426b5e99. Lenses: edge, tests, state-path (clean), codex (clean, one minor), antigravity (clean).

| # | Source | Severity | Finding | Verified | Mark | Note |
|---|--------|----------|---------|----------|------|------|
| 1 | tests F1 | major | rail fix (headerHoldsFocus) has no failing guard; Manual 2 never closed the rail | yes | fix | test-design: Manual 2 extended (Right and Back), row P0 guard with Manual 2 as carrier; qa round 2 runs it; spec Test design re-copied |
| 2 | tests F2 | minor | spec test table out of date | yes | fix | spec Test design re-copied from test-design.md |
| 3 | codex minor, edge unproven, tests unproven | minor | queryGeneration read at render time; a queued old page renders under the new key; with a conflated Loading the new list may not remount | plausible, narrow | accept | one stale frame and possibly focus left on «Фильтры»; no stale final data; listed for the owner |
| 4 | edge F1 | minor | first pages stored before the anime fix stay without anime until refresh/TTL | yes | accept | affects only users with show-anime and hide-watched on, once, until the cache TTL; listed for the owner |
