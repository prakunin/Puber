# Review triage - round 2

Run: session-revoked-pairing   Base: b53bf631   Reviewed head: 3968eeaa (incremental from da172bdf); qa round 3 evidence at 79a85925

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review, adversarial), extras: review-antigravity-2.md (ran, clean), review-state-path-2.md, review-ux-2.md. No blind lens in an incremental round.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1, state-path F1, codex C2 | major (regression of fix round 1: an ended account's caches can survive to the next account) | `ui/feature/root/component/App.kt:97-111`, `core/session/SessionEventBus.kt:37` | The end is claimed before the suspending clears run; a cancelled handler skips the rest for good, since a replay only routes | read the handler: claim, then suspend clears under clearWithoutFailing that rethrows cancellation; replay sees claimed=true | fix | claim only after the clears finish, or release the claim on cancellation; move the clears into a non-composable unit so the once-per-end rule and the cancellation case get unit tests |
| 2 | tests F1 | minor | `ui/feature/root/component/App.kt:97-112` | Once-per-end clear in the handler has no test (only the bus primitive) | plan record says the handler is untested | fix (folded into #1, same extraction) | test: emit, handle, accept(uri), replay - target kept; cancel mid-clear, replay - clears run |
| 3 | codex C1 | minor | `core/error/DefaultErrorHandler.kt:35-44`, AC-7 | Fix round 1 (#5) keeps the mirror action for a refusal that did not end the session, which contradicts the approved AC-7 text | AC-7 says Retry only for "ApiError.Unauthorized that did not end the session"; the code now shows the mirror there | fix (AC amendment) | AC-7 amended: a refusal that ends the session routes to Auth; one that does not end it (unlisted 403) keeps the mirror action and the generic message. Recorded in Decisions; analyst updates brief AC-7, test-designer the AC-7 rows |
| 4 | ux-2 F1 | minor | `ui/feature/auth/component/AuthComponent.kt:190-194` | CodeInfoBlockedPreview draws at 2x canvas, no theme, no centring, so it cannot show the safe-area fit the owner judges for AC-10 | Devices.TV_1080p is 1920x1080 dp; preview has no PuberTheme or Box | fix (it is the AC-10 evidence) | preview at spec:width=960dp,height=540dp,dpi=320 inside PuberTheme and a centring Box on the theme background |

## Rejected codex claims

- In-flight refresh resurrecting a session ended by a concurrent user_banned - same race as triage-1 #9, deferred to session-refresh-lock-race; unproven (no evidence the banned server answers 401).
- clearSessionEndReason keystore failure making a pairing look failed - unproven: the same keystore just encrypted both tokens in that flow.

## Next

- Fix round 2 for #1-#4; then lens edge, tests, codex, antigravity, state-path on the incremental diff, ux-designer on the preview; qa on AC-5 (handler change) by check.full (no device-visible change beyond the handler; device re-check optional). Unproven lines: 6 (tests 2, edge 2, codex 2 rejected as unproven).
