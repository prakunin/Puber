# Review edge, round 1

LENS: edge  ROUND: 1  BASE: b27d8e40
### F1 [minor] The width steps are written out by hand, so changing the step or the floor on the stand gives a different step sequence than the stand shows
- Where: DetailsScreenContent.kt:880
- Problem: PLOT_WIDTH_STEPS = listOf(PLOT_WIDTH, PLOT_WIDTH - 70.dp, PLOT_MIN_WIDTH) matches the "down in steps of 70 to the floor" rule only for 70/460.
- Failure scenario: floor 400 -> code [590,520,400] vs stand 590/520/450/400; step 50 -> code 590/540/460 vs stand also 490; floor 540 -> [590,520,540] not widest-first.
- Fix: generateSequence(PLOT_WIDTH) { it - PLOT_WIDTH_STEP }.takeWhile { it > PLOT_MIN_WIDTH }.toList() + PLOT_MIN_WIDTH, PLOT_WIDTH_STEP = 70.dp.

### Unproven
- :330 maxWidth under PLOT_MIN_WIDTH: all steps clamped then dropped; safe; matters only below ~471 dp.
- :337 constraints.minHeight above the snapped budget could cut the last line; only caller passes min 0.
- :335 heightAt intrinsics up to four times per measure (spec R6).
