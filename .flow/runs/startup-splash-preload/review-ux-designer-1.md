# Review ux-designer round 1 (verbatim)

No findings. Code matches design-ux-designer.md and the Q2-amended spec; checked against code only, none seen on a device.

| Delta item | Code | Result |
|---|---|---|
| Fade 250 ms, FastOutLinearInEasing, alpha 1 to 0 via graphicsLayer, no scale or shift | ui/feature/root/component/StartupSplashHost.kt:18, :32-38 | match |
| Fade has its own constant, separate from PanelMotion | REVEAL_FADE_MS in same package as SplashContent; PanelMotion (core/ui/uikit/component/details/VideoItemGridDetails.kt:108) unchanged and not referenced | match |
| SplashContent keeps foundation-only drawing and its sizes | only a modifier passed; file unchanged | match |
| Splash leaves composition after the fade | finishedListener sets splashGone when alpha reaches 0 | match |
| Overlay can't take focus | plain Box, no focus modifier; keys dropped in MainActivity.dispatchKeyEvent via CoveredKeyFilter | match |
| No spinner and no partial Home after the fade | reveal after isLaunchFocusSettled (heroSettled) and no image loading (RevealGate.kt); only the 2.5 s cap can show Loading (AC-5) | match |
| D-pad from start of fade | covered false at fade start; OK pressed under splash doesn't act on release | match; owner confirms on TV |
| FullScreenProgressIndicator, hero constants, Coil crossfade unchanged | not in diff | match |

To check on device later: 1) fade 250 ms ±1 frame, no wordmark pixels after; 2) background #000000 throughout, no blank frame (AC-10); 3) no LoadingTab/Home Loading spinner, empty carousel or rearranging rows after fade start, hero and cards drawn (amended AC-4) unless cap; 4) focus on hero or 3 dp ring on first row card, no jump/scroll during fade; 5) carousel does not page during the fade near the cap; 6) warm start/recreation: wordmark covers again and same fade (AC-10); 7) cap path with network off (AC-5); 8) update prompt: splash lifts at once, Update focused (AC-12); 9) no startup_splash node after the fade (AC-8).

VISUAL: not measured: emulator occupied by another run (newer DB schema), no lease; code review against the design delta only
