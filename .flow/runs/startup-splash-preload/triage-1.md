# Triage round 1: startup-splash-preload

Head reviewed: 4d159b7f (base a22c03bc). Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md, review-ux-designer-1.md (no findings). Each finding checked against the code by the conductor.

| ID | Sources | Severity | Finding | Verdict | Note |
|----|---------|----------|---------|---------|------|
| T1 | blind F1, edge F1 | minor | MainActivity.kt:76 calls StartupReveal.begin() on every onCreate; on recreation with a restored stack nothing marks ready except Home Content, so other screens sit under the splash with keys dropped until the 2.5 s cap | fix | Verified: begin() unconditional, no configChanges in the manifest. A restored stack has nothing to load; reveal immediately on restore (revealNow, or begin only when savedInstanceState == null) while keeping the AC-10 fade behaviour for a true cold/warm start. Add a test. |
| T2 | codex 1, edge F2 | minor | autoResolveWorkingDomain Mutex serialises but NotFound is not shared, so Home repeats a full failing walk after the warm-up's | fix | Verified ApiDomainInteractor.kt:120-151: only a positive isKnownReachable short-circuits. Share one in-flight Deferred result (NotFound included), cleared on completion. Red test first. |
| T3 | codex 2 | minor | Warm-up probe in the process scope can persist a fallback over a domain the user just set/reset in diagnostics | fix | Verified: saveApiDomain/resetToDefault/switchToBuiltInDomain/applyEndpoint do not take the mutex. Cheap: a domain-mutation version captured before the walk; drop the walk's write if it moved. Unit test. |
| T4 | codex 3 | minor | markReady() from a VM can reveal before Compose has issued the screen's image requests (content-link start on Details), so amended AC-6 gives no protection there | fix | Verified PuberVM.kt:93 -> RevealGate.update. Cheap: let a ready mark take effect only after the next frame (so composition has run and image requests exist) before evaluating imagesBusy. Unit test with a frame seam. |
| T5 | tests F1 | minor | No test that the update prompt opening reveals at once via App onOpenChange (amended AC-12) | fix | Extract the onOpenChange reveal call and unit-test it. |
| T6 | tests F2 | minor | autoResolve_concurrentCallersShareOneProbe relies on delay(200) | fix | Replace with an explicit rendezvous; will be reworked together with T2 anyway. |
| T7 | tests F3 | minor | Delta 1 (amended AC-4/6/12, AC-15, AC-13 re-run) has no device evidence; the Coil listener wiring has no test | fix | Unit check that newImageLoader wires StartupReveal.imageActivity; device evidence comes from qa verify after the rebase. |
| U1 | blind unproven | - | LauncherScreen blank after the cap if routing is slower than 2.5 s | reject | Unproven; routing waits only on domain init (prefs + keystore), measured routes in qa round 1 were far below the cap. Revisit if TV traces show it. |
| U2 | blind unproven | - | Non-Home VM publishing StartupContentState while Home settles reveals early | reject | Unproven; Main composes only the selected tab at startup. |
| U3 | blind/implementer note | - | Stuck key if ACTION_UP never arrives | reject | Unproven; costs one dropped press at most; noted by implementer for reviewers. |
| U4 | edge unproven | - | Identity set and one ImageRequest loading twice | reject | Unproven; requests are built per AsyncImage composition. |
| U5 | edge unproven | - | BUTTON_A / SPACE not dropped | reject | Unproven; supported TV remotes send DPAD_CENTER/ENTER; spec decision lists D-pad and OK. |
| U6 | edge unproven | - | Off-screen prefetch keeps images busy until the cap | reject | Unproven; AC-13 re-measure (revealed median) will show it; then a finding. |

Process note: before the fixes, rebase the branch onto master 8428c946 (cache-storage-bounds merged: Room v4, PuberApp.kt Coil memory-cache lines), so the device verify installs a v4 build and no schema downgrade happens. Fix round re-review: edge + codex on the incremental diff, tests lens (tests change), qa on affected ACs.
