# Review triage - round 1

Run: subtitle-sideload-merge   Base: a97ee2b1   Reviewed head: c6ea3789

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial). qa-device round 1 ran in parallel (qa-report.md).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1, blind F2, codex 2 | minor | `vm/SubtitleTrackMerger.kt:141-148`, `vm/SubtitleTrackSelector.kt:54-61` | API `forced` overwrites the manifest FORCED flag on a linked manifest row; the selector's forced filter then drops the row's own track and may pick another same-language track | Read `withApiMetadata` (`isForced = apiTrack.isForced ?: isForced`) and `isSemanticallyCompatibleWith` (`candidate.isForced == track.isForced`); candidate flag comes from `format.selectionFlags` in `PlayerTextTracks.kt`. Scenario holds when API and manifest disagree | fix | Keep the player's flag on `fromManifest` rows; test with a FORCED rendition linked to an API row with `forced=false` |
| 2 | edge F2, codex 1 | minor | `vm/AudioTrackPreferenceResolver.kt:55-60, 80-83` | `findSameSubtitleTrackIndex` matches by full group id + index without checking language, so after a refresh that reorders or drops side-loads the pick can move to another language | Read the rule order: group+index (rule 4) precedes unambiguous language (rule 5) with no language guard; side-load group ids are positional (`n:0`) | fix | Require `sameSubtitleLanguage` (when both known) and the same forced flag for the group+index rule; tests for reorder-with-new-language and dropped-row |
| 3 | edge F3 | minor | `vm/PlaybackMediaItemFactory.kt:38-49`, `model/PlayerUIMapper.kt:41-48` | API link with blank `url` becomes a selectable row and an empty-URI side-load (it used to mean Off in the controller) | Base mapper added every link; base controller treated `url.isEmpty()` as Off. New `isOff` (`PlayerUIModels.kt:49-50`) needs empty language too; the factory maps every link | fix | Drop blank-url links in the factory spec and the mapper; `currentItemHasSideLoads` from the filtered list; tests |
| 4 | tests F2, blind F4 | minor | `vm/PlayerTextTracks.kt:20-80` | `buildSubtitleRows` / `findRendition` (D5 row building, rendition matching, forced OR, playerTrackUri) have no unit test | grep: no test references them | fix | Add `PlayerTextTracksTest` on the JVM with `Format.Builder`, `TrackGroup`, `Tracks.Group` and renditions; if a type cannot be built on the JVM, record it in the plan and name the device check |
| 5 | tests F1 | minor | `vm/PlaybackController.kt:569` | The controller's D9 Off check (`track == null \|\| track.isOff`) has no unit test; only D-2 guards it | Read: `selectSubtitle` is controller-only; VM tests mock the controller | fix | Move the Off/target decision into a small pure function (selector or helper) and test it with an empty-url manifest row |
| 6 | tests F3 | minor | `PlayerVMTrackSelectionTest.kt` | The R5 single-re-send test only covers Off->Off; no call count after a real pick or a repeated reordered report | Read both tests: `keepsSelectedRow_...` asserts state only | fix | Count `selectSubtitle` calls after a real pick and after a repeated reordered report |
| 7 | blind F1 | minor | `PlaybackController.kt:436, 507, 333` | Claims a failing side-loaded subtitle becomes a fatal player error and triggers CDN fallback | Media3 1.11.0 source: `ExoPlayerImplInternal.maybeThrowRendererStreamError` (`:3845-3880`) disables the renderer and logs for `TRACK_TYPE_TEXT`, never throws; after prepare `MediaPeriodHolder.hasLoadingError()` (`:405-418`) swallows sample-stream IOExceptions; `maybeThrowPrepareError` is only called for an unprepared playing period (`:1511`), and lazy side-load children prepare without I/O | reject | Matches spec D15 and the owner's AC-9 evidence decision; worst case is R2 (rebuffer), not fatal |
| 8 | blind F3 | minor | `vm/SubtitleTrackMerger.kt:38-45` | Per-language count rule can hide an API subtitle that differs from the kept manifest set (e.g. 1 forced manifest vs 1 full API: tie -> manifest) | Read: the rule is exactly upstream 9bd835b0 | reject | Owner decision 2026-10-12 (Q3): merge rule 9bd835b0, more tracks wins, HLS on a tie. Reported to the owner at gate 2 as a known consequence |

## Rejected codex claims

- Codex adversarial rated the group-coordinate re-find (#2) as high - downgraded to minor by the codex-reviewer: it needs a refresh that drops or reorders the selected subtitle, and the failure is a wrong language, not a crash or data loss. Fixed anyway (#2).

## Next

- Implementer fix mode for #1-#6 after qa-device round 1 finishes (same worktree). Then check.fast, lens edge and codex on the incremental diff, lens tests (tests change), and qa-device on the affected ACs (runtime code changes: AC-2, AC-4, AC-6).
