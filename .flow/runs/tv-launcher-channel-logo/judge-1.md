# Gate - tv-launcher-channel-logo - round 1

**Decision:** CONCERNS
**Head:** 6196df8b   **Base:** e11d1b66

## Done as asked

The diff (`git -C /home/user1/orca/workspaces/Puber/tv-launcher-channel-logo diff e11d1b66...HEAD`) does the three Scope rows of `brief.md`: logo `android.resource://<pkg>/mipmap/ic_launcher` (`AndroidTvHomePublisher.kt` `channelLogoUri`, `TvProviderGateway.kt` `publishChannel`), channel failure caught with `CancellationException` rethrown and Watch Next still published (`reconcile`, `publish`), `NO_ID` preview skipped in `delete` and the reuse check, prefs format unchanged. Outside the plan: the JVM seam (`TvProviderGateway`, `TvHomeStore`, factory wiring), owner decision of 2026-11-22 in `state.md`. Nothing excluded was touched.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `channelIsCreatedWithAppIconLogo`, `channelLogoUriUsesGivenPackage`; device D0, D1 | Evidence file D0 (base: `Failed to add logo`), D1 (HEAD: no failure line, stored `channel_id=271`, launcher row). tvprovider 1.1.0 deletes the row and throws when the logo is not stored (research.md:17-18), so a stored ID proves the logo was accepted. Provider `channel` table not readable on the box | PASS (indirect, see Concerns) |
| AC-2 | `publishesPreviewAndWatchNextPerProgramOnChannel`; device D2 | Stored state: 5 items with real preview and Watch Next IDs, launcher row shows the items; provider tables not dumped | PASS (indirect, see Concerns) |
| AC-3 | `secondReconcileReusesStoredChannel`, `missingStoredChannelIsRecreated`; device D3 | Stored state byte-identical over two restarts, `channel_id=271` | PASS (indirect, see Concerns) |
| AC-4 | `channelFailureStillPublishesWatchNext`, `channelFailureIsLoggedOnce`, `cancellationFromChannelCreationPropagates`, `recoveryAfterChannelFailureAddsPreviewsWithoutDuplicatingWatchNext`, `storedPreviewIsKeptWhileChannelIsDown`, `programRemovedWhileChannelMissingSkipsPreviewDelete`, `noIdPreviewIsNotTreatedAsExistingProgram`, `clearAccountProgramsDeletesAllStoredIncludingNoIdPreview` | Judge run at 6196df8b: `AndroidTvHomePublisherTest` 14 tests, 0 failures; red run on 5fd0bb06 in `implementation.md` | PASS |
| AC-5 (row) | device D4 | `2026-11-22-tv-launcher-channel-logo-launcher-row.png`, launcher `com.google.android.tvlauncher` | PASS |
| AC-5 (logo look) | owner judgement | stock launcher draws no logo in the row header | NOT CHECKED (manual, below) |
| AC-6 | `check.full` | Judge run at 6196df8b: BUILD SUCCESSFUL; `TvHomeSyncCoordinatorTest` 3/3, `ContinueWatchingSourceTest` 2/2, both files unchanged since base | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| T1 kept-preview arm untested | review-tests-1 F1 | fixed | 6196df8b `storedPreviewIsKeptWhileChannelIsDown`; review round 2 clean |
| B1 orphan channel on logo failure | review-blind-1 F1 | rejected, accepted | research.md:17: the helper deletes the inserted row before it throws; retry per reconcile is logged once each (AC-4) |
| T2 design labels the cancellation test P0 red | review-tests-1 F2 | accepted minor | `implementation.md` records it green on base, kept as a guard |
| T3 AC-1..3 indirect, logo look unproven | review-tests-1 F3 | rejected as code finding, carried | Concerns 1 and Manual checks below |
| T4 production gateway and store not on the JVM | review-tests-1 F4, review-blind-1 F2 | accepted minor | no Robolectric; device D1-D3 exercise both |
| Edge: channel delete may cascade to previews | review-edge-2 Unproven | accepted | that path yields `NO_ID`, covered by the `NO_ID` tests |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | No change in provider call count per program; one extra `publishChannel` attempt per reconcile only while the channel fails. |
| Reliability | Cancellation rethrown; channel failure no longer blocks Watch Next; `NO_ID` never reaches the provider. `PreferencesTvHomeStore.channelId` reads absent as null; old installs never stored an ID (research.md:66). |
| Maintainability | `@SuppressLint("RestrictedApi")` moved to the gateway class with its reason; no dead code left (old prefs constants moved to `PreferencesTvHomeStore`); tests JUnit 5 with hand-written fakes. |
| Security / privacy | No secrets; evidence holds IDs only; `tools/mobile-evidence-audit.sh docs/superpowers/evidence com.kino.puber.stage` -> passed. Release app `com.kino.puber` not touched. |

## Manual checks for the owner

1. AC-5 logo look: on the box, open Home, scroll to the bottom, select "Customize channels" (or Settings > Apps > Puber(Dev) channels); the Puber(Dev) "Продолжить просмотр" channel entry should show the app icon. Judge whether `ic_launcher` looks acceptable under the circular mask (Q1: a dedicated 80dp asset otherwise).

## Must fix

none

## Concerns for the owner

1. AC-1..AC-3 are gated on the app's stored IDs and the launcher row, not on TV provider rows as decided in Q3: the box returns no provider rows to `shell` and refuses `run-as`. "Exactly one channel row of the package" is therefore not directly observed; a duplicate would show as a second Puber row on the launcher.
