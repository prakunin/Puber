# <Title> — plan

**Spec:** `docs/superpowers/specs/<date>-<slug>-design.md`
**Goal:** <one sentence>

## Global constraints

- Worktree builds: `./tools/agentw <task>` (`AGENT_GRADLE_SERIAL=1` when others
  build), never `./gradlew`.
- One task = green build + green tests of the touched classes + one commit.
  Commit message: one imperative English sentence, no prefixes, no
  `Co-Authored-By`.
- The plan is self-contained: the implementer reads this file, the spec, and
  the files cited below — nothing else.

---

### Task 1: <what changes>

**Covers:** AC-1, AC-2
**Files:**
- Modify: `path/File.kt` — <what> [Source: path/File.kt:123]
- Test: `app/src/test/.../FileTest.kt`

**Consumes:** <interfaces from earlier tasks or existing code>
**Produces:** <signatures later tasks rely on>

- [ ] Write the failing test `<name>` (bug route / P0)
- [ ] <step>
- [ ] Compile and run `<FQCN>`; commit

**Done when:** <command or observation>

---

## Implementation record

<!-- implementer -->

**Commits:**

**Deviations from the plan:**

**Files touched outside the plan:**

**For the reviewers:**

**Blocked:**

## Readiness

<!-- final-judge, readiness mode: READY | NOT READY and why. -->
