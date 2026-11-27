# Gate - subtitle-und-preferred-fallback - round 1

**Decision:** CONCERNS
**Head:** be73cdee   **Base:** 874b049f

## Done as asked

`git -C /home/dev/orca/workspaces/Puber/subtitle-und-preferred-fallback diff 874b049f...HEAD` matches brief.md Plan steps 1-3 and Q1 = A: helper `preferredTextLanguage` (`model/SubtitleLanguage.kt:19-24`), the one call site (`vm/PlaybackController.kt:908`, import `:50`, comment `:899-901`), two tests (`SubtitleTrackIdentityTest.kt:92-105`), device evidence. The value passed to Media3 is unchanged from base. Nothing outside the brief's scope.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `preferredTextLanguage_isEmpty_forUndeterminedAndBlank` | judge's check.full at be73cdee: `TEST-...SubtitleTrackIdentityTest.xml` 15 tests, 0 failures, both new cases present; red on base by compile failure (implementation.md) | PASS |
| AC-2 | `preferredTextLanguage_keepsKnownLanguage` | same run | PASS |
| AC-3 | code read | grep of `app/src/main`: the only `setPreferredTextLanguage` is `PlaybackController.kt:908` with `preferredTextLanguage(track.language)` | PASS |
| AC-4 | M1 on box 192.168.1.110:5555 | `docs/superpowers/evidence/2026-11-27-subtitle-und-preferred-fallback-device.md` (ce8deff2 APK; be73cdee is comments only): language row renders, Off renders none; no "und" row in the title, re-pick step skipped; `tools/mobile-evidence-audit.sh` passed | PASS |
| AC-5 | check.full | run by the judge at be73cdee (qa-report predates the fix commit): exit 0, baseline-profile tooling all PASS | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| KDoc/comment overclaimed "shows nothing" | blind F1, codex | fixed | be73cdee (wording now: prefers no language, default or forced selection may still apply) |
| Tests pin the helper, not the controller wiring | blind F2, tests F1 | rejected, accepted | owner Q2 = no instrumented case; test-design R3; residual listed under Concerns |
| Device smoke does not reach the "und" branch | tests F2 | rejected, accepted | qa-report AC-4 says "neighbouring path only"; no title with an "und" row known; AC-4 as written was exercised |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | No change: same expression, now behind a pure function. |
| Reliability | Behaviour-neutral; null and blank input covered by AC-1. |
| Maintainability | Follows AGENTS.md; no duplicated helper; `knownLanguageOrNull` import removed where unused. |
| Security / privacy | No secrets or account data; evidence audit passed, no screenshots kept. |

## Manual checks for the owner

None.

## Must fix

None.

## Concerns for the owner

1. The call site has no automated guard (test-design R3): reverting `PlaybackController.kt:908` to `track.language` keeps the suite green. `playback-controller-split` edits the same file; its review should re-check this line.
2. What Media3 shows for a preferred "" with an "und" or default-flagged text track rests on the 1.11.0 bytecode reading (test-design R4); no device run reached an "und" row.
