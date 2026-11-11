# Device evidence: grid focus restore after Back

Slug: grid-focus-restore-after-back. Spec: `docs/superpowers/specs/2026-11-11-grid-focus-restore-after-back.md`. Test design: Manual 2-7 and the instrumented rows. Round 2 first, round 1 (HEAD 5ea2899e) after it.

## Round 2

### Build and device

| Item | Value |
|---|---|
| Worktree HEAD | 3e1530f0 (base e9184b79) |
| APK | `app/build/outputs/apk/dev/debug/app-dev-debug.apk`, sha256 `eefd10f3886a3b78195f369c0e94f0dc16c6729dc6058233830b3cf191a3cd62`, 1.10.0-dev; the on-device `base.apk` has the same sha256 |
| Install | `tools/android-apk-install-preserve`, outcome `installed` (`compatible_replace`, data preserved) |
| Device | `emulator-5554` (AVD `puber_tv_36`, Android 16, en-US); the emulator was not running at the start, started with `-no-window` |
| Method | D-pad keyevents (DOWN 20, RIGHT 22, LEFT 21, OK 23, BACK 4, long OK `--longpress 23`), 1 s between keys, focused node bounds `[l,t][r,b]` read after each step, screenshots at key steps. "SAME" = identical focused bounds |
| Preparation | Collections tab enabled in Settings > Navigation for the run and switched off afterwards (screenshot checked, rail back to the original sections). A "new version" dialog dismissed with BACK. No account data changed |
| Lease | 5400 s lease, released. The emulator process died once at the end (hanging QEMU threads) after all checks; it was restarted, the app and login were intact, and the Collections switch-off was done after the restart |

### Instrumented runs (`make DEVICE=emulator-5554 itest TESTS=<class>`)

Red run: `CollectionDetailScreenContent.kt` restored from e9184b79, then restored with `git checkout`; `git status` shows no diff for it.

| Test (CollectionDetailScreenContentFocusTest) | Base content | HEAD 3e1530f0 |
|---|---|---|
| `collectionDetailFocusReturnsToTheFocusedCardAfterReentry` | FAIL (`ComposeTimeoutException` 5000 ms, test line 56) | PASS |
| `collectionDetailHostEntryBeforeTheFirstRestoreAttemptFocusesTheFocusedCard` | FAIL (same exception, test line 76) | FAIL, 3 of 3 runs (same exception, test line 76) |
| `collectionDetailFocusesTheFirstCardOnFirstLoad` | FAIL (test line 84) | PASS |
| `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` | PASS (guard) | PASS |

| Class on HEAD | Result |
|---|---|
| CollectionsScreenContentFocusTest | 1 of 1 PASS |
| home.component.HomeCollectionBackFocusTest | 2 of 2 PASS |

### Manual checks on the worktree APK (3 repeats per origin)

| Manual | Origin | Sequence | Observed | Result |
|---|---|---|---|---|
| 3 (AC-2) | Collections | detail: DOWN x2, RIGHT, OK (Details, no change), BACK | card `[662,415][1259,751]` before and after, 3 of 3 | PASS |
| 3 (AC-2) | Home Collections row | same | card `[662,415][1259,751]` before and after, 3 of 3 | PASS |
| 4 (AC-3) | both | fresh detail; final BACK of the cycle | first card `[32,144][630,480]` on load, 6 of 6; BACK returns to the opener (Collections `[742,324][1299,637]` x2 and `[1331,324][1888,637]`, Home `[717,523][1250,823]` x3), 6 of 6 SAME | PASS |
| 7 (AC-5, D16) | both | after Manual 3's BACK: long OK on the card, 2.5 s, BACK | menu button "Watch movie" `[480,398][1440,494]` focused (screenshot checked), 6 of 6; after BACK the same card `[662,415][1259,751]` focused, 6 of 6 | PASS |

Screenshots stay in the session scratchpad, not committed (account titles visible).

## Round 1 (HEAD 5ea2899e, kept for the unchanged rows)

### Build and device

| Item | Value |
|---|---|
| Worktree HEAD | 5ea2899e (base e9184b79) |
| APK | `app/build/outputs/apk/dev/debug/app-dev-debug.apk`, sha256 `cec784cb58b959856c4dd281347351e6a7e782096412512b13d373500510a82f`, 1.10.0-dev |
| Install | `tools/android-apk-install-preserve`, outcome `installed` (`compatible_replace`, data preserved) |
| Device | `emulator-5554` (AVD `puber_tv_36`, Android 16, locale en-US) |
| Method | D-pad keyevents (DOWN 20, RIGHT 22, LEFT 21, OK 23, BACK 4), 1 s between keys, focused node bounds read after each step, screenshots at key steps. "Same card" = identical focused bounds before and after |
| Preparation | Bookmarks and Collections tabs enabled in Settings > Navigation for the run; both switched off afterwards (screenshot checked: rail back to the original sections). A "new version" dialog was dismissed with BACK. No account data changed |
| Lease | The 600 s lease expired during the manual run (`release` answered `resource_already_unlocked`); no other agent was observed on the device |

