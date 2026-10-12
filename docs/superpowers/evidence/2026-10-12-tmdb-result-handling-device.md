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
