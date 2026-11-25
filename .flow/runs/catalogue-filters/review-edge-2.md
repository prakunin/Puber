LENS: edge  ROUND: 2  BASE: 027ed8d3
### F1 [minor] Stored first pages from before the anime fix still show no anime until the next refresh (ContentListInteractor.kt:80 cacheKey, :190 isVisible). Fix: version token in cacheKey or a one-time invalidation.
### Unproven
- ShowAllVM.kt:228 queryGeneration read at render time (same as codex minor).
Checked: publishLock cannot deadlock (trySend, non-suspend publish); anime walk matches the fast path; headerHoldsFocus clears; key(queryGeneration) changes only via changeQuery.
