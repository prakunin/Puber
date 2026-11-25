LENS: edge  ROUND: 2  BASE: ef5987ec (incremental c205c62e..18d72ea4)

### F1 [minor] The empty-state «Сброс» focus request also fires on the outgoing tab during a rail tab pick
- Where: app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt:190-195
- Problem: `LaunchedEffect(filter.isActive, contentFocusActive)` restarts every time `LocalContentFocusActive` goes false -> true, not only on a cold open. In `MainScreenComponent.kt` that is `outerActive && drawerState.currentValue != Open`. A tab pick moves the drawer to `HandingOff`, so it becomes true while the outgoing ShowAll is still on screen. `DrawerState.handoffExpectsNewContent` says the outgoing tab must not take the focus aimed at the arriving tab. The effect does not check `LocalContentFocusHandoff.current?.expectsNewContent` and requests focus at once.
- Failure scenario: a stored filter pages empty, «Сброс» takes focus (headerHoldsFocus false). Left opens the rail, another tab is picked. The effect restarts on the outgoing screen and focuses «Сброс»; `contentHasFocus` (FlowComponent.kt:635) becomes true; the new tab's `ContentFocusHandoffEffect` settles on it; the rail closes because of the outgoing «Сброс»; the node is disposed; the arriving tab may be left without focus or focus bounces to the rail.
- Fix: return early when `LocalContentFocusHandoff.current?.expectsNewContent == true`, or latch the request once per mount.

### Unproven
- ShowAllScreenContent.kt:193 single `requestFocus()` attempt in `runCatching`, no frame wait or retry.
- ShowAllScreenContent.kt:113, 403 `headerHoldsFocus` stays true after Up to «Фильтры» and Down to «Сброс» on Empty; later re-entries skip the request.
