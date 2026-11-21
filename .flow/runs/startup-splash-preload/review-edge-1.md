# Review edge round 1 (verbatim)

LENS: edge  ROUND: 1  BASE: a22c03bc

### F1 [minor] Recreating the activity while view models survive puts the splash over the current screen for the full 2.5 s cap and drops D-pad/OK
- Where: MainActivity.kt:76 (StartupReveal.begin() on every onCreate), PuberVM.kt:94
- Problem: onCreate always calls begin() (ready = false, covers). Only markReady() (new StartupContentState published), Home's LaunchFocusSettled, or the cap lift it. No configChanges, so a config change recreates the activity while VMs and the Voyager stack survive; a VM already showing content publishes nothing new; screens without StartupContentState never mark ready (Settings, player before Content, Home Error). Only Home Content reports again.
- Failure scenario: user in Favorites, Details or Settings changes system language (or config changes while backgrounded), returns: wordmark covers a loaded screen for 2.5 s and arrow/OK are swallowed. Before, the restored screen came back within a frame. AC-10's warm start expects no dwell.
- Fix: begin() only when savedInstanceState == null, or revealNow() when it is not null.

### F2 [minor] When every domain fails, the new lock makes Home's domain check wait for the warm-up's failed check and then run its own full check
- Where: ApiDomainInteractor.kt:123-151, AppStartup.kt:62
- Problem: autoResolveWorkingDomain under a Mutex; a second caller skips probing only when isKnownReachable; NotFound records nothing. HomeVM.loadHome then probes every candidate again sequentially (3 candidates, each up to a 5 s callTimeout).
- Failure scenario: authenticated cold start, KinoPub domains blocked by dropped packets: warm-up check ~15 s, Home waits then spends up to another 15 s; after the cap the user watches the searching spinner up to ~28 s instead of 15 s. Not visible with network fully off (immediate failures), so AC-5's emulator test would not catch it.
- Fix: share the running check: one in-flight Deferred<ApiDomainAutoResolveResult>; callers arriving while it runs await its result including NotFound; cleared when it finishes.

### Unproven
- StartupImageActivity.kt:20 identity set: one ImageRequest instance loading twice at once would mark idle early.
- StartupKeyFilter.kt:6 KEYCODE_BUTTON_A / KEYCODE_SPACE not dropped.
- RevealGate.kt:50 waits for every image request in the process incl. off-screen prefetch; a constant stream holds the splash until the cap.
