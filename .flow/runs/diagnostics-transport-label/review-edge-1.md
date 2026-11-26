# Review edge, round 1 (03265fbc)

LENS: edge  ROUND: 1  BASE: 4c398e07

No findings. Every boundary handled in code or covered by a test:
- Mapper (ConnectionTransportProvider.kt:17-33): null activeNetwork and null capabilities -> None; none of the three -> Other; order Ethernet, Wi-Fi, cellular fixed by when; seven unit tests.
- Permission: ACCESS_NETWORK_STATE in AndroidManifest.xml:11; other exceptions caught and logged at NetworkDiagnosticsVM.kt:59-61 (test onStart_keepsLoadingServer_whenTransportReadThrows).
- Cancellation rethrown at :57.
- Lost update: copy built after withContext returns, on Main (test onStart_keepsCurrentServer_whenTransportArrivesAfterIt).
- Repeated Start: running check at :66 before refreshTransport at :67 (test start_doesNotRereadTransport_whileRunning).
- Main thread: binder call on ioDispatcher; DI passes Dispatchers.IO (NetworkDiagnosticsScreen.kt:33-41).
- DI scope: provider and VM in the same scope; provider holds only the app-wide ConnectivityManager.
- Null transport in the UI: spacer and Text inside state.transport?.let (test transportLine_isAbsent_whenTransportNull); plain Text, not focusable.
- Strings: six strings in values/ and values-en/, %1$s correct.
- Process death: state not saved; re-read on next onStart or Start.

### Unproven
- NetworkDiagnosticsVM.kt:52-63 two reads can overlap and the older can land last; needs a Start press within milliseconds plus a network change. Fix if wanted: keep the read Job and cancel it in refreshTransport().
- NetworkDiagnosticsScreen.kt:30-32 getSystemService could in principle return null; modules.kt:78 makes the same assumption.
