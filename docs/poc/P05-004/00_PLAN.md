# P05-004 Archive Candidate Closure Implementation Plan

> **For agentic workers:** This plan is executed in the isolated worktree on branch `poc/core-readiness-archive-v1`. The resulting commit is documentation and disposable host/JVM evidence tooling only.

**Goal:** Create an auditable P05-004 archive-candidate closure package for Commons Compress, Zip4j, Junrar, zstd-jni, and libarchive without selecting production dependencies or overstating untested evidence.

**Architecture:** The package separates measured P0 evidence from proposed closure gates. Eight focused documents cover scope, candidate disposition, format behavior, provider/access behavior, security, licensing/native concerns, and architecture impact. A dependency-free JVM harness validates a TSV evidence manifest and reports closure as `NOT_CLOSED` whenever any required gate is unresolved, unknown, or not tested.

**Tech Stack:** Markdown; POSIX shell; Java standard library only; no Android SDK, Gradle project, archive-library dependency, ADB, or network fixture.

**Spec:** The approved P05-004 campaign scope in the user request, grounded in the branch-local P0 reconciliation at `docs/architecture/14_P0_EVIDENCE_INCORPORATION.md`, `docs/architecture/12_POC_BACKLOG.md`, `docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md`, and `docs/research/04_ARCHIVE_FORMATS.md`.

## Global Constraints

- Work only in `/Users/yuta/Desktop/File Manager/.worktrees/p05-archive`.
- Do not touch ADB, other worktrees, devices, production code, or dependency declarations.
- Preserve the P0 distinction between architecture-review evidence and Technology Freeze.
- Do not convert documentation claims into new runtime measurements.
- Keep `NOT_TESTED` and `UNKNOWN` explicit, with an owner/action needed for closure.
- Treat Junrar/UnRAR licensing and all native code as separate acceptance gates.
- Commit the branch after fresh verification; do not push or merge.

---

### Task 1: Establish the harness contract with a failing test

**Files:**
- Create: `tools/p05-004/test_archive_evidence_matrix.sh`
- Create: `tools/p05-004/fixtures/valid.tsv`
- Create: `tools/p05-004/fixtures/duplicate.tsv`

- [x] Write shell assertions for valid-manifest `NOT_CLOSED` output and duplicate-record rejection.
- [x] Run the test before the Java implementation and record the expected RED failure.

### Task 2: Implement the disposable evidence harness

**Files:**
- Create: `tools/p05-004/ArchiveEvidenceMatrixHarness.java`

- [x] Parse the documented TSV schema using only the Java standard library.
- [x] Validate required candidates, required gates, status vocabulary, and duplicate keys.
- [x] Emit candidate dispositions and `OVERALL=NOT_CLOSED` when unresolved evidence remains.
- [x] Run the harness tests to GREEN.

### Task 3: Write the eight P05-004 documents

**Files:**
- Create: `docs/poc/P05-004/00_PLAN.md`
- Create: `docs/poc/P05-004/01_SCOPE.md`
- Create: `docs/poc/P05-004/02_CANDIDATE_CLOSURE.md`
- Create: `docs/poc/P05-004/03_FORMAT_MATRIX.md`
- Create: `docs/poc/P05-004/04_PROVIDER_ACCESS.md`
- Create: `docs/poc/P05-004/05_SECURITY.md`
- Create: `docs/poc/P05-004/06_LICENSE_NATIVE.md`
- Create: `docs/poc/P05-004/07_ARCHITECTURE_IMPACT.md`

- [x] Record exact repository source-line references for every measured claim.
- [x] Separate existing evidence, proposed gates, and unknowns.
- [x] Include the five-candidate matrix and the proposed evidence-record schema.
- [x] State explicitly that no production dependency is selected.

### Task 4: Final verification and commit

- [x] Run the disposable harness test suite fresh.
- [x] Run Markdown/reference consistency checks without building the application.
- [x] Inspect the complete diff and verify only the approved files changed.
- [ ] Commit on `poc/core-readiness-archive-v1`.
- [ ] Report the commit SHA, tests, changed files, and remaining `NOT_TESTED`/`UNKNOWN` findings.
