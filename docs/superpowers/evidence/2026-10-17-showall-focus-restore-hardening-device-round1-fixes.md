# Show all focus restore hardening - device evidence, review round 1 fixes

Slug: showall-focus-restore-hardening. Worktree head: cc66ec96 (restore effect keyed on `items` and `contentFocusActive`, two new JVM cases).
Device: emulator-5554 (AVD puber_tv_36), leased, released at the end, left running.
Build: this worktree's own `app-dev-debug.apk` (`com.kino.puber.stage`, 1.10.0-dev, sha256 d84456fcb415ca4f28fc12895ac4da92dda25214d58f37c697ffe9fa8b39afed), installed with `tools/android-apk-install-preserve` (outcome `installed`, `compatible_replace`, destructive_action false). The on-device base.apk sha256 equals the built APK.
Input: D-pad key events only. No playback, no bookmark or watched toggle. Screenshots were read and not stored in the repository.

## Static gate

`AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`: BUILD SUCCESSFUL. Unit and detekt tasks were UP-TO-DATE from the implementer's run at the same head; `lintDevDebug` re-executed. Stored `GridFocusMemoryTest` result: tests=23 failures=0 errors=0.

## Instrumented class

`make GRADLE=./tools/agentw DEVICE=emulator-5554 itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllScreenContentFocusTest`: Finished 5 tests, 0 failed, BUILD SUCCESSFUL.

## D-M1: Back restores the same card

Movies tab, "New releases" row scrolled to its end, "Show all", RIGHT into the grid, DOWN x6 then RIGHT (right column card "Luntik. Obratnaya storona Luny", below the first viewport), CENTER (Details, 3 s, "Not started" unchanged), BACK, 4 s. Expected: same card focused with no key. Observed: the same card focused with the same scroll position. PASS.

## D-M2: rail opened right after Back is not stolen

Left column card "Korol Interneta / O Rei da Internet", CENTER (Details), BACK, 0.6 s, LEFT. Expected: rail opens and keeps focus. Observed: rail expanded with "Movies" focused at 0.8 s and still at about 6 s with no input. PASS.

## Rail open/close regression check (effect key)

Sequence over the focused grid, each step by D-pad: close rail (RIGHT), DOWN, open rail (LEFT), close rail (RIGHT), repeated 5 times with focus moved by DOWN in between.

- No close returned focus to a card focused earlier in the sequence (no older-card jump), and the grid did not take focus while the rail was open. PASS for the stated check.
- Observation: on every close in this screen the focus landed on the card one row above the card that was focused when the rail was opened (e.g. rail opened from "Korol Interneta", closed onto "Terapiya Kamino"), and five times in a row. In the History grid (no Show all code) the same open/close kept the focused card. A close right after a tab switch back landed on the card in the rail's row band (middle column). By code reading `reconcile()` returns early after the first grid focus (pendingEntry false, no card left), so the new effect key is a no-op in this sequence; whether this one-row-up landing is pre-existing could not be settled without a base build (not allowed for qa to build outside the worktree or edit production code). Reported as an open observation, not attributed to this change.
