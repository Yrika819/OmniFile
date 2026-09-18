# P05-004 Real Evidence Closure Campaign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace P05-004's declarative archive-engine unknowns with reproducible artifact, license, parser, provider, hostile-input, scale, native, and page-size evidence, then publish a bounded final disposition without initializing production OmniFile or executing Technology Freeze.

**Architecture:** Use isolated disposable host/JVM tooling and resolved Maven artifacts outside production Gradle configuration. Independent read-only evidence tracks return machine-readable reports; the parent integrates only verified results into the existing `docs/poc/P05-004` evidence tree, updates the manifest/harness where needed, performs adversarial review, and then updates the synthesis branch documentation only.

**Tech Stack:** POSIX shell, Java/JVM, Maven or Gradle dependency resolution in temporary directories, `jar`/`unzip`/`sha256sum`, `readelf`/`llvm-readelf`/Android build tools when installed, ADB only if a connected device is available, Markdown, and Git.

**Spec:** The user-provided `OMNIFILE — P05-004 REAL EVIDENCE CLOSURE CAMPAIGN` brief, with current P05-004 authority `03d7c29e04932ecfbd926cd7934f8be66ab05fa2` and synthesis authority `57410625d30b3205d29a9e66494802b0612befce`.

## Global Constraints

- Work in `/Users/yuta/Desktop/File Manager-worktrees/p05-archive` on `poc/core-readiness-archive-v1`, preserving ancestry from `03d7c29e04932ecfbd926cd7934f8be66ab05fa2`.
- Do not initialize production OmniFile, add an app module, freeze SDK/Kotlin/AGP/JDK/dependency versions, add signing/release/CI, modify main, or modify/merge P05-001/002/003.
- Preserve `tools/p05-004/fixtures/duplicate.tsv` and its negative-coverage purpose.
- Do not commit generated large archives, caches, SDKs, secrets, or unrelated files; commit generators and compact fixtures instead.
- Separate FACT, INTERPRETATION, and GATE CLASSIFICATION; retain `UNKNOWN`, `NOT_TESTED`, `NOT_CLOSED`, and `NO-GO` where evidence is absent.
- Serialize all physical-device operations; a Pixel run at 4 KiB is not 16 KiB runtime verification.
- Parent owns final integration, commits, push, remote verification, and synthesis update.

---

### Task 1: Resolve exact candidate artifacts and provenance

**Files:**
- Create or modify only disposable reports under `/tmp/p05-004-*` during investigation.
- Integrate verified results into `docs/poc/P05-004/01_ENVIRONMENT.md`, `04_RESULTS.md`, `05_FAILURES_AND_ANOMALIES.md`, `06_CONCLUSIONS.md`, and `07_ARCHITECTURE_IMPACT.md`.
- Extend `tools/p05-004/fixtures/valid.tsv` only after artifact identities are verified.

**Interfaces:**
- Consumes: artifact coordinates from the current P05-004 docs and actual Maven Central resolution.
- Produces: exact version, repository/tag or commit where traceable, SHA-256, POM, transitives, license/NOTICE inventory, bundled native contents, and reproducible resolution commands for Commons Compress, Zip4j, Junrar, and zstd-jni.

- [ ] Verify the four coordinates against actual resolved artifacts, not README claims.
- [ ] Save checksums, POMs, dependency trees, and archive-entry/license inventories in a compact report outside Git first.
- [ ] Record any artifact or source discrepancy as a gate rather than silently substituting a version.
- [ ] Parent reviews the report and commits only compact provenance metadata or scripts needed to reproduce it.

### Task 2: Establish Junrar license provenance and RAR gate

**Files:**
- Modify the existing Junrar sections in `docs/poc/P05-004/04_RESULTS.md`, `06_CONCLUSIONS.md`, and `07_ARCHITECTURE_IMPACT.md`.
- Preserve `tools/p05-004/JunrarLocalInspectionHarness.java` unless a narrowly scoped evidence improvement is required.

