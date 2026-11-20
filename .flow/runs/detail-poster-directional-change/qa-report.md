# Round 4

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; 2134 unit tests, 0 failures, 0 skipped)
DEVICE: emulator-5554, leased and released; worktree dev APK at 484e5adf installed with install-preserve (outcome installed, on-device sha256 1191f059... matches)

| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | R4-1, R4-3: Movies catalogue, warm double Right (80-90 ms) twice and a triple Left; outgoing poster held opaque, interrupted layer replaced, final poster full strength and in place, no bare strip |
| AC-2 | PASS | R4-2, R4-3: warm double Left and triple Left; nothing dimmed or shifted left behind, no right-edge strip |
| AC-3 | PASS | R4-4..R4-6: real Favourites screen, warm double Right, double Left and triple Right; clean settle |
| AC-7 | PASS | R4-7: no skeleton in any sampled frame |
| AC-8 | PASS | R4-8: trailer video visible in 3 of 3 screenshots (Movies catalogue, after double Right and double Left) |
| AC-5 (row change) | PASS | R4-9: Down to the next row crossfades in place, no horizontal shift |
| AC-4, AC-6, AC-9, AC-11 | unchanged from round 2/3 | not re-run; static gate green |
| AC-10 | NOT CHECKED | owner judges on TV (smoothness, remaining seam on Left presses) |
| AC-5 refresh path | NOT CHECKED | needs an account change |

TESTS ADDED: none (the seeding tests from 484e5adf ran green in the static gate)
EVIDENCE: /home/user1/orca/workspaces/Puber/detail-poster-directional-change/docs/superpowers/evidence/2026-11-20-detail-poster-directional-change-device.md (round 4 section; audit passed; no frames committed; committed in wt)
DEFECTS: none
UNAVAILABLE: none
MANUAL: owner judges AC-10 on the TV (smoothness; any old-poster seam on Left presses); AC-5 refresh path needs an account change. Caveat: while a trailer plays the emulator handles keys late, so the triple Left in the catalogue was handled spread out, not packed at 90 ms; the Favourites triple Right (no trailers) was handled packed. A cold-poster double press was not re-run (covered in round 3, R3-1).
