# Puber addendum: final-judge

## Non-functional checklist for the gate

- Performance: recomposition scope, main-thread work, allocation in hot paths,
  cache growth, startup cost; on the perf track the measured before and after
  numbers.
- Reliability: error and empty states, cancellation, process death.
- Maintainability: the conventions in `AGENTS.md` (Architecture Gotchas), no
  duplicated helper, no dead code left (for example a file the change made
  unused), file responsibilities intact.
- Security and privacy: no secrets or account data in code, logs or evidence;
  the evidence file passed `tools/mobile-evidence-audit.sh`.
- TV: focus, D-pad, Back, overscan.

## Evidence

Device evidence is `{wt}/docs/superpowers/evidence/<date>-<slug>-device.md`,
written by `qa-device`. An AC that needs a device and has neither evidence nor
an owner-accepted reason is `NOT VERIFIED`. An install the owner authorised
(a same-signer `install -r`) must be named in the Concerns.
