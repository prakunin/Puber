# Review blind, round 1

LENS: blind  ROUND: 1  BASE: b27d8e40

### F1 [minor] The rail check passes whenever the rail is open, not when the opened entry is focused
- Where: BaselineProfileGenerator.kt:129-142, 195-201
- Problem: railShows(titleRes) waits for By.res("main_rail").hasDescendant(By.text(title)); with the drawer open every item shows its label, so any RAIL_TITLE_RES title passes. Commit fd08c9ee and the loop comment claim more. Scoping to main_rail does fix the old 4K-header false pass.
- Failure scenario: a lost or misplaced DPAD_DOWN (Favourites twice, 4K step on Settings); LEFT reopens the rail; check passes; fewer/different tabs profiled silently.
- Fix: By.res("main_rail").hasDescendant(By.text(title).selected(true)) or hasDescendant(By.selected(true).hasDescendant(By.text(title))); otherwise correct the commit message and comment to "the rail reopened".

### F2 [minor] The new tooling test is not executable and nothing runs it
- Where: tools/tests/test-baseline-profile-tooling.sh (mode 100644)
- Fix: chmod +x and wire it into the project's check or CI.

### Unproven
- tools/generate-baseline-profile.sh:27 without the old || true, a failing adb devices exits without the "refused" message.

Notes: regenerated profile fits current code (getItems-LiYkppA 9 params vs stale getItems-bMdYcbs); no profile/, mock or instrumentation rules leaked; all baseline rules H; counts steady (47736 lines, 9986 Puber rules).