### Instrumented runs (`make DEVICE=emulator-5554 itest TESTS=<class>`)

| Class | Result |
|---|---|
| CollectionDetailScreenContentFocusTest | 2 of 4 FAIL, same two on a rerun: `collectionDetailFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry` (`ComposeTimeoutException` 5000 ms, test line 67), `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` ("Watch movie" `Focused = false`, test line 104). PASS: `...FocusReturnsToTheFocusedCardAfterReentry`, `...FocusesTheFirstCardOnFirstLoad` |
| CollectionsScreenContentFocusTest | 1 of 1 PASS |
| HomeCollectionBackFocusTest | 2 of 2 PASS |
| ShowAllScreenContentFocusTest | 5 of 5 PASS |
| ShowAllRailCloseFocusTest | 5 of 5 PASS |
| HistoryScreenContentTest | 20 of 30 PASS, 10 FAIL. Every failure is `Expected exactly '1' node` for a hard-coded Russian string ('Повторить', 'Подробнее', 'История', ...) on an en-US device; none is a focus test; the diff touches no History code |

### Manual checks on the worktree APK

3 repeats each; bounds are `[l,t][r,b]` of the focused node.

| Manual | Sequence | Before | After | Result |
|---|---|---|---|---|
| 2 (AC-1) | Collections: DOWN x3, RIGHT, OK, BACK | card `[742,324][1299,637]` | same, 3 of 3 | PASS |
| 4 (AC-3) | Fresh CollectionDetail from Collections | n/a | first card `[32,144][630,480]` focused on load, 3 of 3 (Collections first open too) | PASS |
| 3 (AC-2, Collections origin) | In CollectionDetail: DOWN x2, RIGHT, OK (Details), BACK | card `[662,415][1259,751]` | same, 3 of 3 (base: no focus) | PASS |
| 7 (AC-5) | Long OK on a card, wait 2.5 s | card | menu button `[480,426][1440,522]` ("Watch movie") focused, 3 of 3; BACK returns to card `[662,415][1259,751]`. Items did not change in this run | PASS |
| 4 (AC-3, Home origin) | Home Collections row, second card, OK | opener `[717,777][1250,1077]` | fresh detail: first card `[32,144][630,480]`; BACK: opener again, 3 of 3 | PASS |
| 3 (AC-2, Home origin) | Same detail: DOWN x2, RIGHT, OK (Details), BACK | card `[662,415][1259,751]` | same, 3 of 3 | PASS |
| 5 (AC-8) Bookmarks | DOWN, RIGHT, OK, BACK; column 1: LEFT (rail), RIGHT | `[742,392][1299,705]`; `[152,392][710,706]` | same, 3 of 3 each | PASS |
| 5 (AC-8) History | same | `[742,421][1275,721]`; `[152,421][685,721]` | same, 3 of 3 each | PASS |
| 5 (AC-8) Show all | card, OK, BACK, LEFT/RIGHT | n/a | not run: no Show all card found on Home within the Trending and Collections rows (rows page on) | NOT CHECKED |
| 6 (AC-6) | Collections: column-1 card in a lower row ("Алкоголизм"), LEFT (rail), 2.5 s, RIGHT | `[152,324][710,638]` | same card, no row shift, 3 of 3 | PASS |

### Screenshots

Kept in the session scratchpad, not committed (account titles visible).

## Review fix round 1 (HEAD 4efeed73, emulator-5554)

Method: `make DEVICE=emulator-5554 itest TESTS=<FQCN>` under a lease, result XML copied after each run. Tests-only change; production unchanged since 3e1530f0.

### CollectionDetailScreenContentFocusTest on HEAD

3 tests, 0 failures. `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` passed: it sends `KEYCODE_BACK` with `Instrumentation.sendKeyDownUpSync`, the menu closed through its BackHandler and the neighbour card took focus (`assertIsFocused`). Two runs, both 3/3.

### HistoryScreenContentTest (triage #3), 30 tests

| Build | Passed | Failed |
|-------|--------|--------|
| HEAD | 20 | 10 |
| base (production files restored from e9184b79, then reverted) | 20 | 10 |

The failing set is identical on both builds:

- completedRowStartOverActionCarriesExplicitPlayerMode
- contextMenuDisablesDeleteWhileMutationIsPending
- contextMenuKeepsDeleteVisiblyUnavailableDuringPagination
- contextMenuKeepsDeleteVisiblyUnavailableDuringRefresh
- contextMenuMapsSupportedActionsAndClose
- contextMenuShowsDisclosureAndOmitsBroaderDeletionActions
- emptyRetainedContentAfterReconciliationFailureShowsRetryableError
- fallbackDetailsContextMenuOmitsPlaybackActions
- nextPageFailureWaitsForExplicitRetryAndKeepsCardFocus
- nonEmptyHistoryRendersContentTitle

Base run: `CollectionDetailScreenContent.kt` and `ShowAllScreenContent.kt` (the only production files in the diff) restored with `git show e9184b79:<path>`, then `git checkout --`; `git status --short` is empty afterwards. The 10 failures predate this run and touch no changed code.
