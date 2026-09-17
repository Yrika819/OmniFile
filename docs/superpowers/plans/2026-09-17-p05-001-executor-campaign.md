# P05-001 Executor Campaign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Create eight P05-001 evidence records and a disposable host-only executor/lifecycle fixture harness without claiming Android runtime evidence or freezing D022.

**Architecture:** The harness separates durable operation truth from executor observations. Pure Python logic classifies synthetic destination reality and interruption-shaped fixtures; a JSON report emits planning-only evidence and explicit `NOT_TESTED` Android-runtime boundaries. The eight records capture the question, fixture model, executor matrix, test protocol, results, blockers, and architecture impact.

**Tech Stack:** Python 3.9 standard library (`unittest`, `dataclasses`, `json`); no Gradle, Kotlin, Android runtime, emulator, device, ADB, or external dependency.

**Spec:** Approved P05-001 design in the conversation; authoritative constraints in `docs/architecture/00_ARCHITECTURE_STATUS.md`, `03_OPERATION_MODEL.md`, `12_POC_BACKLOG.md`, `14_P0_EVIDENCE_INCORPORATION.md`, and `docs/research/09_BACKGROUND_OPERATIONS.md`.

## Global Constraints

- Work only in `/Users/yuta/Desktop/File Manager/.worktrees/p05-executors` on `poc/core-readiness-executors-v1`.
- Do not access ADB or any other worktree.
- Keep D022 open; do not select a production executor.
- Android runtime behavior remains `NOT_TESTED`; host logic is not Android evidence.
- Harness and fixtures are `DISPOSABLE` and not production authority.
- Use generated metadata only; no user data or large binaries.
- Apply TDD: failing test first, minimal implementation, fresh green verification.

---

### Task 1: Create the eight evidence records

**Files:**
- Create: `docs/poc/P05-001/00_PLAN.md`
- Create: `docs/poc/P05-001/01_QUESTION_AND_HYPOTHESIS.md`
- Create: `docs/poc/P05-001/02_FIXTURE_MODEL.md`
- Create: `docs/poc/P05-001/03_EXECUTOR_MATRIX.md`
- Create: `docs/poc/P05-001/04_TEST_PROTOCOL.md`
- Create: `docs/poc/P05-001/05_RESULTS.md`
- Create: `docs/poc/P05-001/06_LIMITATIONS_AND_BLOCKERS.md`
- Create: `docs/poc/P05-001/07_ARCHITECTURE_IMPACT.md`

- [ ] Record the D022 question, hypotheses, scope, safety boundary, and explicit non-claims.
- [ ] Record the executor-shaped matrix and required future Android measurements.
- [ ] Record the host toolchain audit and exact Python reproduction commands.
- [ ] Record only host fixture results as green; preserve Android runtime as blocked/not tested.

### Task 2: Red TDD cycle for the fixture oracle

**Files:**
- Modify: `harness/test_p05_001_executor_harness.py`

- [ ] Add failing assertions for planning-only authority, null executor selection, false platform observation, and complete executor-shaped fixture coverage.
- [ ] Run `PYTHONPATH=harness python3 -m unittest discover -s harness -p 'test_*.py' -v` and confirm the new assertions fail for the missing report/matrix behavior.

### Task 3: Green implementation

**Files:**
- Modify: `harness/p05_001_executor_harness.py`

- [ ] Add the smallest immutable fixture/report fields needed by the failing tests.
- [ ] Keep source deletion allowed only for verified-final MOVE with a present source.
- [ ] Keep `platform_runtime_observed` false and `selected_executor` null.
- [ ] Rerun the full unittest command and confirm zero failures.

### Task 4: Generate and inspect the disposable report

**Files:**
- Modify: `harness/p05_001_executor_harness.py` only if needed.

- [ ] Run `PYTHONPATH=harness python3 harness/p05_001_executor_harness.py --json`.
- [ ] Confirm the report has no device/runtime claims and matches the eight-document matrix.
- [ ] Update result/limitation docs only for factual mismatches.

### Task 5: Verify and commit

- [ ] Run the exact protocol commands, `git diff --check`, doc-count verification, and a textual no-ADB audit.
- [ ] Review that D022 remains open and only the current worktree changed.
- [ ] Commit with `git add docs/poc/P05-001 harness docs/superpowers/plans/2026-09-17-p05-001-executor-campaign.md && git commit -m "poc: add P05-001 executor readiness campaign"`.
