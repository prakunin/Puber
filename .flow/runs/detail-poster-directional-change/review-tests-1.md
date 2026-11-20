LENS: tests  ROUND: 1  BASE: 01d1d305

### F1 [major] Twelve P0 tests marked "red" were green on the stubs and are not marked `guard`
- Where: plan:192-196 vs test-design.md:50-53,59-62,68-70,79
- Problem: panelDirection_isNone_* (4), directionTo_isNone_* (4), heldDescription_returnsCurrent_* (3), cardEnterAlpha_followsEnter_whenNoTrailer passed against the prescribed stubs (None/current/enter), so no run showed them failing. Checked by hand they catch realistic mistakes (directionTo without id match, stale shown, cardEnterAlpha always 1); what is missing is a recorded red run.
- Failure scenario: a rule that never shifts keeps all twelve green; only the seven End/Start/sign tests and device checks catch it.
- Fix: mark the twelve rows `guard` in test design and plan, or record a red run for each against a targeted mutant.

### F2 [minor] The AC-6 cap (skeleton after 3 s) is tested only as a pure rule
- Where: VideoItemGridDetails.kt:837-850 (rememberHeldDescription); evidence row M6
- Problem: the timer wiring remember(holding) + LaunchedEffect(holding){delay(POSTER_HOLD_MS)} is untested; M6 did not observe the >3 s case.
- Failure scenario: wrong effect key leaves the old card text beside a new focused card indefinitely, all tests green.
- Fix: a device step holding Loading >3 s, or record acceptance as a copy of rememberHeldUrls (same untested shape).

Suite state: check.full at 6eb4d15c exit 0; 2123 unit tests, 0 failures, 0 skipped; PanelDirectionTest 18, VideoDetailsPosterStateTest 25; detekt, lint green. No weakened assertions. Instrumented I1, I2 PASS in evidence. AC-10 and AC-5 refresh NOT CHECKED per spec decisions.

### Unproven
- VideoItemGridDetails.kt:944 crossfades(urlIndex): no unit test that a card change requests crossfade=false while first image and fallback still crossfade (accepted risk D10).
