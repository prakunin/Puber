# Review edge, round 1

LENS: edge  ROUND: 1  BASE: b27d8e40

### F1 [minor] The script still kills a refused port, which gets around the make target's port guard
- Where: tools/generate-baseline-profile.sh:65-67, :82-84
- Problem: generation_started=1 is set before make; if make refuses BASELINE_ADB_PORT == ITEST_ADB_PORT or 5037, cleanup still kills that port's server. Shell tests drive the guard only via run_make.
- Failure scenario: BASELINE_ADB_PORT=5038 ... during a box make itest -> the 5038 server is killed; 5037 kills the default server; ITEST_ADB_PORT=5039 exported does the same.
- Fix: same check in the script before generation_started=1 (exit 2), or set the flag after make passes the guard; add a script-level test.

### F2 [minor] The rail check no longer catches a journey that lost its place, but the comment and commit say it does
- Where: BaselineProfileGenerator.kt:126-127, :195-201
- Problem: By.res("main_rail").hasDescendant(By.text(title)) passes for any title while the rail is open.
- Failure scenario: OK on entry N not taken (acceptsPickOf false during a handoff, MainScreenComponent.kt:313-319); LEFT reopens on N-1; check passes; a tab is repeated and the last never opened; M2 shows the rail journey is flaky on this emulator.
- Fix: By.res("main_rail").hasDescendant(By.focused(true).hasDescendant(By.text(title))), else reword the comment.

### F3 [minor] A stale leftover .bak overwrites a newer profile in the working tree
- Where: Makefile baseline-profile recipe
- Problem: the git-ignored .bak survives checkout/pull; restore does not check it still matches; the notice does not mention discarding a newer profile.
- Failure scenario: SIGKILL leaves a .bak; owner checks out and pulls a regenerated profile (e.g. from baseline-mock-protocol-parity); next run moves the old .bak over HEAD's profile.
- Fix: restore only when the profile is the stripped copy (grep -q '^[SP]*L.*->' $$f); otherwise delete the .bak with an "out of date" notice.

### Unproven
- generate-baseline-profile.sh:24 failing adb devices exits without a refusal message (old || true removed).
- :34 an unauthorized emulator polls 30 s then suggests ssh mac-emu start.
- Makefile cp $$f $$b unchecked (disk full).
- A reused Gradle daemon's ddmlib bridge started on 5038 could keep using it.

Profiles sampled: no non-H method rules in baseline-prof.txt; no .bak in the generated directory.
