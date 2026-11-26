# Review codex, round 1

CODEX: ran: review, adversarial
CONFIRMED:
- [minor] app/src/main/generated/baselineProfiles/startup-prof.txt:20085 - the checked-in startup profile names HttpStack$Companion;->create(Lkotlin/jvm/functions/Function1;); the branch adds a useDoh parameter, so the JVM signature becomes create(Function1, Z) plus a synthetic create$default. The old rule no longer matches the startup factory, so it falls out of the AOT set for prod release until the profile is regenerated (small startup cost, no functional bug). Possibly also in the second profile file. Fix: update the rules to the new signature by hand.
REJECTED:
- getState() reports the saved override or default instead of the pinned domain (Codex P2) - spec decision Q3 keeps it; no pin caller in app/src/main; unproven.
- The pin can change after HttpStack is built or during a domain walk (adversarial, medium) - inferred; setPinnedEndpoint has no production caller; spec accepts the risk (pin set only from configureRuntime() before startKoin); unproven.
VERDICT: changes needed
