# tmdb-result-handling device attempt (round 1)

Build: worktree dev debug APK, 1.9.70-dev, sha256 4270beef...472851c, HEAD 0f153791.
Device: emulator-5554 (puber_tv_36, API 36), leased and released.
Known limitation (owner decision Q3): empty TMDB token in a worktree build.

## Result: NOT CHECKED (device blocked)

1. First `install-preserve` returned `installed` (compatible_replace, same signer 6b2918...).
2. Launch crashed with `ClassNotFoundException` for `com.kino.puber.PuberApp`,
   `androidx.core.app.CoreComponentFactory` and `coil3.SingletonImageLoader$Factory`.
3. The installed base.apk has the same size as the built APK (36185782 bytes) but a different
   sha256 (27030882... vs 4270beef...); `cmp` differs at byte 16307618. The built APK does contain
   the classes (classes17.dex, classes21.dex). The on-device copy is corrupted.
4. A second `install-preserve` returned `outcome: blocked`, `classification: signer_unknown`
   (the installed copy cannot be read as a valid signed APK).
5. Per policy no uninstall, data clear or downgrade was attempted.

| AC | Status |
|----|--------|
| AC-10 (M1 schedule screen, M2 player skip prompts) | NOT CHECKED |

The crash comes from the corrupted on-device file, not from a source difference; the corruption
is not attributed to the change under test.

# Round 2 (device smoke)

Build: same worktree dev debug APK, sha256 4270beef...472851c, installed by the conductor; the on-device
base.apk sha256 was re-read and equals the build. Device: emulator-5554 (puber_tv_36), leased and released.
No install, uninstall or data clear by QA. Limitation (owner decision Q3): empty TMDB token, live TMDB
success is not exercised on device.

## M1 episode schedule screen: PASS
Steps: launch; Home > Continue watching > Outlander (series details) > calendar button (D-pad, center).
Expected: defined missing-credentials state, no crash. Observed: screen "No schedule available / TMDB access
isn't set up. / Data and images come from TMDB; dates may change." Process alive, crash buffer empty.
Back returned to the series details screen. Observation: focus after Back is on the primary play button, not
on the calendar button that opened the screen (the spec does not require restoring it to that button).

## M2 player: PARTIAL
Steps: details > play (Up next, season 2 episode 3) > "Resume from 14:52?" > Continue; watched ~15 s.
Observed: playback started and ran, no crash, no stall, crash buffer empty; resume dialog worked.
Not observed: an IntroDB.app skip prompt. The playhead was mid-episode (about 15:00), away from the intro
and credits windows, and seeking or restarting would change the account's watch progress, so it was not
done. Skip-prompt display therefore NOT CHECKED on device; unit tests (SkipSegmentServiceTest) cover the
fallback logic. Side effect: a short resumed playback was reported to the account's watch progress.

| AC | Status |
|----|--------|
| AC-10 | PARTIAL: M1 PASS; M2 starts and plays without crash, skip prompt NOT CHECKED |
