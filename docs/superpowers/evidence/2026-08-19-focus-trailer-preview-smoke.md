# Task 6 smoke run — executed by the controller

Device: 192.168.1.121:5555 (VS KM6, Android 10), explicitly authorised by the user with this serial.
Build: dev/debug, 1.7.42-dev, installed with `make run DEVICE=192.168.1.121:5555` (install -r, app data kept).
Screen under test: Movies (`ContentListScreen`), side rail active.

## Method

`adb screencap` returns a near-blank 8 KB image whenever a video SurfaceView is on screen, so
screenshots cannot distinguish "still" from "playing" — an early frame-hash comparison was
invalid for exactly this reason and was discarded. The reliable signal used instead:

    adb -s <serial> shell dumpsys audio | grep "ID:.*<app pid>"

which lists the app's `AudioTrack` with `usage=USAGE_MEDIA content=CONTENT_TYPE_MOVIE` — the exact
attributes `TrailerPreviewPlayer` sets — and its `state:started`. The audio-focus log in the same
dump shows matching `requestAudioFocus`/`abandonAudioFocus` pairs from
`androidx.media3.common.audio.AudioFocusManager`, confirming `handleAudioFocus = true` works.

## Results

| # | Check | Verdict | Evidence |
|---|---|---|---|
| 1 | Fresh install opens on the side rail | NOT VERIFIABLE HERE | The device already stores an explicit `navigation_mode=SideDrawer`, so it exercises the "explicit choice honoured" path, not the never-configured default. The side rail did render. Verifying the default needs a data wipe, which would sign the user out. Unit-covered instead. |
| 2 | Rest on a card ~2s → trailer plays with sound | PASS | Polled from the moment focus landed: no player entry at +0.1/+0.7/+1.3/+1.9s, `state:started` from +2.5s onward. Audio focus requested. |
| 3 | Fast scrolling starts nothing | PASS | Eight rapid LEFT presses: silent throughout; playback began only after settling ~3s on the landing card. |
| 4 | Trailer ends → still returns, no replay | PASS | Playback ran 109s, then stopped by itself; silent for 12s more with focus unmoved. Screenshot `after-trailer-end.png` shows the still restored for the focused card. |
| 5 | Focus to another card stops it | PASS | Playing → LEFT to the neighbouring card → silent immediately, new card's trailer started ~2s later. |
| 6 | Open details and return → nothing playing | PASS | Playing → OK → silent within 1s in details; after Back, silent for 4.8s. |
| 7 | Switch off → the still never gives way | PASS | Toggling "Трейлер при наведении" wrote `auto_trailer_enabled=false`; six cards held 3.5s each, all silent. Restored to true afterwards. |

## Finding confirmed on the device

**I3 from the final branch review is real, and observed.** With a trailer playing, pressing LEFT
from the first card of a row moves focus into the side rail and the trailer **keeps playing** —
`state:started` continuously across 3.6s of polling, with the frame capture still showing a video
surface. Nothing clears `previewTrailerUrl` when focus leaves the rows for a non-card.

## Not verifiable with this instrumentation

**I1 (black rectangle during buffering / on a failing URL) could not be judged.** `screencap`
blanks the video layer, so what the panel shows between the player attaching and its first frame
cannot be captured. Only a person watching the TV can confirm or refute it.

---

# Post-fix-wave re-smoke (commits 3465216, 0e9b29c, 265c538)

Same device and method. Build reinstalled with `make run DEVICE=192.168.1.121:5555`.

| Check | Verdict | Evidence |
|---|---|---|
| Card-to-card still starts a fresh trailer — the implementer's own stated residual risk that the new LazyColumn `onFocusChanged` might cancel the incoming gate | **PASS** | Arrowing along a row, cards at +3, +4 and +5 each started playback within 3.5s, including moves from an already-playing card. The risk is not realised. |
| Open details from a playing card, then Back | **PASS** | Playing → silent within 1s in details → after Back, silent for ~2.8s, then a fresh start at ~+3.5s. No instant replay; the full pause is served. This is a deliberate behaviour change from the pre-fix run (which stayed silent), covered by the new `refocusingTheSameCardAfterAStop_startsAFreshCountdown` test. |
| I3's fix — focus leaving the rows into the side rail | **NOT REPRODUCIBLE TODAY** | Reproducing it needs a trailer-bearing card at a **row start**, because from any other position LEFT moves to the previous card instead of the rail. Roughly a dozen row starts were sampled across the Movies and Series tabs; none had a trailer. The pre-fix confirmation stands (it was obtained when a row's first card did have one), but the fix itself is **unverified on hardware**. |
| I1 — the still holding until the first frame | **NOT VERIFIABLE** | Unchanged reason: `screencap` blanks the video layer, so what composites over the hole-punched surface cannot be captured. Needs a person watching the TV. |

## What still needs a human on the TV

1. **I1**: focus a card with a trailer and watch the panel for the two seconds before video appears — the still must hold, then give way. A black rectangle at any point means the fix did not take.
2. **I3**: focus the **first** card of a row that has a trailer, wait for it to play, then press LEFT into the side rail — the sound must stop.
