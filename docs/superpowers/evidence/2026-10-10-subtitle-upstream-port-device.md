# subtitle-upstream-port: device pass

Date: 2026-10-10. Device: emulator-5554, AVD `puber_tv_36` (Android TV, API 36, x86_64),
software rendering (`-gpu swiftshader_indirect`). Build: this worktree's `app-dev-debug.apk`
(`com.kino.puber.stage` 1.9.69-dev), installed with `android-apk-install-preserve`
(`compatible_replace`) over a paired main-checkout build, so the login was kept.

Static gate before this: `testDevDebugUnitTest` 1373 tests / 0 failures, `:app:detektAll` clean,
`:app:lintDevDebug` without new issues.

## Checks

| Check | Expected | Observed |
|---|---|---|
| App start with this build | Home loads, logged in | Home loaded with account data; no crash buffer entries for the package |
| Series episode started without a saved subtitle choice (Outlander: Blood of My Blood S2E3) | Picker reads Off, no text on screen | Settings showed `Subtitles: Off`; no subtitles rendered |
| Subtitle picker labels | API language `ai` reads as a name, not a code | Rows: `Off`, `AI-generated` (screenshot `subtitle-picker-off-ai.png`) |
| Selecting a subtitle | Text appears | Russian subtitles rendered after choosing `AI-generated` |
| Choice carried to the next media | Saved choice restored | Subtitles were on again for S2E4 and on a later restart of S2E3 |
| Back on fresh media without a resume prompt or skip segment (movie Caja Rapida, not started) | First Back hides the controls and playback continues | Controls hid, player stayed and kept playing (`back-at-start-before.png`, `back-at-start-after.png`) |
| Movie started fresh | No subtitles shown by default | None rendered during the first minute |

## Not covered

- Off surviving a player rebuild (buffer preset or DNS toggle): the D-pad path to Advanced
  missed under software rendering and switched episode instead. Covered by unit tests only.
- An HLS manifest that marks a subtitle DEFAULT or FORCED: none of the titles used was known to
  carry one, so the regression this guards against was not reproduced on the old build.
- `частичные` / `forced` and `вариант N` labels: no title with forced or duplicate-language
  subtitles was found; covered by `SubtitleLabelsTest`.

## Notes

- The emulator crashed once with the host GPU renderer when video started (GL ColorBuffer errors in
  the emulator log, no app crash); it was relaunched with swiftshader by the other session.
- The pass moved account watch progress: Outlander S2E3/S2E4 and about a minute of Caja Rapida.
