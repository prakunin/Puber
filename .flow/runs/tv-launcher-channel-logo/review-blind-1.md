LENS: blind  ROUND: 1  BASE: e11d1b66

### F1 [minor] A failed logo upload may leave an orphan channel, and every reconcile retries it
- Where: `/home/user1/orca/workspaces/Puber/tv-launcher-channel-logo/app/src/main/java/com/kino/puber/core/tvhome/AndroidTvHomePublisher.kt:60-66` (`ensureChannel`) and `TvProviderGateway.kt` (`publishChannel`)
- Problem: `publishChannel` sets the logo and then calls `helper.publishDefaultChannel`. The test comments suggest the provider throws "Failed to add logo", presumably after the channel row is inserted. I did not check the androidx source for that ordering. When it throws, `store.channelId` is never saved. Each later `reconcile` therefore inserts another channel and logs the failure again. Nothing caps the retries, so previews stay unpublished for as long as the logo keeps failing.
- Failure scenario: On a launcher or device where the `mipmap/ic_launcher` resource cannot be decoded as a bitmap, such as an adaptive icon, or a provider that rejects the logo, every home refresh adds a channel and a log line. The user ends up with duplicate "Continue watching" channels, or none that fills.
- Fix: Verify the helper's behaviour. If the channel is inserted before the logo is written, catch the logo failure inside `publishChannel`, create the channel without a logo, and store its ID. Alternatively, remember the failure and skip the logo on retry.

### F2 [nit] The tests cannot check that the logo works
- Where: `/home/user1/orca/workspaces/Puber/tv-launcher-channel-logo/app/src/test/kotlin/com/kino/puber/core/tvhome/AndroidTvHomePublisherTest.kt`
- Problem: The tests only check that a URI string reaches the fake gateway. Whether the resource is valid and the provider accepts it is checked only by the device evidence file. `PreviewChannelTvProviderGateway`, including the `setLogo` branch, has no unit test.
- Failure scenario: A wrong resource name or type passes every test and fails only on a device.
- Fix: Keep the device check as the acceptance evidence. No code change is needed.

I found no other defects. Cancellation is rethrown correctly. The stored-program format is unchanged, and the `NO_ID` preview entries are skipped on delete and on the exists check.

### Unproven
- `AndroidTvHomePublisher.kt:60-66` `channelId` store getter treats a missing key as null, where the old code used the `-1` sentinel. This is equivalent unless an old install stored `-1`, and no evidence suggests it did.
