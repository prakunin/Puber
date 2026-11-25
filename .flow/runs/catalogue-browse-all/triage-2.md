# Triage round 2: catalogue-browse-all

Incremental 6c35732d..0c1f8378. Lenses: edge (clean), tests, state-path (clean), codex (review + adversarial), antigravity (clean).

| # | Source | Severity | Finding | Verified | Mark | Note |
|---|--------|----------|---------|----------|------|------|
| 1 | codex | major | All sections Empty: entry visible but never focusable | yes: canFocus = launchSettled || isOwner (BrowseEntryFocus.kt:42-43); Empty renders nothing focusable (SectionRow.kt:88), so firstRowLanded never sets | fix | settle the launch when no section is Loading and none has focusable content; instrumented all-empty test with a red run |
| 2 | tests F1 | major | No evidence at head; saved SRFT XML is red at c0664ac0 | yes | fix | qa round 3 (running) records head runs; after fix #1 a short qa round re-runs SRFT and M-1; relabel the c0664ac0 XML |
| 3 | tests F2 | minor | ownership-clearing call exercised through the scene's copy | yes | accept | round 2 minor; M-3 step 5 on device is its guard; listed for the owner |
| 4 | edge/tests unproven | - | 100 ms settle window, shimmer->card focus without hasFocus toggle, late launch request after the rail | traced by state-path, no reachable path | reject | M-1 on an uncached tab in qa covers the shimmer case |
