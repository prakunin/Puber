Visual review, round 2 (`b6660c74..1d38134a`). All four round-1 findings closed. No blockers or majors. One nit in the evidence text. No device lease, no files written; measured from round-2 PNGs.

### F1 [nit] Evidence says to measure contact sheets on the 1280 scale, but their frames are 640 px wide
- Where: `docs/superpowers/evidence/2026-11-10-seek-blocked-by-skip-prompt-device.md`, Build and device table ("measure dp from the 1280 scale")
- Problem: `r2-skip-intro-ok-skips.png` (1280x360), `r2-skip-recap-back-dismisses-right-seeks.png` (1920x360) and `r2-skip-recap-left-right-prompt-returns.png` (1280x720) are made of 640x360 frames, where 1 dp = 0.67 px, not 1.33 px.
- Failure scenario: the next reviewer measures the skip chip on the 1280 scale and gets dp values half the real ones.
- Fix: add to that table row: "contact sheets hold 640 px wide frames, 1 dp = 0.67 px".
VISUAL: match

Round-1 findings:
- F1 [major] X took focus: closed. `PlayerTimedActionOverlay.kt:380` sets `focusProperties { canFocus = false }` when `iconOnly`; both callers (`NextEpisodeOverlay.kt:32`, `SkipSegmentOverlay.kt:37`) pass `compactCancel = true`. On device, `r2-m4-after-one-up-chip-focused-x-idle.png`: X peaks at 197/255 over 97 lit px after one Up (93 before), matching the idle X of round 1 (196/94), not the focused X (236-238/121). The chip grows 174 -> 191 px at 1280 (130 -> 143 dp, 1.10); focused hairline peak 196 vs 104 idle. Up from the seek bar now lands only on the action.
- F2 [minor] No evidence for skip-prompt focus: closed, with a resolution caveat. "Skip recap" and "Skip intro" captured on device with the controls hidden; the chip hairline is distinguishable as focused at 640 px; X muted, only the action focused. The 1.1 scale on the skip prompt is not measured directly (no idle frame, indistinguishable at 0.67 px/dp); same `focusRequester` and style as next-episode, where 1.10 is measured.
- F3 [minor] Seek-bar focus under the prompt not shown: closed. `r2-m4-prompt-under-controls-no-key-pressed.png` and `r2-m4-run2-prompt-under-controls-no-key-pressed.png`: no key pressed, controls open, seek thumb enlarged, chip and X idle (chip 130 dp, hairline 104). `r2-m4-run2-right-on-seek-bar-seeks-prompt-down.png`: Right seeks, no highlighted button.
- F4 [nit] Evidence scale: closed for M4 (175x52 px at 1280 = 131x39 dp, 191 px = 143 dp). The contact-sheet remainder is F1 above.

No style constant changed in the diff. The `PlayerVM.kt` change is behavioural, with no visual effect in the round-2 shots.

VISUAL: match
