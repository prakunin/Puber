LENS: blind  ROUND: 1  BASE: 35bfbbec

No findings.

I read the whole of `/home/user1/orca/workspaces/Puber/showall-focus-restore-hardening/app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt`, the androidTest and the new unit test. These were the strongest candidates; each one fell apart when checked:

- **Focus landing on the old card after a re-target.** `reconcile` changes `restoreId` and then waits for a frame. The worry was that the request runs before recomposition, while the previous target card still holds `restoreFocus`. It doesn't: the coroutine resumes on `AndroidUiDispatcher`, which runs it after that frame's recompose and apply. By then the requester is on the new card.
- **`pendingEntry` staying true.** This happens when the restore is aborted because content focus is inactive (drawer open), or when the grid fallback fails. Later reloads then re-run the restore. Every attempt is still gated by `contentFocusActive()`, and the flag clears the first time focus enters the grid. That flag-clearing path already existed before this change, so nothing new is exposed.
- **Grid fallback with no target.** When `restoreId == null`, `reconcile` returns without calling `requestGrid`. That only happens with an empty list, where nothing can take focus, and the restore runs again when items arrive.
- **The launch-focus requester competing on re-entry.** `rememberFocusRequesterOnLaunch` saves its flag under the screen key, so it does not fire again on re-entry. Any remaining overlap comes from code outside this diff.
- **The changed androidTest assertions.** `>= DeepRows * GridColumns` matches 5 Down presses from index 0 in 3 columns. The `none { isDisplayed() }` check passes trivially if card 1 has been disposed, which still means "off the viewport". The plain `var shown` really is read only by the restored composition, because `StateRestorationTester` runs the same content lambda again.
- **Unit test mechanics.** The fake clock cancels the job and restarts it from inside `withFrameNanos`, and `ensureActive` stops the stale attempt. The `memory` self-reference inside the `Harness` initializer is only read later, so it is safe. Each scenario would fail if its decision branch were reversed.
