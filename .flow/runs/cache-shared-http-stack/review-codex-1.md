CODEX: ran: review, adversarial (model gpt-6-astra, no fallback needed)

CONFIRMED:
- None.

REJECTED:
- [P2 from review, high from adversarial] `HttpStack.kt:19-24`: the DoH client shares the base client's `Dispatcher`, so 64 parent calls waiting on uncached DNS could starve their own DoH queries (circular wait) - unproven. The mechanism is plausible from the dependency bytecode: DoH enqueues its queries on the shared dispatcher. But the default `maxRequestsPerHost` of 5 caps the parent calls per host. Filling the 64 slots with uncached lookups would take at least 13 distinct hosts resolving at the same moment. The app talks to a handful of hosts: KinoPub API, IntroDb, TheIntroDb, TMDB, and a few poster/CDN hosts. The DoH host's own per-host limit is not consumed by parent calls to other hosts. No caller, AC or data in the spec produces that fan-out. Codex itself says the finding is "inferred from the dependency bytecode" and offers no concrete scenario. Both Codex passes reported this same single issue, and no other defects. If the owner wants insurance, a separate `Dispatcher` on `dohClient` (shared pool kept) is a cheap optional hardening, not a defect of this branch.

VERDICT: clean
