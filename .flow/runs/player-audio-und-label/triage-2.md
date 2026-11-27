# Review triage - round 2

Run: player-audio-und-label   Base: cb7fcd45   Reviewed head: d1ed06ad (incremental from 3fab644b)

Sources: review-edge-2.md (no findings), review-tests-2.md (F1 minor), review-codex-2.md (ran: review, adversarial; clean), review-state-path-2.md (no findings), review-antigravity-2.md (skipped: quota exhausted, HTTP 429, resets in about 136h; owner: do not wait). Blind not re-run (incremental).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1 | minor | `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:906` | the `setPreferredTextLanguage(knownLanguageOrNull(...).orEmpty())` change has no test; the lens suspects Media3 would match a preferred "und" to an "und" track, so the change could remove a working fallback | grep: no test reads preferredTextLanguages; state-path-1 and state-path-2 trace that Media3 normalises "und" to undetermined and, with selectUndeterminedTextLanguage = false, a preferred "und" selects nothing, so the line is behaviour-neutral; a blank row already sent "" on base | accept | round 2 minor: untested line, behaviour-neutral by the state-path trace; listed for the owner |

## Rejected codex claims

- Shared normalized identity between a tagged and an untagged side-load makes matchBySourceIdentity ambiguous - unproven, side-loads are keyed by distinct file names.
- Untagged candidates bypass the reused-track-ID guard after a refresh - unproven; needs a refresh that reuses an id for a different untagged track; prepare clears pendingSubtitleTrack (edge-2).

## Next

- no fixes, go to judge; qa round 2 on 2d52a21e: check.full pass, AC-4, AC-5, AC-7 subtitle selection PASS on the box; unproven lines: 2 (edge 1, tests 1)
