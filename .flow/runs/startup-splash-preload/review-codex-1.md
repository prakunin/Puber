# Review codex round 1 (verbatim)

CODEX: ran: review, adversarial (model gpt-6-astra, static review only)

CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/domain/interactor/api/ApiDomainInteractor.kt:120-136 - The mutex only serializes autoResolveWorkingDomain. NotFound is not cached, because only a successful isKnownReachable is. - Scenario: an authenticated cold start with no endpoint reachable. AppStartup.warmUp (AppStartup.kt:62) walks all candidates and gets NotFound. HomeVM (HomeVM.kt:279) then takes the lock and repeats the whole walk, so Home waits two sequential sets of timeouts before it shows its error state. Before this branch it waited for one walk. Fix: share the in-flight result with waiters, NotFound included, or let HomeVM reuse the warm-up's outcome.
- [minor] app/src/main/java/com/kino/puber/domain/interactor/api/ApiDomainInteractor.kt:135-144 - The warm-up probe now runs in the process-owned AppStartup scope. saveApiDomain/resetToDefault/switchToBuiltInDomain/applyEndpoint do not take autoResolveMutex. - Scenario: flaky network, probe walk still running after the 2.5 s cap; user opens domain diagnostics and switches or resets the domain; the old walk then finds a reachable fallback and persists it over the user's choice. Not a blocker: narrow window, same race existed with HomeVM's probe, now more exposed because the probe is not tied to a screen scope. Fix: capture a domain-mutation version before the walk and drop the result if it moved.
- [minor] app/src/main/java/com/kino/puber/core/ui/PuberVM.kt:93 and core/startup/RevealGate.kt (update) - markReady() fires when the VM publishes a StartupContentState, before Compose has consumed it and started its image requests. With nothing in flight, ready && !imagesBusy uncovers the splash at once and latches revealedOnce. - Scenario: cold content-link start lands on DetailsScreenState.Content; splash reveals before the artwork requests exist, so the images-settled part of amended AC-6 gives no protection on that path. Home and Auth less exposed (Home waits for hero settle). Impact cosmetic: pop-in during the fade, decode during first interaction.

REJECTED:
- None. All four Codex findings confirmed (two distinct defects raised in both passes); severities lowered (P2 / high -> minor) because each depends on offline/flaky networks or a narrow timing window.

VERDICT: changes needed
