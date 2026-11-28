# Device evidence: trailer-preview-load-control

Date: 2026-11-28
Device: TV box 192.168.1.110:5555 (lease held and released), package com.kino.puber.stage only
Build: worktree dev debug APK 1.12.8-dev, sha256 491dcfff8af6153d121e670c4c2aefa1ef4f4fc98d8484ba33fde1b6b23fffe0
Install: install-preserve outcome "installed" (compatible_replace, destructive_action false); on-device base.apk sha256 equals the built APK.
Session: stage app launched once before install and auth-save taken; after install the app was logged in (home feed loaded).
Surface: Movies section (catalogue panel with trailer preview), D-pad only, human pace.

## AC-4 trailer start, fade, end
Steps: sidebar to Movies, focus rests on the first card (Shaun the Sheep), screenshots every 3 s.
Expected: trailer starts, still fades out with no black frame, plays to the end, still returns.
Observed: still at t=0; trailer frames at +3 s, +9 s and +15 s (different scenes, no black rectangle, no spinner or stall seen). Screenshots every 6 s afterwards: still image restored and unchanged for the last 7 samples, so the trailer ended and returned to the still. PASS.
Limit: sampling by screenshot at 3-6 s, so a sub-second stall cannot be excluded; no stall was visible at that pace.

## AC-5 stop on card change and on Home
Steps: focus right to card 2 and wait for its preview; focus right to card 3; later HOME, then relaunch via the launcher intent.
Expected: one active audio track at a time, none after Home, next preview starts, no resume on return.
Observed: dumpsys audio showed exactly one started AudioTrack from the stage process during the first preview (ID 5535) and one for the next card (new ID 5543, the first was gone); card 3 shows its trailer frames about 9 s after focus; after HOME no started track; after returning the card still shows and no trailer resumed. PASS.

## Round 2: AC-4 end-of-trailer vs player error (triage-1 row 3)

Device: TV box 192.168.1.110:5555 (lease held and released), package com.kino.puber.stage only.
Build: same worktree dev debug APK, sha256 491dcfff8af6153d121e670c4c2aefa1ef4f4fc98d8484ba33fde1b6b23fffe0. At the start of round 2 the on-device base.apk had sha256 a05e05b5... (not this build; the box is shared and another run had installed its APK), so the APK was reinstalled with install-preserve (outcome "installed", compatible_replace, destructive_action false) and the on-device base.apk sha256 then equalled the built APK. Before that the stage app was launched once and auth-save taken; after the install the app was logged in.
Surface: Movies section, first card (Shaun the Sheep: The Beast of Mossy Bottom), D-pad only, human pace.

Steps: focus on the first card and leave it untouched; sample the stage process's audio track state (dumpsys audio, filtered to the stage pid) every 4 s; read the audio event log lines for that track; filter the app's log to its pid for ExoPlayer, PlaybackException and onPlayerError lines; then open the card's detail screen and its Trailer button to read the trailer's own duration from the player controls.

Observed:
- Preview audio track started 08:56:33.614 and stopped 08:57:34.596: 61.0 s of playback.
- Trailer length in the full player (Trailer button): 0:05 elapsed and -0:56 remaining, so about 61 s.
- Observed duration (61.0 s) equals the trailer length (about 61 s): the preview ran to its end, not a cut-off.
- Preview player: ExoPlayerImpl Init 08:56:32.094, Release 08:57:35.671 (about 1 s after the track stopped). The still image was back on the panel afterwards.
- Stage pid log, filtered to ExoPlayer, PlaybackException, onPlayerError and AndroidRuntime: only the two Init/Release info pairs of the preview players; no error or exception line. No raw log is committed.
Result: AC-4 end-of-trailer is a real finish, not a player error. PASS.
Limit: the 61 s trailer length is read to the second from the player controls; the preview and the full player may use different stream variants of the same length.
