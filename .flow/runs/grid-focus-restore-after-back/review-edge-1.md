LENS: edge  ROUND: 1  BASE: e9184b79

No findings.

I checked every boundary on the reviewer.md `also_consider` list and every changed branch. Each was either handled in code or accepted in the spec: R2 (reload through `Loading`), R5, R10 and R11. What I checked:
- **Empty list:** `targetItemId` is null, no requester is attached and the grid is not composed.
- **First load:** the target is the first item.
- **Error, Retry, Content:** this is a fresh composition, so focus goes to the first card.
- **Process death:** the `rememberSaveable` value is consumed when the `Content` branch appears after `Loading`. The grid's scroll state is restored with it.
- **In-place `isSaved` update** (`CollectionDetailVM.kt:130-137`): the item ids stay the same, so nothing is published as pending.
- **Menu-open parking:** the dialog subtree reads nothing from `LocalContentFocusActive`. Its focus uses `rememberFocusRequesterOnLaunch` and `TvDialogOverlay`, which do not depend on that value.
- **Menu close:** the entry effect brings focus back to the target.
- **`markFocusRestored`:** it is guarded by the screen key (`FlowComponent.kt:409-414`).
- **Show all fallback with the menu open:** `inGrid` is false, so the grid fallback does not fire.
- **Show all fallback with the rail open:** `contentFocusActive()` is false, which also stops it.
- **Show all fallback when the list becomes empty:** `restoreId` is null and the function returns before `requestGrid`.

### Unproven
- `app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt:220-226` `focusedCardLeft` is read once, before up to 3 frame-spaced attempts. If the user moves focus back onto a grid card during those frames, `requestGrid()` still fires on a grid that already has focus. This becomes real only with evidence of a D-pad press landing in the roughly 50 ms window after the focused card is removed, plus a device trace showing that `requestGrid` then moves focus off the user's card.
- `app/src/main/java/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContent.kt:117-121` with `FocusTargetState.kt:234-239` The menu's "Watch" action dismisses the menu, which turns content focus back on, and the target card then asks for focus one frame later while the player is being pushed. This becomes real only if the root flow keeps CollectionDetail composed during the push transition and the player's 100 ms launch request loses to it. Manual 7 tested closing the menu with BACK, not with a navigating action.
- `app/src/androidTest/kotlin/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContentFocusTest.kt:60` The AC-5 test removes the focused card while the menu is open. `CollectionDetailVM` never removes items while the screen stays in `Content`: it changes `isSaved` in place or reloads through `Loading`. So the guarded path has no production caller today. It becomes real once an action such as "remove from collection" or an in-place refresh (the idea run `collection-detail-inplace-refresh`) lands.
