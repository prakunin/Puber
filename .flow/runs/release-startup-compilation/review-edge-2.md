LENS: edge  ROUND: 2  BASE: e4c3b5aa
### F1 [minor] Fix #6 does not hold: a cleared token is still logged as present=true
- Where: app/src/nonMinifiedRelease/java/com/kino/puber/profile/AuthEventLog.kt:40 (`hasValue`)
- Problem: `CryptoPreferenceRepository.saveString(name, null)` stores `encrypt("")`. That is Base64 of a 12-byte IV plus a 16-byte GCM tag (28 bytes), a non-empty string. So `!getString(key, null).isNullOrEmpty()` is true for a cleared token, exactly as `contains()` was. The new KDoc ("present means a non-empty stored value") is true of the stored string but not of the token.
- Failure scenario: a refresh fails during generation and the app calls `clearAccessToken()`. The log still prints `access entry now present=true`, so the log cannot say why the tokens went away, which is the purpose of this file.
- Fix: decode the stored value without decrypting it. Report present only when `Base64.decode(v, DEFAULT).size > 12 + 16`, that is IV plus tag plus at least one byte of plaintext. No key or token value is touched.

### F2 [minor] Rail check passes falsely after Series: the Series screen itself shows the "4K" title
- Where: baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt:116-124 (expectedTitle / railShows)
- Problem: after index 4 (Series) the check waits for the text `main_tabs_f4k`. `TabTypeConfig.standardSections` gives Series a section titled `R.string.main_tabs_f4k`. After index 5 the expected title is the 4K tab's own title, which is the screen just opened. In both cases the text can come from the content screen rather than the opened rail. The rail item text is only composed while the drawer has focus, so the check is sound for indices 0-3.
- Failure scenario: LEFT on Series does not reach the rail, but the "4K" section header is in view. `check` passes, and the next DOWN/OK land in the content list instead of the 4K tab. The run profiles fewer tabs without failing, which is what fix #1 was meant to stop.
- Fix: look the text up inside the rail, for example by a test tag or resource-id on the drawer, or check that the item is focused or selected (`By.text(t).focused(true)` on the drawer item). Do not search the whole screen.

### F3 [minor] Makefile backup is unsafe after an earlier aborted run, and HUP is not trapped
- Where: Makefile:166-168 (`cp $$f $$f.bak; trap ... INT TERM`)
- Problem: the trap covers only INT and TERM. A dropped SSH session or closed terminal (HUP), or a SIGKILL from a tool timeout, leaves `baseline-prof.txt` stripped to S/P and leaves `baseline-prof.txt.bak` behind. `.bak` is not in `.gitignore`. The next run's `cp $$f $$f.bak` then overwrites the good backup with the stripped file. If that run also fails, the restore puts back the S/P-only file.
- Failure scenario: a run dies on HUP, then the next run fails (pairing lost, flake). The tracked profile has lost every `H` flag. With the untracked `.bak` beside it, a careless `git add -A` commits a profile that does not shorten startup on API 29.
- Fix: add HUP to the trap, and use `trap ... EXIT` in the recipe shell. At start, if `$$f.bak` exists, restore it (`mv -f $$f.bak $$f`) or refuse before copying. Optionally add `*.bak` under that directory to `.gitignore`. The TERM exit code should be 143, not 130 (nit).

### F4 [minor] Preflight refuses a just-connected emulator that is still `offline`
- Where: tools/generate-baseline-profile.sh:27-35 (`preflight_device`)
- Problem: `adb connect` returns before the auth handshake finishes, and an immediate `get-state` can be empty (`error: device offline`) or `authorizing`. The make target waits up to 60 s for the same reason (Makefile:145-150). The preflight checks only once, and its message says "Start the emulator" even when the emulator is already running.
- Failure scenario: `ssh mac-emu start`, then the script right away. It exits 2 with a misleading message, and the user has to run it again.
- Fix: poll `get-state` for a few seconds (or run `timeout 30 adb -s "$DEVICE" wait-for-device`) before refusing, and print the state that was seen.

### F5 [minor] The EXIT trap kills the shared 5038 adb server even when the script never started it
- Where: tools/generate-baseline-profile.sh:94-99 and the trap at :125 (cleanup runs before and without `preflight_device` succeeding)
- Problem: `cleanup` runs `adb kill-server` on `ITEST_ADB_PORT` (5038) on every exit, including a preflight refusal or a Ctrl-C while waiting for pairing. That port is the private server of `make itest`.
- Failure scenario: someone runs the script while a lease holder runs `make DEVICE=... itest`. The preflight refuses, cleanup kills 5038, and the other run's instrumented tests lose their device.
- Fix: track whether `make baseline-profile` was started (a flag set just before the `make` call) and run kill-server only when it is set.

### Unproven
- app/src/main/generated/baselineProfiles/baseline-prof.txt.bak: AGP or the baselineprofile plugin might pick up or clean the `.bak` in the generated source dir during generation. It becomes real if the merge task deletes non-profile files there (then the failure-path `mv` has nothing to restore) or if the embedded profile includes the `.bak`.
- app/src/main/java/com/kino/puber/core/telemetry/StartupMarks.kt:62: the startup line now comes at reveal+7 s, plus up to 5 s for the sink drain. A harness that force-stops before about 12 s after launch would lose it. It becomes real if any measurement loop waits less than that (the current evidence uses 12-14 s and saw the line every time).

Checks that hold: strip sed idempotent, leaves class rules, no-op without method rules; rewrite sed does not double H; INT/TERM path works through make and the outer bash trap; preflight now runs before ensure_paired; logcat cleanup on all three exit paths; StartupMarks 7 s bound only when awaitProfileStatus, asserted by the test; Search BACK logic presses a second BACK only when the rail did not appear.