**Interfaces:**
- Consumes: the exact Junrar artifact from Task 1, its POM and embedded metadata, upstream source/license materials, and the architecture RAR-creation authority.
- Produces: a technically precise disposition among `TECHNOLOGY_FREEZE_ALLOWED_WITH_NOTICES`, `TECHNOLOGY_FREEZE_ALLOWED_WITH_EXPLICIT_LICENSE_GATE`, `EXTERNAL_LICENSE_REVIEW_REQUIRED`, `NOT_ACCEPTABLE_FOR_PRODUCTION`, or `UNRESOLVED`, plus `FAMILY_FREEZE_BLOCKING`, `RAR_FEATURE_FREEZE_BLOCKING_ONLY`, `IMPLEMENTATION_GATE`, or `RELEASE_GATE`.

- [ ] Inspect artifact, META-INF, source headers, UnRAR-derived terms, redistribution conditions, and the RAR creation prohibition.
- [ ] Distinguish read/extract evidence from encoder capability without offering legal clearance.
- [ ] Derive the gate severity from architecture authority; do not invent an optional-RAR exception.

### Task 3: Run actual JVM engine parser and provider-access evidence

**Files:**
- Create disposable harness/source and fixtures under `tools/p05-004/engine-evidence/` only if compact and reproducible.
- Integrate verified results into `docs/poc/P05-004/02_HARNESS_DESIGN.md`, `03_TEST_MATRIX.md`, `04_RESULTS.md`, and `05_FAILURES_AND_ANOMALIES.md`.

**Interfaces:**
- Consumes: resolved artifacts from Task 1 and compact real-format fixtures generated or checked into the evidence harness.
- Produces: actual Commons Compress, Zip4j, Junrar, and zstd-jni-assisted parser outcomes plus provider behavior for sequential streams, channels/descriptors, and any practical URI/SAF adapter; records path/seek/reopen/materialization/spool requirements.

- [ ] Exercise applicable TAR/TAR.GZ/TAR.BZ2/TAR.XZ/ZIP, Zip64/encrypted/split ZIP, RAR4/RAR5, and TAR.ZST paths only where the actual libraries support them.
- [ ] Exercise malformed/truncated/password cases and capture exact exception class/message category, accepted/rejected result, and bounded timeout behavior.
- [ ] Keep application policy separate from parser behavior; do not call parser success a security certification.
- [ ] Record provider adapter limitations honestly, including required spooling.

### Task 4: Run actual hostile-corpus and parser-scale evidence

**Files:**
- Create compact corpus generators and runner scripts under `tools/p05-004/engine-evidence/`.
- Integrate measurements into `docs/poc/P05-004/03_TEST_MATRIX.md`, `04_RESULTS.md`, `05_FAILURES_AND_ANOMALIES.md`, and `06_CONCLUSIONS.md`.

**Interfaces:**
- Consumes: actual engine harness from Task 3 and reproducible generated fixtures.
- Produces: per-engine/case parser result, exception, timeout/crash, partial-output and cleanup observations; real 10,000/100,000-entry archive measurements or exact largest safe case with timing, heap/native memory where measurable, cancellation, first-listing latency, single-entry latency, and spool footprint.

- [ ] Run traversal, absolute-path, duplicate/conflict, truncation, malformed metadata, invalid compressed stream, ratio/entry-count, nested, link, unsupported-feature, password, cancellation, and wrapper cleanup cases with hard resource limits.
- [ ] Generate large real archives without committing them; record generator parameters and hashes.
- [ ] Never extrapolate 10k to 100k and never intentionally exhaust host/device resources.
- [ ] Keep the existing 14-fixture application-policy harness intact and distinguish its result from actual parser evidence.

### Task 5: Inspect zstd-jni native artifacts and page-size readiness

**Files:**
- Create disposable native inspection scripts/reports under `/tmp/p05-004-*` or `tools/p05-004/native-evidence/` if reproducible and compact.
- Integrate verified results into `docs/poc/P05-004/01_ENVIRONMENT.md`, `04_RESULTS.md`, `05_FAILURES_AND_ANOMALIES.md`, `06_CONCLUSIONS.md`, and `07_ARCHITECTURE_IMPACT.md`.

