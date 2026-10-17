# Device evidence: tab stack lost on root push (round 2)

Slug `tab-stack-lost-on-root-push`, worktree HEAD `6b47873d` (Task 5: ShowAll focus restore), base `f14dcc84`.

## Build and device

- Device: `emulator-5554` (AVD `puber_tv_36`, API 36 TV). Lease held, released at the end; emulator left running.
- Build: this worktree's own `:app:assembleDevDebug`, APK sha256 `c81e9e4c...54a8`; the on-device `base.apk` sha256 is identical.
- Install: `tools/android-apk-install-preserve`, outcome `installed`, `compatible_replace`, `destructive_action: false`, signer unchanged (`6b291812...49a83e`). No uninstall, clear, downgrade or signer change.
- Input: D-pad keyevents only. Session log switched on and off through Settings > For developers; OFF again at the end (screenshot checked). Log lines below are `type/scr/cmd/from/to` fields only.
- No logout (AC-7 stays NOT CHECKED on device).

## Static gate

`AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`: BUILD SUCCESSFUL, exit 0 (44 tasks, 1 executed, the rest up to date on identical inputs from the implementer run at this HEAD).

## Instrumented classes (`make GRADLE=./tools/agentw DEVICE=emulator-5554 itest TESTS=<fqcn>`)

- `com.kino.puber.ui.feature.showall.content.ShowAllScreenContentFocusTest`: 2 tests, 0 failed, exit 0 (new in this round).
- `com.kino.puber.core.ui.navigation.component.TabHostTelemetryWiringTest`: 10 tests, 0 failed, exit 0.
- `com.kino.puber.core.ui.navigation.component.TabFlowLifecycleTest`: 1 test, 0 failed, exit 0.

## M1 (AC-1, AC-2, AC-4): PASS

Concerts tab, "Popular" row, "Show all" (DPAD_CENTER) opens the grid (tab-local push). DPAD_RIGHT from the rail into the grid.

- Opener A: DPAD_DOWN x4, DPAD_RIGHT, card "Derek Deane / Swan Lake" (second visible row, middle column, grid scrolled several rows).
  DPAD_CENTER: Details. KEYCODE_BACK once. After 3 s the screen is the Show all grid, the same rows in the same positions as before
  Details (screenshots compared: same card layout, no one-row shift), focus ring on "Derek Deane / Swan Lake".
  Round 1 landed on "Sam Smith" and shifted a row.
- Opener B, not in the first visible row: DPAD_DOWN x3 more, "The Original Blues Brothers Band / Pleins Feux" (grid scrolled further,
  first visible row is "Foo Fighters / Black Eyed Peas / VA 50th"). Details (title confirmed on screen), BACK: grid shown, rows identical to
  before, focus ring on the Blues Brothers card.
- Opener C: left column, "Katseye / Lollapalooza", Details, BACK: focus ring on Katseye (M2 below).
- Log (one grid session, Details opened from the grid four times): every return is `nav cmd=back from=Details to=ShowAll` with
  `scr=ShowAll`; no `ContentList` line follows (AC-4). BACK from the grid to the root happened only once, on purpose.

## M2 (K13, rail open during the return): PASS

Movies tab, "New releases", Show all, DPAD_DOWN x3 (card "Tenzing", left column). In one adb shell call:
DPAD_CENTER, wait 2.5 s, BACK, wait 0.5 s, DPAD_LEFT.
Screenshots 0.5 s and 4 s after the LEFT: rail expanded, focus on the Movies rail item at both times; the grid behind it
is at the restored scroll position. Focus was not pulled back into the grid. DPAD_DOWN x2, DPAD_CENTER on the rail then switched
to the 4K tab (see AC-3). (A first try with LEFT sent immediately after BACK, before focus existed, was dropped by the app
and the rail did not open; the focus ring was then on the opener. Not a stolen-focus case.)
A variant run on Concerts where the rail was already open when DPAD_CENTER was sent selected the Concerts rail item, which reset the tab to its root
(`nav cmd=tab`, then `nav cmd=back from=ShowAll to=ContentList`): the rail click on the current tab pops the tab to its root; my script's artefact, not a defect of this change.

## AC-3 (tab switches keep stacks, root push keeps both): PASS

4K tab: "Popular" Show all, Details on "Silo", BACK: 4K grid shown, focus on Silo. Rail to Movies: its Show all grid shown, focus on "Tenzing".
Rail to 4K: its grid shown, focus on "Silo". Neither tab showed its root.

## AC-9 (resume delivered once): PASS

Home, "My bookmarks" first card (Outlander), DPAD_CENTER (Details), BACK. Log: `nav push_result Home->Details`, `nav cmd=back from=Details to=Home`,
`act Resumed scr=Main`, exactly one `act OnResume scr=Home` after it. Same card focused, no crash. No player opened.

## AC-8 (Settings tab renders): PASS

Rail to Settings after the sequence above: "General" content rendered 1.5 s after the switch and still at 4.5 s, no spinner.

## AC-7 on device: NOT RUN

No logout from a worktree build (cannot log in again). Stand-in `TabHostTelemetryWiringTest.replacedHostStartsAtTabRootsWhenShownAgain` green.

## Observations (unchanged from round 1, not attributed to this change)

1. First entry into a Show all grid from the tab root (Concerts, Movies): the rail expanded with focus on the rail item; DPAD_RIGHT focused the grid.
2. Settings tab shows "General" again after leaving and returning (previously "For developers").
