### F1 [major] Up from the seek bar lands on the X, and from there the action chip cannot be reached
- Where: `app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerTimedActionOverlay.kt:274-280` (the cancel `TimedActionButton`), together with the seek interception at `:265-271`
- Problem: the X is expected never to hold D-pad focus (design delta, "The X loses its D-pad role"). In `m4-prompt-under-controls-chip-idle.png` it does. Its glyph peaks at 236/255 and covers 121 lit px. In the two action-focused shots it peaks at 196/255 and covers 94 px. 196/236 = 0.83, which matches `TIMED_ACTION_ICON_IDLE_ALPHA` 0.82, and 121/94 is about 1.1² (the focus scale). So the X holds focus in that shot. The seek bar does not. qa had pressed Up to keep the controls up, and geometric search sent focus to the X: its centre (x ≈ 995) is closer to the bar's centre than the chip's (x ≈ 1129).
- Failure scenario: controls are up and the next-episode prompt is showing. The viewer presses Up and the ring lands on the X. Right is now consumed as a seek, so it no longer moves the ring to "Next episode". OK on the X cancels autoplay. The weakest-cued element ends up as the only OK target, and the action can only be reached by waiting for the controls to hide. The skip prompt has the same layout, so the same applies to it.
- Fix: make the X unfocusable for the D-pad with `Modifier.focusProperties { canFocus = false }` on the cancel button. Pointer clicks still work. Up from the seek bar then reaches the action chip, or nothing.
VISUAL: deviates

### F2 [minor] Skip-prompt focus has no device evidence (design check 1)
- Where: `app/src/main/java/com/kino/puber/ui/feature/player/component/SkipSegmentOverlay.kt:32`; evidence M3
- Problem: expected on a skip prompt: 85 % hairline and 1.1 scale on the action, X at 82 %. Not measured. M3 is NOT CHECKED because this build produces no skip segment. The code path is the same `focusRequester` as next-episode, which does match (see the VISUAL line).
- Failure scenario: none visible today. This was the user-visible bug the run set out to fix: the X held focus on the skip prompt.
- Fix: new screenshot needed. Skip prompt up, controls hidden, before any key press, with HW overlays off. Get it from a build with the TMDB token, or from a title IntroDB.app answers for.
VISUAL: not measured: no skip-segment prompt on this build

### F3 [minor] "Seek bar keeps focus under the prompt" (design check 2) is not shown by its evidence shot
- Where: `docs/superpowers/evidence/2026-11-10-seek-blocked-by-skip-prompt/m4-prompt-under-controls-chip-idle.png`
- Problem: expected both chips idle with the seek bar focused. The shot shows the action chip idle (175×52 px at 1280 = 131×39 dp, hairline about 45 %) but the X focused (F1). The `PlayerFocusEffects` path that should leave the bar alone was never captured without an Up press.
- Failure scenario: see F1.
- Fix: new screenshot needed. Open the controls with Down or OK before the prompt is due, press nothing, and capture once the prompt appears. Then take one more after a single Up to show where focus goes once F1 is fixed.
VISUAL: not measured: shot taken after Up taps

### F4 [nit] Evidence describes the screenshots as 1080p, but they are 1280×720 or contact sheets
- Where: `docs/superpowers/evidence/2026-11-10-seek-blocked-by-skip-prompt-device.md`, Build and device table and M4 row
- Problem: the device table says 1080p, so readers expect 1 dp = 2 px. The PNGs are 1280×720, 1280×360 and 1280×480, which is 1 dp = 1.33 px. The M4 heights "72 px / 80 px" match neither scale: 1280 measures 52/57 px, which is 78/85.5 px at 1080p. The 1.1 ratio holds either way.
- Failure scenario: a later reviewer measuring from these files gets the dp values wrong by a factor of 1.5.
- Fix: state the downscale in the evidence file and correct the M4 pixel figures.
VISUAL: match

VISUAL: deviates. Geometry and the focused look match base. No style constant changed in the diff. Focused chip measured 193×57 px at 1280, which is 145×43 dp, 1.10× its idle size. Right offset is 40.5 dp, plus 6.6 dp of scale growth, so about 48 dp (`TimedActionBottomPadding`). Next-episode with the controls hidden shows the action focused (bright hairline, 1.1) and the X at 82 %. The deviation is the X taking focus under the controls (F1). Two new screenshots are needed (F2, F3).
