# Review blind round 1 (verbatim)

LENS: blind  ROUND: 1  BASE: a22c03bc

### F1 [minor] A recreated activity puts the splash back over a screen that will never report ready
- Where: MainActivity.kt:76, PuberVM.kt:94
- Problem: StartupReveal.begin() runs on every onCreate including system recreation (no configChanges: locale, uiMode, density, keyboard/navigation e.g. a Bluetooth remote reconnecting). Voyager stack and screen VMs are restored; a retained VM does not call updateViewState again, so nothing calls markReady(). Only Home handles this (rowsHeldFocus). Details, Player, Settings, Search: only the 2.5 s cap lifts the splash.
- Failure scenario: user on Details or Player, remote reconnects or config change -> activity recreated, splash covers for up to 2.5 s and CoveredKeyFilter swallows D-pad and OK.
- Fix: cover only a real first start: begin() only when savedInstanceState == null, or revealNow() when restored.

### Unproven
- LauncherScreen.kt:28 blank screen after the cap: LauncherScreen no longer draws SplashContent; if routing has not happened 2.5 s after onCreate (slow first Keystore/encrypted-prefs open), the splash fades to an empty Launcher.
- PuberVM.kt:94 early reveal: any non-Home VM publishing a StartupContentState while Home is still settling lifts the splash early.
- StartupKeyFilter.kt:30 stuck key: a key pressed under the splash whose ACTION_UP never arrives (e.g. notification-permission dialog takes focus) stays in pressedWhileCovered; its next press is dropped once.
