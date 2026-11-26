---
slug: ci-workflow-hardening
title: "Pin GitHub Actions by SHA, drop persisted credentials, narrow permissions, pin the release signer"
stage: done
size: M
tracks: []
kind: improvement
branch: prakunin/ci-workflow-hardening
worktree:
base: 90e69aff
spec: docs/superpowers/specs/2026-11-26-ci-workflow-hardening.md
plan: docs/superpowers/plans/2026-11-26-ci-workflow-hardening.md
rounds: {verify: 2, review: 2, judge: 0}
blocked_by: []
source: upstream-sync-triage
created: 2026-11-26
migrated:
updated: 2026-11-26
---

## Next
triage with /task:run ci-workflow-hardening

## Open questions
| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q2 | Stay on current action majors (v4/v5/v2, pinned by SHA) instead of moving to the Node 24 majors now? | no | answered | Yes; bump majors in a separate run |
| Q3 | Add upstream's keytool pre-check of the decoded keystore? | no | answered | No; the APK signer check covers it |
| Q4 | Keep the "Assert GitHub-hosted runner" step from 2a4bc402? | no | answered | Yes |
| Q1 | Which release certificate SHA-256 does the signer pin check? | yes | answered | 7431d0e642edd29fc633fed6d529decab17e7b8dc1e40a75f880a07745baf2c6 (CN=Puber Release, OU=Puber, O=Puber, L=Warsaw, ST=Mazowieckie, C=PL), read with apksigner from the published v1.12.3 and v1.12.7 APKs; owner confirmed 2026-11-26: the release key has never changed |

## Decisions
| Date | Decision | By |
|------|----------|----|
| 2026-11-26 | Pin release signer SHA-256 7431d0e6…f2c6; the key has never changed | owner |
| 2026-11-26 | Q2-Q4 as recommended; codex and antigravity required on every review round | owner (standing instruction: recommended) |
| 2026-11-26 | Gate 1: spec approved (size M, 9 ACs) | owner (standing instruction: recommended) |
| 2026-11-26 | Q5: AC-8 test checks the upstream triage plan file is absent instead of an empty plans/ diff | owner (standing instruction: recommended) |
| 2026-11-26 | Gate 2: ship | owner (standing instruction: recommended) |

## Log
- 2026-11-26 created (stage idea) from the upstream (rovkinmax/Puber) re-check: upstream has no app-code commits after b755826c (17 new commits, all `.kent/` tooling), so the open items are the unmerged ports of upstream-sync-triage. Spec with the triage table: `git show upstream-sync-triage:docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md`. Branch upstream-sync-triage is 31 commits ahead of its base and master is 376 ahead, so cherry-pick into a fresh worktree rather than rebase the branch.
- Port P1 (triage T14), upstream 7bd28c11. master still uses floating tags in .github/workflows/release.yml and pr-checks.yml (checkout@v4, setup-java@v4, setup-gradle@v5, upload-artifact@v4, action-gh-release@v2). Take: SHA pins for every action (look up setup-gradle and action-gh-release SHAs ourselves, upstream does not pin them), `persist-credentials: false`, `permissions: contents: read` with write only where a job needs it; keep Lint, workflow_dispatch and the secrets check. Signer fingerprint pin with `apksigner verify` needs the SHA-256 of our release certificate from the owner (open since upstream-sync-triage Task 2). Do not take upstream's signing-mode logic in app/build.gradle.kts. No device.
- 2026-11-26 release cert SHA-256 taken from the published GitHub release APKs (v1.12.3 and v1.12.7 match): 7431d0e642edd29fc633fed6d529decab17e7b8dc1e40a75f880a07745baf2c6. Public value, safe to commit. Pin it in the workflow (or a repo variable) and fail the release job when `apksigner verify --print-certs` on the built APK shows another digest.
- 2026-11-26 /task:run started (base 90e69aff); research
- 2026-11-26 research.md written (recommended A: cherry-pick 2a4bc402 from upstream-sync-triage, add a signer-verify script and release step)
- 2026-11-26 brief.md written: size M (security of the release path), kind improvement, no tracks, 9 ACs; Q2-Q4 taken as recommended
- 2026-11-26 worktree /home/dev/orca/workspaces/Puber/ci-workflow-hardening (branch prakunin/ci-workflow-hardening) via orca; architect design
- 2026-11-26 design-architect.md written (13 decisions, 7 risks, no questions); test-designer
- 2026-11-26 test-design.md written (P0 12, P1 3, P2 4, manual 2); spec assembled and committed in the worktree; gate 1 approved (standing instruction). Stage plan.
- 2026-11-26 plan written (3 tasks); Q5 taken as recommended; readiness
- 2026-11-26 readiness-1 READY (2 non-blocking notes); pasted into the plan and committed. Stage impl.
- 2026-11-26 impl done: f714c702, a92f807b, 7a836616; conductor check: diff --stat 8 files, check.fast green, co-authored 0; check-named-tests reports 3 P1 names MISSING but they exist in tools/tests/test-verify-apk-signer.sh:95-107 (checker does not scan .sh). Stage verify.
- 2026-11-26 qa round 1: check.full green, AC-1..AC-9 PASS, no device needed (app/ untouched), manual M1/M2 after merge. Stage review (round 1 at 7a836616).
- 2026-11-26 review round 1 at 7a836616: blind 3 minor, edge 1 minor, tests 3 minor, codex ran (1 minor, 3 rejected), antigravity ran (clean). triage-1.md: fix #1-#7, reject #8-#10. Implementer fix round.
- 2026-11-26 fix round 1: c11d3e59, cfa72862 (triage-1 #1-#7); check.fast BUILD SUCCESSFUL in 828ms. Review round 2 on 7a836616..cfa72862 + qa round 2 on AC-2..AC-7.
- 2026-11-26 review round 2: edge 1 minor, tests 2 minor, codex ran (clean), antigravity ran (clean, 1 minor note); qa round 2 all PASS. triage-2.md: no blockers/majors; 3 minors accepted. Stage judge.
- 2026-11-26 judge-1 PASS (manual M1, M2 after merge; spec Design still names tools/verify-apk-signer.sh as the called path, workflow now calls signer-verifier/ - non-blocking drift). Gate 2: ship (standing instruction). Squash before ship.
- 2026-11-26 squashed to 2 commits (6ad1bdc9 code, e0eac78c docs); tree 9ecbdb3f identical to cfa72862; tests 6 + 15 PASS; co-authored 0. Next: /task:ship.
- 2026-11-26 /task:ship cannot be invoked by the conductor (user-only command); owner to run /task:ship ci-workflow-hardening.
- 2026-11-26 shipped: merged prakunin/ci-workflow-hardening into master as eb505d6d (--no-ff); Orca worktree removed; branch kept until the owner deletes it.
