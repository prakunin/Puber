# Review triage - round 1

Run: startup-reveal-gate-on-slow-tv   Base: 9158cf82   Reviewed head: 854a4981 (3289825e only restates AC-7 in the spec)

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md (check.full green: 2466 tests, 0 failed; detekt, lint green), review-state-path-1.md (extra). Codex: skipped, usage limit until 2026-11-25 07:38 (owner, 2026-11-23: do not wait for Codex).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1 | minor | RevealGate.kt:98, :123 | A READY reveal can now come with in_flight > 0, and img_idle on the startup line can predate ready or be missing; the meaning change is undocumented | Read: onImagesIdle reported only while !revealedOnce; reveal waits only for held | fix (cheap): document the meaning of img_idle and in_flight for cause ready and grace where the startup line fields are documented (KDoc of StartupMarks / telemetry docs); no behaviour change | open |
| 2 | blind F2 | minor | StartupReveal.kt:60-63 | No JVM test covers the production graceHook wiring | Spec marks it UNTESTABLE on the JVM (Handler on the main looper); round-1b device evidence shows cause grace in 14 of 15 S1 starts | reject: the wiring is proved on the device, as the spec and plan state; a JVM seam would need Robolectric, which the project does not use | rejected |
| 3 | tests F1 | minor | evidence :130; spec Traceability :143 | The restated AC-7 has no verdict in the evidence and the Traceability row still states the old AC-7 | Read both files | fix: qa adds an AC-7 (restated) verdict row from the round-1b data (no new device work); test-designer restates the Traceability row; conductor copies it into the spec | open |
| 4 | tests F2 | minor | evidence :108, :129 | AC-6 holds only while ready <= cap - G (2200 ms); set 1 shows cap reveals for ready 2323-2470 | Read the set-1 table | fix: qa states the limit in the AC-6 verdict; reported to the owner at handover as a known limit | open |

## Unproven, not acted on

- edge/blind: onGrace sets forcedCause = GRACE after a READY reveal; no reader today.
- edge: grace fires while the app is backgrounded during the splash; no such start in the evidence.
- edge: onRevealed in_flight on a grace reveal counts post-ready requests (spec decision "unchanged").
- tests: forcedCause == null check before arming G has no test; a queued G would be a no-op.

## Rejected codex claims

- none (codex not run)

## Next

- Fix round 1 (docs and evidence only): implementer #1, qa-device #3/#4 (no device), test-designer #3; then lens edge on the incremental diff (codex skipped at its limit).
