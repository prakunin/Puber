# Gate - seek-blocked-by-skip-prompt - round 1

**Decision:** CONCERNS
**Head:** 1d38134a   **Base:** 6b087697

## Done as asked

`git diff 6b087697...HEAD` delivers scope items (a)-(e) of the spec, with one exception in (d) (see Concerns 1):

| Scope | Where in the diff |
|---|---|
| (a) seek keys forwarded and consumed | `PlayerTimedActionOverlay.kt` Row `onPreviewKeyEvent` + `seekActionForKeyCode` (`PlayerVideoSurface.kt:231`) |
| (b) skip prompt focuses its action | `SkipSegmentOverlay.kt` passes the shared `focusRequester` to the action; the X is `canFocus = false` |
| (c) no prompt during a seek burst | `PlayerVM.kt:713-716` `startSeekBurst`, gates at `:1527` and `:1644` |
| (d) focus on hide | `PlayerScreenContent.kt:83-91`: surface when the controls are hidden; nothing when the controls are up |
| (e) no focus taken under the controls | same effect runs only with `!controlsVisible` |

Nothing from Out of scope is touched: no style constant changes, Up/Down and Play/Pause are not forwarded (`seekActionForKeyCode_returnsNull_forUpDownCenterAndPlayPause`), and dismissal policy is unchanged (`PlayerVMPromptDismissalTest` 6/6).

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `skipPrompt_leftAndRightSeekAndFocusStaysOnAction`, `skipPrompt_heldRightSeeksOnEveryRepeat`; device S1 | Red at `b7854fbb` (`red-run-b7854fbb.md` :92, :103); green at round 2; `r2-skip-recap-left-right-prompt-returns.png` | PASS |
| AC-2 | `nextEpisodePrompt_leftAndRightSeekAndFocusStaysOnAction`; device M1, M4c, M4d | Red at `b7854fbb` :113; green; `m1-left-on-prompt-seeks-prompt-comes-down.png` | PASS |
| AC-3 | `PlayerVMSeekBurstTest` (6 cases incl. `creditsLoadedMidBurst...`, `aSeekBackwardBurst...`); device M1, S1 | Unit XML 0 failures (re-run by judge, below); 1.5 s timing not measurable on device (buffering) | PASS |
| AC-4 | `skipPromptHidden_nextRightSeeks`, `nextEpisodePromptHidden_nextRightSeeks` (guards, green on base); device M2 | Device: Left, Back, countdown-out, skip Back (S3) each followed by a seeking key. Hide through the action followed by Left/Right not observed (M2 OK NOT CHECKED; S2 has no following key) | PASS |
| AC-5 | `skipPrompt_appearsWithActionFocused_okSkips` (red at `b7854fbb` :150), `skipPrompt_backCancels`; device S2, S3 | `r2-skip-intro-ok-skips.png`, `r2-skip-recap-back-dismisses-right-seeks.png` | PASS |
| AC-6 | `promptAppearingUnderControls_seekBarKeepsFocusAndRightSeeks` (red :180), `controlsHidingWhilePromptIsUp_focusesThePromptAction` (red :194); device M4a, M4d | `r2-m4-prompt-under-controls-no-key-pressed.png`, `r2-m4-run2-right-on-seek-bar-seeks-prompt-down.png` | PASS |
| AC-7 | `skipPrompt_heldLeftAndRightFireNeitherSkipNorCancel`, `skipPrompt_okUpWithoutDownDoesNotSkip`; device M5 single long press | Instrumented green; multi-second hold on a remote is manual (section below) | PASS |
| AC-8 | Full unit suite; `PlayerVMPromptDismissalTest`, `PlayerVideoSurfaceTest` | Judge run of check.full: BUILD SUCCESSFUL, 222 suites, 0 failures, 0 errors | PASS |
| AC-9 | Owner sequence (M1 + M5 on a real remote) | Parts ran (M1 credits burst, S1-S3); the flash judgement is the owner's | NOT CHECKED |

