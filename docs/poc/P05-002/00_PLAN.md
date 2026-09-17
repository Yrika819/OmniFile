# P05-002 SAF Recovery Evidence Harness Plan

> **For agentic workers:** This plan is executed inline in the requested P05-002 worktree. The harness is disposable evidence only and must not be promoted into production code.

**Goal:** Preserve the deterministic host recovery model and add a disposable raw-SDK Android SAF harness for a separately authorized parent run.

**Architecture:** Keep the host-side in-memory policy model, and add a separate isolated one-activity Java APK that uses real `ACTION_OPEN_DOCUMENT_TREE` grants, disposable fixtures, bounded stream transfers, JSONL checkpoints, and startup reconciliation. The APK records evidence but does not become production authority or delete move sources.

**Tech Stack:** Python 3 standard library for the policy harness; Android SDK API 36 `android.jar`, Java 8, `aapt2`, `d8`, and `apksigner` for the disposable APK. No Gradle, AndroidX, production module, or external dependency was added.

**Spec:** `docs/architecture/12_POC_BACKLOG.md` — POC-002 durable large operation and reconciliation requirements, interpreted with `docs/architecture/03_OPERATION_MODEL.md` and `docs/research/09_BACKGROUND_OPERATIONS.md`.

## Global Constraints

- Every harness artifact is labeled exactly `POC-ONLY — NOT PRODUCTION AUTHORITY`.
- The Python model is host-side fault injection; the raw APK is a disposable Android SAF recorder, not production authority or device evidence until a parent runs it.
- No production app, production dependency/version choice, persistence schema, executor mapping, or Android API contract may be initialized or frozen.
- This continuation performs no ADB, emulator, physical-device, provider-account, or user-file operation. Any historical P0-001 evidence remains historical and is not a result of this commit.
- Test data is generated in memory; no user data or destructive filesystem operation is permitted.
- Recovery is reconciliation, not blind replay; durable checkpoints are observations and are not storage truth.
- For a move-shaped operation, source deletion is never performed by reconciliation and is only eligible after independently observed finalized destination state.
- Hand-written recovery logic follows RED -> GREEN -> REFACTOR TDD; generated/configuration-only files are exempt.
- The final commit must contain only the P05-002 docs and disposable host/Android harness files in this worktree.

## File Map

- `docs/poc/P05-002/00_PLAN.md` — this execution plan and scope gate.
- `docs/poc/P05-002/01_ENVIRONMENT.md` — checkout, toolchain, and device boundary.
- `docs/poc/P05-002/02_HARNESS_DESIGN.md` — host model, state machine, and exact interfaces.
- `docs/poc/P05-002/03_TEST_MATRIX.md` — deterministic test matrix and statuses.
- `docs/poc/P05-002/04_RESULTS.md` — recorded host-test evidence and status.
- `docs/poc/P05-002/05_FAILURES_AND_ANOMALIES.md` — resolved issues and remaining blockers.
- `docs/poc/P05-002/06_CONCLUSIONS.md` — bounded conclusions.
- `docs/poc/P05-002/07_ARCHITECTURE_IMPACT.md` — bounded consequences for the existing architecture decisions.
- `docs/poc/P05-002/08_ANDROID_HARNESS_DESIGN.md` — isolated raw-SDK Android recorder design.
- `docs/poc/P05-002/09_ANDROID_HARNESS_PLAN.md` — TDD implementation plan and verification gates.
- `docs/poc/P05-002/harness/saf_recovery.py` — disposable recovery model and in-memory provider.
- `docs/poc/P05-002/harness/test_saf_recovery.py` — standard-library tests for the hand-written model.
- `docs/poc/P05-002/harness/test_android_contract.py` — host contract for the raw Android harness.
- `docs/poc/P05-002/android/` — disposable raw-SDK APK source, build script, and parent-run instructions.

## Execution Tasks

### Task 1: Record scope and toolchain gate

- [x] Inspect the requested branch/worktree and existing POC-002/storage-operation authorities.
- [x] Check for an Android build toolchain without invoking ADB or any device operation.
- [x] Record the result in `01_ENVIRONMENT.md`.

### Task 2: Define the disposable host model

- [x] Write the failing tests in `harness/test_saf_recovery.py` for checkpoint lag, permission revocation, disconnect, mismatched partial output, and ambiguous move finalization.
- [x] Run the focused test file and confirm the failures are caused by missing recovery behavior.
- [x] Implement the smallest `saf_recovery.py` model required by those tests.
- [x] Re-run the focused tests and then the complete harness test command.
- [x] Refactor only while the tests remain green.

### Task 3: Document evidence and impact

- [x] Record the exact test command, result count, and evidence boundary in `04_RESULTS.md`.
- [x] Record Android/physical-device limitations in `05_FAILURES_AND_ANOMALIES.md` and `06_CONCLUSIONS.md`.
- [x] Record accepted, unchanged, and still-open architecture consequences in `07_ARCHITECTURE_IMPACT.md`.

### Task 4: Final verification and commit

- [x] Check that every required document exists and contains the exact POC label.
- [x] Check the diff and ensure no production module, device command, or unrelated file changed.
- [x] Run the full host harness test command again from the worktree.
- [x] Commit only the verified P05-002 files on `poc/core-readiness-saf-operations-v1`.
- [x] Report changed files, commit SHA, tests, and blockers with no stronger claim than the evidence supports.

## Expected Evidence Boundary

The result supports three bounded statements: the host-side policy model
deterministically classifies selected SAF-shaped recovery faults; the isolated
APK is buildable and statically contains the required real SAF/grant and
durable-record paths; and a separately authorized parent can record the three
transfer directions plus interruption/restart/reconciliation, cancellation,
conflict, mutation, finalization, and identity observations. This branch does
not claim any device/runtime result.
