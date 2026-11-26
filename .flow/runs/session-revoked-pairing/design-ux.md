# Design delta: session-revoked-pairing (ux-designer)

**No `docs/design/` stand governs this change.** Stands exist for `catalogue`, `content`, `player-controls`, `showall`; none covers Auth, the Home error view or the Settings error view. Per `.flow/config.yaml` `escalate_also`, the conductor runs `designing-a-screen` with the owner for the Auth blocked line before planning, or the owner accepts the proposed numbers below in place of a stand (AC-10 is the owner's call either way). The Home and Settings deltas only remove a button and need no stand.

## Auth: blocked-account line (AC-6, AC-10)

Current layout, `ui/feature/auth/component/AuthComponent.kt:117-134` (`CodeInfo`, centred column): code 32 sp, 20 dp, QR 200 dp, 20 dp, url, 16 dp, time left, 20 dp, mirror hint. Body lines use the default `Text` style (theme `bodyLarge` 16 sp, `core/ui/uikit/theme/Type.kt:11-14`).

| Item | Proposed | Status |
|---|---|---|
| Where | First item of `CodeInfo`, above the code: it is the reason the screen is shown | open (owner) |
| Gap to the code | 20 dp (the column's existing rhythm) | open (owner) |
| Type | `bodyLarge` 16 sp, same as the sibling lines | proposed |
| Colour | `colorScheme.error` for the text only; no container, no icon | open (owner) |
| Width | max 560 dp, centred, `TextAlign.Center`, wraps; never truncated | open (owner) |
| States | Shown only in `AuthViewState.Content` when the reason is blocked; not in `Loading`; reason expired shows nothing and the column keeps today's geometry | fixed by AC-6 |
| Text | Own string (Q2); must say the KinoPub account is blocked and that pairing again will not help | fixed by brief Q2, wording for the owner |

Focus: the line is not focusable. Auth keeps its single focus target, the root `Box` (`AuthComponent.kt:51-63`), so long-press OK still opens the mirror dialog; no new focus order.

## Home error view (AC-7)

`ui/feature/home/component/HomeScreenContent.kt:129-159`: message, 24 dp, Retry (`TvSafeButton` primary, 48 dp), 8 dp, mirror (`TvSafeButton`).

- For `ApiError.Unauthorized`: message, 24 dp, Retry only. Drop the 8 dp spacer with the button, so Retry sits where it sits today.
- Network and other HTTP errors: unchanged.
- No "pair again" button: an `Unauthorized` that reaches this view did not end the session (brief AC-3), so pairing is not the remedy and a pairing action would wipe a session a worktree build cannot restore.
- Focus: Retry is the only focusable; it must hold initial focus when the view appears (today the view has no focus requester; with one button it must not depend on default focus search). Focus visibility: `TvSafeButton` focused state, unchanged.

## Settings error view (AC-7)

`ui/feature/device/settings/DeviceSettingsContent.kt:131-176`: title, message, 20 dp, `Row` spaced 12 dp of Retry + mirror (tv-material3 `Button`).

- For `ApiError.Unauthorized`: the row holds Retry only, centred; title, message and the 20 dp gap unchanged.
- Focus: Retry keeps `rememberFocusRequesterOnLaunch` and `SettingsTestTags.ErrorRetry`; nothing new.

## Shared constants

- Both `ErrorView`s are private per screen and share no constant; parameterise each with a boolean (e.g. `showMirrorAction`) decided in the VM state, not by inspecting the error type in the composable. This is the seam `screen-error-states` will fold into a shared view.
- `TvSafeButton` `ButtonHeight` 48 dp and `ButtonCornerRadius` 24 dp (`core/ui/uikit/component/TvSafeButton.kt:39-40`) are shared app-wide: do not retune.
- `CodeInfo` spacings are used by the preview and the live screen only; adding the line must not change the existing 20/16/20 dp gaps.
