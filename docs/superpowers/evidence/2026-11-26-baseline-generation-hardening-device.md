# baseline-generation-hardening: box rail check (M4)

Date: 2026-11-26. Device: TV box 192.168.1.110:5555, package com.kino.puber.stage only.
Build: worktree HEAD ddbe66c7 dev debug APK (main_rail testTag + testTagsAsResourceId on the rail Column);
on-device base.apk sha256 equalled the built APK sha256 (e936fd9a...135b). install-preserve outcome: installed, compatible_replace.
App data saved first with `make DEVICE=192.168.1.110:5555 auth-save`. Session intact: cold launch reached Home, no pairing screen.
Input: D-pad key events at about 1.5 s pace; no scripted API requests; one entry (Series) opened, as an ordinary user would.

## Comparison method

The same steps were run on the tagged build and on a temporary build with MainScreenComponent.kt taken from base b27d8e40
(reverted afterwards with git checkout; tree clean; tagged APK reinstalled at the end). Screenshots were taken but are not
committed because they show account watch history.

## Observations

| Step | Expected | Tagged build | Base build |
|------|----------|--------------|------------|
| Cold launch, wait 15 s, LEFT | Rail opens with labels | Rail open, focus on History pill, Home pill dimmed as selected | Identical |
| DOWN x11 from there (and from History: 4, then 7 more) | Focus moves one entry per key down to Settings | Moved entry by entry, last entry Settings, no stall | not repeated |
| DOWN to Series, CENTER, wait 5 s | Series screen opens, rail collapses to icons | As expected | As expected |
| LEFT from the Series card row | Rail reopens | Rail reopens, focus on Settings (nearest by geometry), Series stays the selected pill; DOWN stays on last entry | Identical |
| Crash buffer after the run | Empty | 0 bytes, app process alive | n/a |

Result: PASS. Rail focus, opening, traversal and return behave exactly as on base; no crash.
Side finding: after LEFT from content the rail focuses the entry nearest by position, not the selected tab, on both builds
(D7's premise that focus is restored to the selected tab does not hold on the box; the tag-scoped check does not depend on it).
