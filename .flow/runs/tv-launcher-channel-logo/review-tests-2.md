LENS: tests  ROUND: 2  BASE: 9d80b868

No findings.

The incremental diff adds one test, `storedPreviewIsKeptWhileChannelIsDown`, in `AndroidTvHomePublisherTest.kt`. It closes triage item T1. I did not run the suite. I checked the test against the code by reading both.

The test would fail if the `previewId != null -> previewId` arm of `publish()` broke. Its setup is a stored preview, a cleared channel set and `channelFailure` set to an `IOException`. That sends `reconcile` down the path where `channelId` is null and the preview still exists.

- **Stored IDs:** `assertEquals(storedPrograms, store.programs)` fails if that arm returns `NO_ID` or a new ID.
- **Preview survives:** `previews.keys == setOf(previewId)` fails if the preview is dropped.
- **No delete:** `deletedPreviews` being empty fails if the preview is deleted.
- **No republish:** `publishPreviewCalls == 1` fails if a second preview is published.

The test also does not repeat what the neighbouring tests already check. `channelFailureStillPublishesWatchNext` and `programRemovedWhileChannelMissingSkipsPreviewDelete` start without a stored preview.

T2 to T4 stay as triaged: T2 and T4 are accepted and listed for the owner, and T3 is rejected as a code finding and carried to gate 2.
