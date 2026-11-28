# Review triage - round 1

Run: dependency-patch-bump-nov   Base: b1b3c51a   Reviewed head: 16588c3f

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial), review-antigravity-1.md (skipped: quota reached, 429 RESOURCE_EXHAUSTED).
ux-designer and state-path-reviewer: not matched (no Compose layout, no ViewModel or focus code in the diff).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests (codex raised and rejected the same point) | minor | `app/src/main/java/com/kino/puber/ui/feature/device/settings/DeviceSettingsContent.kt:210-218` | AC-7 cites the `currentStateFlow` lines as the session-log toggle, but they are the share-chooser focus-restore wait; qa exercised the toggle and relaunch, not this effect, and reported AC-7 PASS | Read the code: `LaunchedEffect(restoreShareFocus)` collects `lifecycle.currentStateFlow` twice (leave RESUMED, return to RESUMED); qa-report states the chooser did not appear on the box. The 2.11.1 change (same StateFlow instance per call) does not change what a fresh `first {}` collector sees, so no regression scenario starts from the code; the gap is in the evidence, not the build | fix (evidence) | The AC-7 clause on the share-chooser focus return is carried to the gate as NOT CHECKED, not PASS, and listed as a manual check for the owner (Settings > Developer > Share log, close the chooser, focus returns to the Share row). No code change, so no re-review |

## Rejected codex claims

- Share-log chooser path unverified (P2 / medium adversarial) - same point as #1; codex-reviewer rejected it as unproven. Handled in #1 as an evidence correction, not a code defect.
- Compare Share log on base and candidate before shipping - not required by the spec; the box shows no chooser on either build, independent of the bump.

## Next

- No code fixes; go to judge. AC-7 share-focus clause NOT CHECKED (manual for the owner). Unproven lines: 5 (blind 2, edge 2, tests 1).
