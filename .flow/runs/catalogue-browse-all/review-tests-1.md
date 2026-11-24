LENS: tests  ROUND: 1  BASE: e4d75215
Suite: check.full green at 6c35732d (cached tasks).

### F1 [major] AC-7 is checked only on the device, and after the fix only part of M-3 was re-run, with no evidence file
- Problem: M-3 steps 3-5 and M-2 steps 5-6 not re-run after 0a65717f/c484b503, which change exactly these paths; no screenshots or build hash for the fixed APK.
- Fix: qa-device re-runs all of M-3 and M-2 steps 5-6 on the fixed APK with head hash and screenshots.

### F2 [minor] SRFT entry tests copy the production wiring (canFocus, isOwner, initialFocusEnabled) instead of using it
- Where: SectionRowFocusTraversalTest.kt:619-630, :530-549; ContentListScreenContent.kt:366, :447, :464-465
- Fix: extract the section-0 header construction into one internal composable used by production and the test scenes, or a pure gating function with unit tests.

### F3 [minor] Nothing tests how the shelf grid title is built (ShowAllTitle.resolve)
- Where: ShowAllHeader.kt:30-33
- Fix: SACF cases asserting «Фильмы · Новинки» and «Все фильмы» from ShowAllTitle(...).resolve().

### Unproven
- ShowAllHeader.kt:31 title fallback when titleRes == null untested (hero configs only).
