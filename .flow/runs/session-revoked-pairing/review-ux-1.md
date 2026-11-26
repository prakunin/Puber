### F1 [minor] Blocked line in the Auth code state was never seen on a device and has no preview
- Where: `app/src/main/java/com/kino/puber/ui/feature/auth/component/AuthComponent.kt:132-154` (`CodeInfo`), preview at the end of the file
- Problem: the spec asks for a 20 dp gap and the 20/16/20 dp gaps kept. The code matches that, but nobody has seen it rendered. A worktree build gets no device code, so the box only showed the loading state. The `CodeInfoPreview` still calls `CodeInfo` without `accountBlocked`. By my estimate the column needs about 484 dp of the 540 dp screen height: the two-line blocked line about 48 dp, then 20, code about 44, 20, QR 200, 20, url 24, 16, time 24, 20, a two-line hint about 48. That leaves about 28 dp above and below, close to the TV overscan safe area. It gets worse if the mirror hint or the English text wraps to one more line.
- Failure scenario: the session ends as blocked on a master or release build, which does get a device code. The code screen then shows the red line on top and the hint at the bottom very close to the screen edges, and nobody has looked at that layout before the owner judges AC-10.
- Fix: add `@Preview internal fun CodeInfoBlockedPreview()` with `accountBlocked = true` at 1920x1080 (`device = "id:tv_1080p"`), so the owner can judge AC-10 in both states. No layout change unless the preview shows edge crowding.
VISUAL: not measured: Content state needs a client-secret build

### F2 [nit] Home error view with Retry only has no visual evidence
- Where: `app/src/main/java/com/kino/puber/ui/feature/home/component/HomeScreenContent.kt:130-167`
- Problem: the expected layout is message, 24 dp, Retry, with Retry focused at launch. The code matches: `rememberFocusRequesterOnLaunch` is on Retry, and the 8 dp spacer is removed together with the mirror button. Nothing on a device shows it, because M-2 cannot happen on the box (the banned session ends at once). Settings has the instrumented `DeviceSettingsContentTest`, which checks the focused Retry and the missing mirror button. Home has nothing like it.
- Failure scenario: none expected from the code. If the launch focus request lost a race with the screen's other focus handling, the viewer would see Retry without its focused state until they pressed a D-pad key.
- Fix: none required for this run. A compose test like `DeviceSettingsContentTest`, checking that the Retry node is focused, would close the gap.
VISUAL: not measured: state not reachable on the box

The loading state of the Auth screen matches the spec. I measured it on `2026-11-26-session-revoked-pairing-r2-auth-30s.png`, `-r2-auth-65s.png` and `-r2-after-second-back.png` (1920x1080, dp = px / 2):
- **Text size:** lines are 48 px apart (356 to 404), which is 24 dp. That is `bodyLarge` 16 sp, the same as the mirror hint lines (917 to 965).
- **Width:** the longest line runs from 493 to 1427 px, about 467 dp. It is centred, under the 560 dp limit, wraps to two lines and is not cut off.
- **Position:** the text block's centre is about y = 380 px, 160 px (80 dp) above the screen centre. That matches `Alignment.Center` with `padding(bottom = 160.dp)`, mirroring the status message's `top = 160.dp` (spec Q7). Between the text bottom (about 425) and the top of the spinner (about 500) there are about 37 dp, so they do not collide.
- **Stability:** the mirror hint at the bottom (`padding(horizontal = 64, vertical = 48)`) does not overlap the line, and the line stays in the same place from 30 s to 65 s and after the dialog closes.
- **Colour and focus:** the red `colorScheme.error` text reads clearly on the black background, with no container or icon. The line is not focusable, and the root `Box` is still the only focus target.

Settings error view: Retry alone stays inside the existing `Row`, which the `CenterHorizontally` column centres. The title, the message and the 20 dp gap are unchanged.

Shared constants: the `TvSafeButton` height and corner radius are unchanged, and both error views take `showMirrorAction` as a parameter set by the VM state, as the design asked.

VISUAL: match