Named tests: `check-named-tests.py` 0 missing; every test named in `test-design.md` grep-resolves in `app/src/test` or `app/src/androidTest`.

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| X still a D-pad target (Up from seek bar) | blind F1, ux F1 (major) | fixed | `d95aa91d`; device M4b `r2-m4-after-one-up-chip-focused-x-idle.png` |
| OK on skip prompt now skips | blind F2 (minor) | rejected, accepted by judge | Spec Q1 / AC-5 decide exactly this |
| Late credits load bypasses the burst | blind F3, codex (minor) | fixed | `670934be`, `PlayerVM.kt:1644`, test `creditsLoadedMidBurst...` |
| `onPlaybackEnded` not gated | blind F3, codex adversarial (minor) | rejected, accepted by judge | Checked: prompt checks run only under `isPlaying` (`PlayerVM.kt:1524-1530`), so a gated end-of-media prompt would never return |
| Three red Compose tests lacked a valid red run | tests F1 (major) | fixed | `red-run-b7854fbb.md`: failing lines :150, :180, :194 with the chip resolved |
| `everySeekInTheBurstRestartsTheWindow` vacuous | tests F2 (major) | fixed | `670934be`; tests-2 confirms the 2 000 ms assertion |
| No `SeekBackward` burst test | tests F3 (minor) | fixed | `670934be`, `aSeekBackwardBurst...` |
| Refocus-on-hide has no failing test | tests F4 (minor) | accepted | Plan records AC-4 refocus as device-only (M2) |
| Skip-prompt focus no device shot | ux F2 (minor) | fixed | Round 2 S1-S3 |
| M4 shot taken after Up | ux F3 (minor) | fixed | M4a, M4d round 2 |
| Evidence scale wrong | ux F4 (nit) | fixed | Evidence Build and device row |
| X-unfocusable has no automated test | tests-2 F1 (minor) | accepted | Device M4b |
| Red-run header counts (11/6 vs 12/7) | tests-2 F2 (minor) | accepted | Record-only; still wrong in `red-run-b7854fbb.md` and plan :174 |
| Plan cites test headers, not assertions | tests-2 F3 (nit) | accepted | Record-only |
| Stale "either of them can hold the focus" comment | edge-2 F1 (nit) | accepted | Still at `PlayerTimedActionOverlay.kt:330` |
| Contact-sheet frame scale | ux-2 F1 (nit) | accepted | Evidence text only |

Idea runs recorded from triage-1: `prompt-key-passthrough`, `introdb-imdb-id-prefix` (both exist under `.flow/runs/`).

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One `launch { delay }` per seek, cancelled on the next; one extra effect key (prompt kind). No hot-path allocation of note. |
| Reliability | Burst job dies with the VM scope; not cancelled on episode switch (accepted R4, bounded by 1.5 s). Focus request retries one frame. Controls-up hide path leaves focus unowned (Concerns 1). |
| Maintainability | Focus ownership moved into `PlayerFocusEffects` only; overlay effect removed, no dead requester. Stale comment and unreachable focused tint for the X remain (accepted nit). `requestFocusOrRetryNextFrame` will overlap `requestAcrossFrames` on `player-overlays-port` at rebase. |
| Security / privacy | `tools/mobile-evidence-audit.sh` re-run by judge: `passed`, 16 files. No secrets in diff. |
| TV | Back dismisses (`BackHandler`), X pointer-only, OK runs the action, seek keys consumed. |

## Manual checks for the owner

1. AC-9 / M5: on a real remote, play an episode with a known intro (for example Outlander S1). Hold Right for several seconds from before the intro through it, then again through the credits lead-in. Expected: seeking never stops; nothing skips or cancels; judge whether any prompt flash during the burst is acceptable. Then let the prompt appear and press Right twice: both seek.

## Must fix

none

## Concerns for the owner

1. Scope (d) says focus returns to the controls target when a prompt hides with the controls up. Not implemented: `PlayerScreenContent.kt:83-91` acts only with the controls hidden, and the controls effect keys only on `controlsVisible`/`controlsFocusTarget`. Reachable path: controls up, Up onto the chip (M4b), Right seeks and hides the prompt (M4c); what the next key does was not observed. No AC covers it; it heals when the controls auto-hide.
2. AC-4 hide through the action (OK) followed by Left/Right was not observed on the device; it is covered only by state-level Compose guards that also pass on base.
