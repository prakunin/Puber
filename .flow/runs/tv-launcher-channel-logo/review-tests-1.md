LENS: tests  ROUND: 1  BASE: e11d1b66

I did not re-run the suite. I relied on the implementer's and QA's recorded `check.full` and per-test results (13/13 green). I did not check them independently.

### F1 [minor] The branch that keeps an existing preview while the channel is down has no test
- Where: `app/src/main/java/com/kino/puber/core/tvhome/AndroidTvHomePublisher.kt`, in `publish`, the `previewId != null -> previewId` arm of the `when`. The test file is `app/src/test/kotlin/com/kino/puber/core/tvhome/AndroidTvHomePublisherTest.kt`.
- Problem: No test starts with a real stored preview and a failing `publishChannel`. Every channel-failure test starts with empty state, so the preview is always `NO_ID`. The implementation record says "a stored preview is kept untouched, not updated, until the channel returns", but nothing checks it.
- Failure scenario: If someone changes that arm to call `updatePreview` with a null channel, or to return `NO_ID` (which would orphan the preview row and lose its stored ID), every test stays green.
- Fix: Add one test. Reconcile once with the channel working, then set `channelFailure`, clear `gateway.channels` and reconcile again. Assert that `store.programs` still holds the original preview ID and that `previews` still has that entry.

### F2 [minor] `cancellationFromChannelCreationPropagates` is labelled "P0 red" in the design but was green on base
- Where: `/mnt/data/projects/PetProjets/Puber/.flow/runs/tv-launcher-channel-logo/test-design.md` (traceability table); `implementation.md` (red run).
- Problem: The design lists the test as P0 red. The implementation record shows it green on base and says it is kept "as a guard", so the intent is recorded. The design row was never updated to `guard`, though, so the two documents disagree. The test itself does catch a swallowed `CancellationException`.
- Failure scenario: None in the code. The risk is that a reader of the design table believes this test went red on base.
- Fix: Change the design row's label to `guard`.

### F3 [minor] AC-1, AC-2, AC-3 and AC-5 are not verified at the provider level, and the logo's persistence is not proven
- Where: `/home/user1/orca/workspaces/Puber/tv-launcher-channel-logo/docs/superpowers/evidence/2026-11-22-tv-launcher-channel-logo-verify-device.md`
- Problem: The brief requires provider rows to gate AC-1 and AC-2: exactly one channel row with the right display name, one preview row per item linked to the channel, and one `WATCH_NEXT_TYPE_CONTINUE` row per item. The evidence could not read those tables. It substitutes the app's own stored IDs and a launcher header crop. The "exactly one channel" check and the "no second channel on restart" check (AC-3) are therefore indirect.
- QA also saw an E-level tvprovider line, "Logo for preview channel (ID:-1) not found", with a `FileNotFoundException` on the first creation. The channel was still created. That is consistent with the real provider accepting the logo, but it does not prove the logo bitmap was stored.
- The stock launcher draws no logo, so the owner cannot judge the icon (AC-5 "NOT CHECKED").
- Failure scenario: A logo that is silently not stored, or a duplicate channel row, would pass this evidence.
- Fix: Add one manual check to the owner gate. Look for the icon in Settings or the launcher "customize channels" screen, or run a read with a permission that can see the provider. Otherwise state in the gate summary that AC-1 and AC-3 rest on the stored IDs and the launcher row.

### F4 [minor] The production gateway and store have no JVM tests
- Where: `app/src/main/java/com/kino/puber/core/tvhome/TvProviderGateway.kt`, in `PreviewChannelTvProviderGateway` and `PreferencesTvHomeStore`.
- Problem: Tests only use fakes. The `setLogo` wiring, the `if (logoUri != null)` branch, the `channelId` null-versus-absent read that replaced the `-1` sentinel, and the `TvHomePublisherFactory` wiring are covered only by the device run. The record names this gap, and the device run covers the happy path.
- Failure scenario: A regression in the store, such as a wrong key or a `contains` check that is always true, passes the unit suite. It would show up only on a device.
- Fix: Accept it, since there is no Robolectric. Alternatively, add a small store test with a fake `SharedPreferences`.

### Unproven
- `AndroidTvHomePublisher.kt` (`publishChannel` returns an ID of `NO_ID` or 0) - nothing shows that `publishDefaultChannel` returns such a value, so no handling is needed until it does.
