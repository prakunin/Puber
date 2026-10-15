# Device evidence: subtitle-restore-model (AC-11, rollout, Off)

Date: 2026-10-15. Round 1. Worktree HEAD `d538e15b` (base `f351d988`).

## Build and device

- APK: this worktree's `app/build/outputs/apk/dev/debug/app-dev-debug.apk`, sha256
  `dda60c6f6153692b66a777ae86650eaf511546536204c8974ae6c4e9e8c666f2`, versionName 1.9.70-dev.
- Install: `tools/android-apk-install-preserve install-preserve`, JSON outcome `installed`,
  classification `compatible_replace`, `destructive_action: false`. On-device `base.apk` sha256 equals the APK sha256.
- Device: `emulator-5554` (AVD `puber_tv_36`), leased and released. App was already logged in; no re-pairing.
- Input: D-pad key events only. No crash in the crash buffer for `com.kino.puber` after the run.
- Static gate (separate): `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` green; 1632 unit tests, 0 failed, 0 skipped.

## Account-state changes (recorded)

| Title | Before | During | After |
|-------|--------|--------|-------|
| Outlander (HLS) | saved subtitle Russian (old-shape lang/url prefs) | English, then Off, then Russian | Russian again, now written in the new shape |
| Psyren S1E1 | Off | Russian variant 1 | Off |

Watch progress of Outlander S1E5 (resumed at 9:33) and Psyren S1E1 moved forward by normal playback and a few seek
presses. Nothing was uninstalled, cleared or downgraded.

## Steps and results

### Rollout (spec step 5, AC-4): old preference survives the install

- Steps: after install, open Outlander S1E5 (saved Russian from the old build), open Settings, read Subtitles.
- Expected: previous pick still resolves, no crash.
- Observed: Subtitles = Russian, cues render in Russian, picker shows the check on Russian. PASS.

### M1 / step 1: pick the manifest-only row

- Steps: Outlander S1E5 on Auto (stream 1916x1076, rows Russian and English from the manifest). Pick English.
- Expected: cues render, no Off, no other language.
- Observed: English cues ("Oh, and who do we have here?"). PASS.

### M2 / step 2 (AC-11 core): leave and re-enter

- Steps: Back out of the player to the home screen, open Outlander, play S1E5 again, no subtitle pick, open Settings.
- Observed: Subtitles = English, picker shows the check on English, English cues on screen ("They hate me."). PASS.

### Step 4 (AC-11 clause b): stream that lacks the row

- Steps: Quality 480p (stream 720x404), later 720p (1280x718) and 1080p (1916x1076 manual). Read Subtitles.
- Observed: on every manual quality Subtitles = Off and audio = "Track 1" (url.hls, no manifest rows).
- 4b: Quality back to Auto. Observed: Subtitles = English, English cues, audio back to the NewStudio track. PASS.
- 4c: Back out, re-enter S1E5. Observed: Subtitles = English. PASS (Off was not saved).
- Stricter variant: select 720p (Off shown), leave the player while on 720p, re-enter. Observed: Subtitles = English
  on Auto. PASS.

### Step 3 (AC-11 clause a): a quality whose stream still offers the row

- Outlander: every manual quality (1080p, 720p, 480p) plays url.hls and lacks the manifest row, so no manual quality
  offers a manifest-only row. Clause (a) for a manifest-only row has no stream to test on this title. NOT CHECKED.
- Closest observation, Psyren S1E1 on Auto (rows: Spanish/Russian/Portuguese/Polish variants, two forced Spanish rows,
  and more): picked "Russian variant 1" (cues render), then Quality 720p (1280x720).
  Observed: after the switch Subtitles = Russian, the picker lists one row per language (Chinese, Russian, Portuguese,
  Polish, Italian, French, Spanish, German, English) with the check on Russian, Russian cues render
  ("Чего только не придумают."). This is a language-level pass for rows that are present on both streams. The UI cannot
  tell whether the pick was the manifest or the API variant, so it does not stand in for a manifest-only row.

### M6 / step 6: explicit Off

- Steps: Outlander, pick Off, leave, re-enter, open Settings.
- Observed: Subtitles = Off, no cues. PASS.
- Then Russian was picked again (state restored, see table). Psyren was set back to Off through the picker.

## Not exercised on device

- Forced and language-less manifest rows: no such title known (unit tests only, accepted by the owner).
- No log filtering was needed: the spec's "subtitle-restore log line" does not exist; step 4c re-entry is the evidence.
