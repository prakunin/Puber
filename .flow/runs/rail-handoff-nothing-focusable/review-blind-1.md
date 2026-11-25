LENS: blind  ROUND: 1  BASE: c9e98129

### F1 [minor] The shipped baseline profile still lists the removed beginHandoff(Z) signature
- Where: app/src/main/generated/baselineProfiles/baseline-prof.txt:29811
- Problem: beginHandoff(expectsNewContent: Boolean) became beginHandoff(kind: HandoffKind); the profile rule HSPL...DrawerState;->beginHandoff(Z)Ljava/lang/Long; now matches nothing; the new beginHandoff(L...HandoffKind;), getHandoffKind() and HandoffKind are not in the profile.
- Failure scenario: in release, rail picks, Back and Right from the rail run the new path without AOT until the profile is regenerated.
- Fix: change the line to beginHandoff(Lcom/kino/puber/core/ui/uikit/component/drawer/HandoffKind;)Ljava/lang/Long;, or regenerate with tools/generate-baseline-profile.sh.

### F2 [nit] A test comment the diff did not touch now describes the old behaviour
- Where: app/src/androidTest/kotlin/com/kino/puber/ui/feature/main/component/MainTabPushRailFocusTest.kt:94
- Problem: says a Back handoff over Loading content "would reopen at the 5 s handoff timeout"; now a failed Dismiss ends Closed (reopenRail = reopen && handoffKind == TabPick).
- Fix: say a failed Dismiss leaves the rail closed with focus cleared.

### Unproven
- MainScreenComponent.kt:322 / ContentFocusHandoff.kt bornDuringRequest: a corrective pick back to the outgoing tab (A, B, A) before B's swap recomposes could leave A with bornDuringRequest == null, so the rail reopens after 5 s. Real only if two Center presses land within one frame (auto-repeat).
- MainRailHandoffKindTest pickDuringInitialHandoff_restartsAsTabPick_outgoingTabCannotSettle depends on wall-clock time with ~1 s margin; flaky if awaitRoot(OTHER_TAB_ROOT) takes > ~4 s on the box.
