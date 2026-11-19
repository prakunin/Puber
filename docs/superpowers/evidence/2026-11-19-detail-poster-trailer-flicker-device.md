# Device evidence: detail-poster-trailer-flicker (round 1)

Build: worktree `app-dev-debug.apk` (sha256 908b74f8...36eaf4, 1.12.0-dev), installed with
install-preserve (outcome `installed`, `compatible_replace`); on-device base.apk sha256 matches.
Device: emulator-5554 (AVD puber_tv_36, headless, no human watching), leased and released.
Method: D-pad keyevents; bursts of `screencap` after each press, the media area (x 960-1920,
y 0-700) scanned for pixels within 4 of #625B71 (skeleton grey).

| Check | Steps | Expected | Observed | Result |
|---|---|---|---|---|
| AC-4 / M1 warm+cold, catalogue (Movies) | 5 + 8 DPAD_RIGHT, 8 DPAD_LEFT, 1 DPAD_DOWN, 49 captures (about 200-400 ms after each press) | no skeleton-grey frame | grey fraction 0.000-0.001 in all frames; no empty panel frame; posters change card to card | PASS on screencap level (crossfade smoothness not observable) |
| AC-4 / AC-8 favourites panel | 6 DPAD_RIGHT, 12 captures | same | grey 0.000 in all | PASS on screencap level |
| AC-5 first focus | not isolated: first-focus capture happened after the poster loaded | skeleton until first image | not observed | NOT CHECKED |
| AC-8 details hero + BACK | opened a details screen by D-pad, BACK | hero shows, BACK returns to catalogue | hero poster rendered, BACK returned to the grid | partial (no trailer part) |
| AC-1, AC-2, AC-3, AC-7, trailer part of AC-8 | rested on cards 8-20 s with auto-trailer default on | poster over buffering trailer, 300 ms fade, no letterbox poster | ExoPlayer instances were created (`ExoPlayerImpl: Init`) 2 s after each focus, but no SurfaceView layer appeared in `dumpsys SurfaceFlinger --list`, `screencap` kept the full-opacity poster for the whole dwell, and `screenrecord` frames stayed static. The first video frame was never confirmed, so the fade and letterbox states were never reached | NOT CHECKED |
| `screenrecord` and the video layer | `screenrecord` x2 (12 s, 20 s) with a trailer dwell | would show video if captured | static poster frames only; inconclusive whether this is the capture or the headless emulator not rendering video | inconclusive |
| M6 broken poster URL | no reproducible broken URL | n/a | not attempted | NOT CHECKED |

No crash in logcat (only boot-time AndroidRuntime lines).

# Round 2 (HEAD 9ede97aa)

Build: worktree `app-dev-debug.apk` sha256 a12341d2...cc730, installed with install-preserve
(outcome `installed`, `compatible_replace`); on-device base.apk sha256 matches. Device:
emulator-5554, headless, leased and released. Same scan as round 1 (media area x 960-1920,
y 0-700, pixels within 4 of #625B71, plus a near-black check for an empty panel).

| Check | Steps | Expected | Observed | Result |
|---|---|---|---|---|
| AC-4 catalogue (Movies, New releases) | 8 DPAD_RIGHT, 8 DPAD_LEFT, DOWN, RIGHT, UP; 3 captures after each press, 57 frames | no skeleton-grey frame, no empty panel | max grey fraction 0.0013; no frame over 1%; no near-black frame | PASS on screencap level (crossfade smoothness and the 3 s hold bound not observable) |
| AC-4 / AC-8 favourites panel | 6 DPAD_RIGHT, 6 DPAD_LEFT; 3 captures after each press, 36 frames | same | max grey 0.0002; no empty frame | PASS on screencap level |
| AC-5, AC-6, trailer ACs | as round 1 | n/a | not reachable headless / no broken URL | NOT CHECKED |

No crash in `logcat -b crash`.
