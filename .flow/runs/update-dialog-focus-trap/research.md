# Research: update-dialog-focus-trap

## 1. Request

Bug reported by the owner on 2026-11-20 (in Russian, translated): "I open the app, it checks for a new version, finds one, and the update window appears - but focus stays under this window, and it is impossible to press Update." The update prompt overlay (app/src/main/java/com/kino/puber/ui/feature/update/component/UpdatePromptOverlay.kt, hosted in ui/feature/root/component/App.kt UpdatePromptHost) shows at app start when a new release exists. Suspected cause (conductor's code reading, unverified): UpdatePromptOverlay requests focus once, 100 ms after the state appears, while the main screen's own launch focus logic takes focus later when its data arrives; TvDialogOverlay (core/ui/uikit/component/TvDialogOverlay.kt) is an in-tree Box with focusGroup() that does not trap focus nor make the screen beneath unfocusable. Release v1.12.3 tag is on hold until this fix lands on master.

## 2. Understanding

The update prompt is drawn in the same Compose tree as the screen beneath it, on top of it but not modal for focus. It asks for focus once; the Main/Home screen beneath keeps issuing its own programmatic focus requests (launch focus, hero claim, row restore) as its data arrives, and nothing stops either those requests or D-pad moves from putting focus on the hidden screen. The fix must make the prompt keep focus while it is shown, then give focus back to the screen beneath when it closes.

## 3. Current state in code

Paths below are relative to `app/src/main/java/com/kino/puber/`.

**The prompt**

- `ui/feature/root/component/App.kt:129-143`: `UpdatePromptHost` is composed as `content()` of the root `FlowComponent` (`App.kt:163-170`). It is a sibling after the navigator, so it draws on top and its `BackHandler` wins. Its VM is `viewModelOf(::UpdatePromptVM)` in the root flow scope (`App.kt:56`).
- `core/ui/navigation/component/FlowComponent.kt:103-109`: `FlowNavigator(...)` and then `content()`. The screen sits in a `Box` with `focusRequester(contentFocusRequester).focusRestorer().focusGroup()` (`FlowComponent.kt:149-156`). The prompt sits outside that box.
- `ui/feature/update/vm/UpdatePromptVM.kt:36-55`: `onStart` runs the auto check; on success the state goes `Hidden -> Available` at whatever time the network answers. Failures are silent. `onBackPressed` (`:66-72`) dismisses.
- `ui/feature/update/component/UpdatePromptOverlay.kt:56-61`: `LaunchedEffect(state)` with `delay(100)` and one `runCatching { primaryFocusRequester.requestFocus() }`. The result is ignored and there is no retry and no re-claim.
- `core/ui/uikit/component/TvDialogOverlay.kt:46-54`: a full-size scrim `Box` with `.focusGroup()` only. There is no `onExit` trap, so D-pad search can leave it for any focusable under the scrim. Its `LocalTvDialogFocusRestorer` hook (`:19-24`, `:38-42`) only reports dialog close to an opener; nothing provides it at root level.

**What takes focus beneath (Main/Home at launch)**

| Requester | Where | When it fires |
|---|---|---|
| `rememberFocusRequesterOnLaunch` for the main content | `ui/feature/main/component/MainScreenComponent.kt:95`; `core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:43-67` | 100 ms after first composition, once. Gated by `LocalAutoFocusOnLaunchEnabled` (`:23`) and drawer state. |
| Home hero claim | `ui/feature/home/component/HomeScreenContent.kt:194-203`, `:319-343` | When `heroItems` first publish (network-timed), with up to 5 frame retries. Gated by `LocalContentFocusActive` (`:193`). Added in `43df905d` (2026-08-20, in v1.10.0 and later). |
| Row reconcile and restore | `core/ui/uikit/component/moviesList/FocusTargetState.kt:75`, `:113-133`, `:176` | On row publish and restore. Gated by `LocalContentFocusActive`. |
| Tab content restore and handoff | `FlowComponent.kt:635-645`, `:649-664`; `core/ui/uikit/component/drawer/ContentFocusHandoff.kt:54`, `:93` | On stack pop and rail handoff. |

The prompt and Home both wait on network calls, so either order is possible. Whichever request runs last takes focus, which matches the intermittent reports.

**Existing patterns worth reusing**

- Gate the content beneath while an overlay is open: `LocalContentFocusActive provides (contentFocusActive && contextMenuItem == null)` in `ui/feature/bookmarks/component/BookmarksScreenContent.kt:77`, `ui/feature/favorites/content/FavoriteScreenContent.kt:95` and `ui/feature/collections/detail/component/CollectionDetailScreenContent.kt:57` (shipped in `bookmarks-context-menu-focus-loss`).
- `MainScreenComponent.kt:138` sets `LocalContentFocusActive provides (drawerState.currentValue != DrawerValue.Open)` without reading the outer value. A root-level gate would be overridden there unless the two are combined with AND.
- Focus trap precedent: `.focusProperties { onExit = { cancelFocusChange() } }` in `ui/feature/player/component/PlayerSidePanel.kt:142`, and a conditional version in `ui/feature/player/component/EpisodesPanel.kt:106` and `:185`.
- Retried initial focus: `TvContextMenuDialog` (`core/ui/uikit/component/TvContextMenu.kt:77-90`, 4 attempts) and `ClaimHeroFocusEffect`.

**Other `TvDialogOverlay` users**, all affected by a change to it: `TvContextMenuDialog` (`TvContextMenu.kt:61`) and `ApiDomainDialog` (`core/ui/uikit/component/ApiDomainDialog.kt:100`, which has a text field and an IME). `ResumeDialog` (`ui/feature/player/component/ResumeDialog.kt:51-52`) does not use `TvDialogOverlay`.

**Tests**

- `app/src/test/kotlin/com/kino/puber/ui/feature/update/vm/UpdatePromptVMTest.kt` covers VM state only.
- No UI test covers `UpdatePromptOverlay` or `TvDialogOverlay` focus.
- Instrumented focus-test precedents: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/home/component/HomeFocusTraversalTest.kt`, `.../bookmarks/component/BookmarksScreenContentFocusTest.kt`, `.../core/ui/uikit/component/TvContextMenuLongPressTest.kt`.

**History**

- The overlay focus code dates from `2c1a4a21` (Add manual update check) and `1b7646af` (Release 1.5.0).
- `TvDialogOverlay` was last changed in `1b7646af`.

## 4. Related work

| Run | Stage | Relation |
|---|---|---|
| `update-prompt-focus` | idea (2026-10-11) | **Probable duplicate.** Same symptom, seen in upstream-sync-triage QA: overlay buttons not focusable, D-pad reached Main, first Back moved focus to the drawer. This run should absorb it. |
| `bookmarks-context-menu-focus-loss` | done (`dc15e42b`) | Introduced the `LocalContentFocusActive` gate pattern for overlays. |
| `home-rows-context-menu-focus-loss` | idea | Home rows and `ClaimHeroFocusEffect` steal focus from an open context menu. Same mechanism, so the same gate would cover it. |
| `favourites-launch-focus-ignores-rail` | idea | `rememberFocusRequesterOnLaunch` ignores `LocalContentFocusActive`. Same gap if the gate relies on that local alone. |
| `focus-request-helper` | idea | Proposes one shared `tryRequestFocus()`. Overlaps with the retry/claim helper. |
| `player-focus-after-panel-close` | idea | A swallowed `requestFocus()` failure, the same class of bug in the player. |

`docs/architecture/navigation.md:27-29` says an overlay is "a normal screen or content inside the current screen". No root-level modal layer is described. No spec under `docs/superpowers/` covers the update prompt.

## 5. External findings

| Finding | Source |
|---|---|
| `focusProperties { onEnter / onExit }` with `cancelFocusChange()` blocks focus crossing a group boundary. These are the recommended replacements for the deprecated `enter`/`exit` setters. The project uses Compose UI 1.12.x (BOM 2026.09.00), which has them. | https://developer.android.com/develop/ui/compose/touch-input/focus/focus-group ; https://developer.android.com/reference/kotlin/androidx/compose/ui/focus/FocusProperties (fetched 2026-11-20) |
| `canFocus = false` applies only to the focus target it is attached to, not to its descendants. Disabling a parent does not make the subtree unfocusable. | FocusProperties reference above |
| A TV app trapped D-pad focus in modal overlays with `onExit { cancelFocusChange() }` and left the Exit direction (clearFocus) untouched so IME Done and Back on a text field still work. | https://github.com/sreichholf/dreamDroid/pull/682 (third-party, secondary) |
| Whether `onExit` also cancels a **programmatic** `requestFocus()` on a node outside the group (rather than only D-pad moves) is not stated in the docs. `unverified` | none found |

## 6. Options

| | Option | Trade-offs | Size |
|---|---|---|---|
| A | **Trap and re-claim in the overlay only.** `TvDialogOverlay` gets `focusProperties { onExit = { if directional: cancelFocusChange() } }`. `UpdatePromptOverlay` retries its first request (as `TvContextMenu.kt:77-90` does) and re-requests whenever the overlay loses focus while visible. | Few files. Stops D-pad leaks for all dialogs. Programmatic steals from Home still happen and are only undone afterwards: the hero/row claim can scroll Home under the scrim, and the two sides can fight if both retry. Depends on the `unverified` programmatic-request behaviour. | S |
| B (recommended) | **Make the screen beneath inactive while the prompt is shown, and trap D-pad in the overlay.** Hoist "prompt visible" to `App`. While the prompt is shown, provide `LocalContentFocusActive = false` and `LocalAutoFocusOnLaunchEnabled = false` around the navigator. Combine the outer value with AND at `MainScreenComponent.kt:138`. Add the `onExit` trap and a retried initial claim to the overlay, as in A. | Uses the shipped project pattern (Bookmarks/Favourites). Stops the steal at its source. On dismiss the gated hero/row claims run and give Home its launch focus. `rememberFocusRequesterOnLaunch` does not read `LocalContentFocusActive` (see `favourites-launch-focus-ignores-rail`), so it needs the second local. Crosses root navigation, Main and a shared component, all in the TV-focus risk area. | M |
| C | **Move the update prompt into a window-level `androidx.compose.ui.window.Dialog`.** | The platform gives key focus to the dialog window, so no in-tree gate is needed. Changes how the scrim and dim look (owner's visual stand). Keys bypass `AppRemoteHotkeyHandler` (`FlowComponent.kt:141-147`). Back moves to `onDismissRequest`. Focus return to the main window after close is `unverified` on this Compose/TV setup. Applies to this prompt only, unless `TvDialogOverlay` users move too. | S |

## 7. Risks

- **The `onExit` trap is in a shared component.** `ApiDomainDialog` has a text field. Cancelling non-directional exits would break IME Done and Back. Cancel directional moves only.
- **Focus return on dismiss.** With B, the gated Home claims fire once the gate lifts; the hero claim is still unresolved at that point. This needs a device check. If Home already holds focus underneath, the overlay's dispose must not leave focus nowhere: `TvDialogOverlay` has no root-level restorer.
- **Timing-dependent bug.** It reproduces only when the update check answers before Home's data. Verification needs a forced `Available` state or a fake interactor, plus a device run (AGENTS.md: TV focus means on-device verification is required).
- **Release hold.** v1.12.3 waits on this fix (`state.md` decision 2026-11-20). An M-size change lengthens that wait.

## 8. Open questions

| # | Question | Recommended answer |
|---|---|---|
| 1 | Close `update-prompt-focus` as a duplicate of this run? | Yes. Record it as merged into this run. |
| 2 | Should the `onExit` trap go into `TvDialogOverlay` for all dialogs, or only into the update prompt? | Into `TvDialogOverlay`, for directional moves only. Every user of it is meant to be modal. |
| 3 | Should the root gate cover only the update prompt, or be a general "root modal" flag for future root overlays? | Only the update prompt, as one boolean in `App`. Generalise when a second root overlay appears. |
| 4 | Is a device repro with a forced `Available` state enough evidence, given that a real new release cannot be staged on the shared emulator? | Yes. Add an instrumented test (host + overlay + a focus-claiming child) and a device smoke test with a debug-forced state. Check that no account data changes. |

## 9. Sources

- Files: `App.kt`, `FlowComponent.kt`, `UpdatePromptOverlay.kt`, `UpdatePromptVM.kt`, `TvDialogOverlay.kt`, `TvContextMenu.kt`, `ApiDomainDialog.kt`, `ResumeDialog.kt`, `MainScreenComponent.kt`, `HomeScreenContent.kt`, `FocusOnLaunchRequester.kt`, `FocusTargetState.kt`, `PlayerSidePanel.kt`, `EpisodesPanel.kt`, `BookmarksScreenContent.kt`, `FavoriteScreenContent.kt`, `docs/architecture/navigation.md`, `docs/architecture/ui-and-compose.md`, `gradle/libs.versions.toml`
- Commits: `43df905d`, `2c1a4a21`, `1b7646af`, `dc15e42b`
- Runs: `update-prompt-focus`, `bookmarks-context-menu-focus-loss`, `home-rows-context-menu-focus-loss`, `favourites-launch-focus-ignores-rail`, `focus-request-helper`, `player-focus-after-panel-close`
- URLs: listed in section 5
