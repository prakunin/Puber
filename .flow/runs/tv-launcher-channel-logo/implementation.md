# Implementation record: tv-launcher-channel-logo

Worktree /home/user1/orca/workspaces/Puber/tv-launcher-channel-logo, base e11d1b66. No device work done.

## Commits

| sha | Subject | What | Tests | check.fast |
|---|---|---|---|---|
| 5fd0bb06 | Put the TV provider and stored IDs behind seams so the Android TV publisher runs on the JVM | Seam with the old behaviour: `TvProviderGateway` (+ `PreviewChannelTvProviderGateway`), `TvHomeStore` (+ `PreferencesTvHomeStore`), `TvProgramFields`, pure `channelLogoUri` in `core/tvhome`; the publisher takes them by constructor, the factory wires them. Channel is still created without logo and a failure still propagates. | `AndroidTvHomePublisherTest`: the 6 guard rows (`channelLogoUriUsesGivenPackage`, `publishesPreviewAndWatchNextPerProgramOnChannel`, `secondReconcileReusesStoredChannel`, `missingStoredChannelIsRecreated`, `storedProgramsFormatIsUnchanged`, `cancellationFromChannelCreationPropagates`) green | green |
| d7fbb00d | Give the TV home channel the app icon as logo and keep Watch Next published when the channel fails | Logo `android.resource://<pkg>/mipmap/ic_launcher`; `reconcile` catches channel failure (rethrows `CancellationException`), logs once via `onFailure`, publishes Watch Next only; `NO_ID` preview sentinel skipped in delete and reuse; stored format unchanged. | the 7 red tests below added; all 13 green | green |

## Red run (on 5fd0bb06, full test file)

```
red: channelIsCreatedWithAppIconLogo - AssertionFailedError (logos list holds null, not the icon URI)
red: channelFailureStillPublishesWatchNext - IOException escapes reconcile before any Watch Next call
red: channelFailureIsLoggedOnce - IOException escapes reconcile, nothing logged by the publisher
red: recoveryAfterChannelFailureAddsPreviewsWithoutDuplicatingWatchNext - IOException escapes the first reconcile
red: programRemovedWhileChannelMissingSkipsPreviewDelete - IOException escapes the first reconcile
red: noIdPreviewIsNotTreatedAsExistingProgram - IOException escapes the first reconcile
red: clearAccountProgramsDeletesAllStoredIncludingNoIdPreview (P1 guard in the design) - IOException escapes the first reconcile
green on base: cancellationFromChannelCreationPropagates - the base has no catch, so cancellation already escapes; kept as a guard against the new catch swallowing it
```

Two of the reds (`programRemoved...`, `noIdPreview...`) fail on the base by the escaping IOException of their setup step, not on the `deletePreview(-1)` / `getPreview(-1)` assertion itself, because the `NO_ID` state can only be produced by a failed channel. They still discriminate the final guard: they pass only with the decouple, and the `-1` assertions are in them.

## Deviations

- Gateway uses `channelExists/previewExists/watchNextExists` Booleans instead of `getChannel/getPreview`; `appLink` is built inside the production gateway (needs `Intent`), so `publishChannel(displayName, logoUri)` has no `appLink` argument.
- `logoUri` is nullable in the interface only so commit 1 could keep the old behaviour; the publisher always passes it after commit 2.
- The failure is logged by the publisher through an injected `onFailure` (default `Unit.log(..., tag)`); the coordinator's "Failed to publish TV home programs" no longer fires for a channel failure.
- Plan checkboxes: no plan file (size S).

## Files outside the plan

`TvProviderGateway.kt` (new), `TvHomePublisherFactory.kt` (wiring), `AndroidTvHomePublisherTest.kt` (new), all in `core/tvhome`.

## Check

- check.fast: BUILD SUCCESSFUL.
- check.full (`testDevDebugUnitTest :app:detektAll :app:lintDevDebug`): BUILD SUCCESSFUL in 6m; `TvHomeSyncCoordinatorTest` and `ContinueWatchingSourceTest` unchanged and green.

## For reviewers

- The production `PreviewChannelTvProviderGateway` and `PreferencesTvHomeStore` are untested on the JVM; the device rows D1-D3 gate them (logo accepted by the real provider, store round trip). `PreferencesTvHomeStore.channelId` returns null when the key is absent, replacing the old `-1` sentinel read.
- When the channel fails and a stored preview still exists, it is kept untouched (not updated) until the channel returns.
- `ic_launcher` opacity is the owner's AC-5 judgement.

Fix round 1, T1: added storedPreviewIsKeptWhileChannelIsDown (tests only). Guard on the unchanged code; it fails when the keep arm returns NO_ID (mutation checked), passes otherwise. 13 -> 14 tests green.