**Interfaces:**
- Consumes: exact zstd-jni artifact from Task 1 and installed Android/ELF tooling.
- Produces: every bundled `.so` path, ABI, size, checksum, ELF class/machine, PT_LOAD alignment, packaging alignment/compression result, static 16 KiB classification, connected-device page size if available, and bounded malformed-input native failure result.

- [ ] Inspect arm64-v8a and every other present ABI; do not assume ABI support.
- [ ] Run current available Android tooling and label 4 KiB runtime, 16 KiB static compatibility, and 16 KiB runtime separately.
- [ ] Use a connected Pixel only serially and record model/API/build/ABI/APK SHA; absence of a 16 KiB runtime remains explicit.
- [ ] Test invalid/truncated Zstandard input under strict limits for Java exception, native crash, hang, or resource exhaustion.

### Task 6: Parent integration, capability matrix, libarchive disposition, and adversarial review

**Files:**
- Modify the existing P05-004 evidence documents and manifest only.
- Preserve `tools/p05-004/fixtures/duplicate.tsv`.
- Create review/ledger artifacts only in the ignored SDD workspace for this plan.

**Interfaces:**
- Consumes: Tasks 1–5 reports and all existing P05-004 evidence.
- Produces: actual multi-engine capability matrix, error-normalization feasibility, exactly one libarchive disposition, candidate and family classifications, severity of each residual gate, and findings/dispositions from an independent skeptical review and final code/doc review.

- [ ] Use `YES`, `NO`, `CONDITIONAL`, `FORMAT_DEPENDENT`, or `UNKNOWN` per capability; do not collapse engine differences.
- [ ] Classify libarchive as exactly one of `REQUIRED_FOR_CORE_V1`, `OPTIONAL_LATER`, `NOT_JUSTIFIED_FOR_CORE_V1`, or `UNRESOLVED` based on an exact unmet CORE_V1 requirement.
- [ ] Dispatch independent adversarial review to falsify provenance, licensing, provider, security, scale, native, 16 KiB, readiness, libarchive, and gate-severity claims.
- [ ] Resolve or explicitly disposition every actionable finding before publication.

### Task 7: Verify, publish P05-004, update synthesis, and stop

**Files:**
- Modify P05-004 docs and commit focused changes on `poc/core-readiness-archive-v1`.
- Modify only the three synthesis readiness documents on `poc/p05-core-readiness-synthesis` after P05-004 review and push.

**Interfaces:**
- Consumes: reviewed P05-004 result and exact remote state.
- Produces: verified local/remote SHAs, synthesis local/remote SHAs, protected-ref checks, production-boundary confirmation, and final status line; no Technology Freeze execution.

- [ ] Run the complete existing harness plus all new reproducible evidence commands and fresh documentation/reference checks.
- [ ] Confirm clean tree, push once normally, and verify remote HEAD equals local HEAD.
- [ ] Update synthesis from `57410625d30b3205d29a9e66494802b0612befce` with documentation-only changes, push normally, and verify equality.
- [ ] Return `YES`, `YES_WITH_EXPLICIT_GATES`, or `NO` for CORE_V1 Technology Freeze readiness and stop.

## Verification commands

```sh
git status --short --branch
git merge-base --is-ancestor 03d7c29e04932ecfbd926cd7934f8be66ab05fa2 HEAD
git ls-remote origin refs/heads/poc/core-readiness-archive-v1 refs/heads/poc/p05-core-readiness-synthesis
./tools/p05-004/test_archive_evidence_matrix.sh
git diff --check
```

The final report must include the exact commands, artifact hashes, measurements, review findings, gate classifications, local/remote equality, protected-ref equality, duplicate-fixture retention, unchanged P05-001/002/003, frozen product authority `OmniFile` / `com.omnifile`, and confirmation that production initialization and Technology Freeze were not executed.
