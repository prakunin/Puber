# Triage round 1: hermetic-baseline-seams

Reviewed head: c50f506c (base b53bf631). Sources: review-blind-1.md, review-edge-1.md (no findings), review-tests-1.md, review-codex-1.md (ran, review + adversarial), review-antigravity-1.md (ran, clean). qa round 1: qa-report.md.
Each finding was checked against the code by the conductor.

| # | Finding | Sources | Severity | Verdict | Reason / action |
|---|---------|---------|----------|---------|-----------------|
| 1 | Checked-in baseline profiles name HttpStack$Companion->create(Function1) - the old signature; the startup factory falls out of AOT until regenerated | codex | minor | fix | Confirmed in app/src/main/generated/baselineProfiles/startup-prof.txt:20085 and baseline-prof.txt:31590. Regeneration needs a device (owner: none), so update both rules by hand to the new JVM signature(s) (create with the Boolean parameter and the synthetic create$default if the call site uses it; check with javap on the compiled class) |
| 2 | saveCustomDomain Empty/Invalid branches rewritten without a test | tests F1 | minor | fix | Add the two unpinned cases with no-save/no-evict checks |
| 3 | No test that the app client keeps its event listener with useDoh = false | tests F2 | minor | fix | Add the listener assertion to HttpStackTest |
| 4 | Non-DoH DnsCache is unused; KDoc says answers live in it; assertNotNull cannot fail | blind F2 | nit | fix | Cheap: correct the KDoc; replace assertNotNull with an assertion that can fail or drop it |
| 5 | Nothing uses the hooks or sets the pin yet; names suggest hermetic | blind F1 | minor | fix | By design (item 1 of a split; hermetic-baseline-generation wires them). Cheap: KDoc on the hooks and setPinnedEndpoint states they are seams with no production caller until hermetic-baseline-generation |
| 6 | getState()/saveCustomDomain report the default domain or Success while pinned | blind, edge, codex (unproven) | - | reject | Spec decision Q3; no pinned production caller; revisit in hermetic-baseline-generation if a pinned build shows the dialogs |
| 7 | Pin changed after HttpStack is built or during a walk | edge, blind, codex adversarial (unproven) | - | reject | Pin is set only from configureRuntime() before startKoin (design); no production caller |
| 8 | shouldInitializeRuntime()==false with images/activities | edge (unproven) | - | reject | Only item 2's control process returns false and it loads no image (design risk 5) |
| 9 | Pinned saveCustomDomain test uses only valid input; leavesNoWalkInFlight catches one guard placement | tests (unproven) | - | reject | Hypothetical guard moves; current guards are first statements (checked) |

Deferred: none. Accepted: none.
