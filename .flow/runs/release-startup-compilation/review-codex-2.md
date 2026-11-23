CODEX: unavailable - usage limit reached. Codex answered "You've hit your usage limit ... try again at Nov 25th, 2026 7:38 AM". This happened on `review` and `adversarial-review` with `gpt-6-astra`. A retry of `review` with the fallback `gpt-5.6-sol` failed the same way. Setup reported "Status: ready", so auth is fine and the limit is the only cause.

Codex produced no findings, so nothing was adjudicated. Range: `a4c19ba4..2d1c7395`, run in `/home/dev/orca/workspaces/Puber/release-startup-compilation-2`.

CONFIRMED:
- none (Codex returned no findings)
REJECTED:
- none
VERDICT: n/a

The owner's standing rule requires Codex on every round, so the conductor should stop this round. It can resume after the limit resets on 2026-11-25 07:38, or the owner can lift the requirement.
