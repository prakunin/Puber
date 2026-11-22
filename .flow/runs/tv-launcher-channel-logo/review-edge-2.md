LENS: edge  ROUND: 2  BASE: 9d80b868

No findings.

The incremental diff adds one test, `storedPreviewIsKeptWhileChannelIsDown`, and changes no production code. I read the test against `AndroidTvHomePublisher.reconcile` and `publish`, and ran nothing. The test's expectations match the code:
- `ensureChannel` throws because the fake channel set is empty and `publishChannel` fails.
- `channelId` is null, and `publish` takes the `previewId != null` arm, which returns the stored ID without calling `updatePreview`.
- The stored string stays identical, no preview is deleted, and `publishPreviewCalls` stays at 1.

### Unproven
- `AndroidTvHomePublisher.kt` `publish`: the test clears only the fake's channel set, so the preview survives. On a real device, deleting a channel may cascade-delete its previews. In that case `previewExists` returns false and the stored ID becomes `NO_ID`, which the existing `NO_ID` tests already cover. Evidence that would make this real is a provider run showing preview rows that outlive their channel, which the brief says cannot be read from adb on the box.
