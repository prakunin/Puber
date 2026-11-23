LENS: blind  ROUND: 1  BASE: e4c3b5aa
### F1 [major] The new playback journey writes a watch position to the paired KinoPub account on every iteration
- Where: baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt:865-919
- Problem: `playbackProfile` opens the first Home item, presses OK on Play, waits 5 s, then presses BACK. BACK leads to `PlayerVM.exitPlayer()` -> `saveCurrentPosition()` (PlayerVM.kt:1898-1928), which calls `interactor.saveWatchingTime` (`watching/marktime`). The script now requires `PUBER_CLIENT_SECRET`, so the build is fully logged in against the real account. Nothing in the generator, script or docs says that a run changes the account's history.
- Failure scenario: `make baseline-profile` runs this journey once per `BaselineProfileRule` iteration. Each time, the first Home title gets a position of about 5 s (or its saved resume point plus 5 s) and moves into the account's continue-watching and history. The title depends on what Home shows that day, and an earlier real position on it can be overwritten.
- Fix: Keep the profile from reaching the server. One way is to leave the player another way, without the exit save. A cleaner way is a nonMinifiedRelease-only flag the generator sets (for example through the existing provider) that turns off progress writes. At minimum, document the side effect in docs/release.md next to the command.

### F2 [minor] `prof` misses the race against the 5 s bound on fast starts, and every ready line now waits about 5-6 s
- Where: app/src/main/java/com/kino/puber/core/telemetry/StartupMarks.kt:103-107, TelemetryRuntime.kt:57
- Problem: `ProfileVerifier.getCompilationStatusAsync()` only resolves when `ProfileInstaller.writeProfile` runs. In profileinstaller 1.4.1, `ProfileInstallerInitializer.installAfterDelay` posts that 5000 ms + random(0..1000) ms after the first Choreographer frame. The bound fires at reveal + 5000 ms.
- Failure scenario: Reveal lands within the random 0-1 s of the first frame (a fast start with images cached). The bound wins, and the line is written with `prof` absent on exactly the fast starts. On every other start the ready line, which used to be written at ready, now waits until about 5-6 s after the first frame.
- Fix: When `awaitProfileStatus` is set, give the bound a longer wait (for example reveal + 7 s), or wait for the status up to a cap measured from the first frame. If the delayed line is intended, say so in the class KDoc.

### F3 [minor] AuthEventLog reports a cleared token as "present=true"
- Where: app/src/nonMinifiedRelease/java/com/kino/puber/profile/AuthEventLog.kt:320-325
- Problem: `CryptoPreferenceRepository.clearAccessToken()` and `clearRefreshToken()` call `saveString(name, null)`. That writes `encrypt("")`, a non-empty sealed string, so `prefs.contains(key)` stays true after a clear. Only the "sealed shut" path actually removes the key.
- Failure scenario: A logout, or a failed refresh that clears the tokens during a run, logs `access entry now present=true`. The next process logs `access=true refresh=true`, even though the provider returns no tokens. This log was added to explain why tokens disappear, and it says the opposite.
- Fix: Log whether the stored value is non-empty, or decrypts to a non-empty value, never the value itself. For example, check `!shared.getString(key, null).isNullOrEmpty()` and add a note that a cleared entry is a sealed empty string.

### F4 [minor] `is_paired` treats "device not reachable on the default adb server" as "not paired"
- Where: tools/generate-baseline-profile.sh:1179-1183, 1187-1203, 1265-1269
- Problem: The script never runs `adb connect "$DEVICE"` on the default server. Only the make target connects, and only on its private server. `is_paired` discards every adb error with `2>/dev/null || true`.
- Failure scenario: Following docs/release.md literally (`DEVICE=host.docker.internal:5555 ./tools/generate-baseline-profile.sh`) without a prior `adb connect`, the script prints "not paired", assembles the app and calls install-preserve on a device it cannot reach, which fails with an unrelated error. In the same state after the run, it prints a false "not paired after the run" warning.
- Fix: For a host:port serial, run `adb connect "$DEVICE"` at the top of the script, then check `adb -s "$DEVICE" get-state` and exit with a clear message before calling `is_paired`.

### Unproven
- app/src/devNonMinifiedRelease/AndroidManifest.xml:5-8 the exported `BaselineProfileAuthProvider` returns raw tokens to any caller. This was already there before the diff; it matters only if a nonMinifiedRelease APK reaches a device that has other apps installed.
- baselineprofile/.../BaselineProfileGenerator.kt:881 the player check only waits for the `player` tag, so a stream that fails to play still passes and the profile misses the playback code. It matters only if playback actually fails on the generation emulator.
