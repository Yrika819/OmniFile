# P05-002 Android SAF Harness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add the smallest disposable raw-SDK Android harness that records real SAF grants, bounded transfers, and durable recovery evidence.

**Architecture:** A single isolated Java activity owns explicit tree selection, deterministic fixtures, stream-copy operations, JSON-lines durability, and startup reconciliation. A standard-library host contract test protects the package/API/build/run boundary; no device automation is part of the repository change.

**Tech Stack:** Android SDK API 36 compile jar, Java 8 source, Android framework APIs only, `aapt2`, `d8`, `apksigner`, Python 3 `unittest`.

**Spec:** `docs/poc/P05-002/08_ANDROID_HARNESS_DESIGN.md`

## Global Constraints

- Every new artifact is labeled exactly `POC-ONLY — NOT PRODUCTION AUTHORITY`.
- Package ID is exactly `dev.poc.safoperations`; `com.omnifile` must not occur in the harness.
- Only `docs/poc/P05-002/**` may change.
- No ADB, emulator, physical-device, user-file, credential, or provider-account operation is executed by this task.
- SAF selection uses real `ACTION_OPEN_DOCUMENT_TREE` with persistable read/write grants.
- Durable state contains no live descriptors, streams, Android services, or executor objects.
- Reconciliation never sets `deleteSource` true and never deletes a source.
- Hand-written behavior follows RED → GREEN → REFACTOR.

## File Map

- `docs/poc/P05-002/android/AndroidManifest.xml` — isolated activity manifest.
- `docs/poc/P05-002/android/src/dev/poc/safoperations/MainActivity.java` — disposable UI, SAF adapter, fixtures, transfer state, event log, and reconciliation.
- `docs/poc/P05-002/android/build.sh` — exact local raw-SDK build/sign recipe; outputs stay under ignored `android/build/`.
- `docs/poc/P05-002/android/README.md` — parent build/run/capture instructions and explicit non-claims.
- `docs/poc/P05-002/harness/test_android_contract.py` — host contract tests written before Android source.
- `docs/poc/P05-002/03_TEST_MATRIX.md`, `04_RESULTS.md`, `05_FAILURES_AND_ANOMALIES.md`, `06_CONCLUSIONS.md`, `07_ARCHITECTURE_IMPACT.md` — updated evidence and disposition.

### Task 1: Add the failing Android contract

**Files:**
- Create: `docs/poc/P05-002/harness/test_android_contract.py`
- Test: `docs/poc/P05-002/harness/test_android_contract.py`

- [ ] Write tests that require the manifest, Java source, build script, and README to exist and contain the isolated package, real tree intent, persistable permission calls, durable event fields, all three directions, fault/reconciliation labels, build-tool commands, and no `com.omnifile`/ADB automation.
- [ ] Run `PYTHONPATH=docs/poc/P05-002/harness python3 -m unittest discover -s docs/poc/P05-002/harness -p 'test_*.py' -v` and observe expected RED caused by missing Android artifacts.

### Task 2: Implement the minimal raw-SDK app and build recipe

**Files:**
- Create: `docs/poc/P05-002/android/AndroidManifest.xml`
- Create: `docs/poc/P05-002/android/src/dev/poc/safoperations/MainActivity.java`
- Create: `docs/poc/P05-002/android/build.sh`
- Create: `docs/poc/P05-002/android/README.md`

- [ ] Add the manifest with `dev.poc.safoperations`, API 31 minimum, API 36 target, and one exported launcher activity.
- [ ] Add the activity with tree selection/persisted grants, identity snapshots, deterministic fixtures, Local→SAF/SAF→Local/SAF→SAF copies, JSON-lines checkpoints, interruption/cancellation/mutation/conflict controls, finalization, and startup reconciliation.
- [ ] Add a raw SDK build that compiles Java against `android.jar`, links the manifest with `aapt2`, dexes with `d8`, signs with a disposable build-local key, verifies with `apksigner`, and never invokes ADB.
- [ ] Add exact parent instructions for selecting trees, creating fixtures, running each direction, injecting faults, restarting/reconciling, and collecting the app-private JSONL log; label device-dependent cases as parent-only.
- [ ] Run the new contract test and fix only failures caused by the implementation.

### Task 3: Update P05-002 evidence docs

**Files:**
- Modify: `docs/poc/P05-002/03_TEST_MATRIX.md`
- Modify: `docs/poc/P05-002/04_RESULTS.md`
- Modify: `docs/poc/P05-002/05_FAILURES_AND_ANOMALIES.md`
- Modify: `docs/poc/P05-002/06_CONCLUSIONS.md`
- Modify: `docs/poc/P05-002/07_ARCHITECTURE_IMPACT.md`

- [ ] Record host contract and raw APK build results separately from unrun parent/device evidence.
- [ ] State exactly which runtime cases are recordable by the app and which remain untested until a parent run.

### Task 4: Self-review, full verification, and focused commit

**Files:**
- Review only: `git diff --check`, `git diff --name-only`, and all P05-002 files.

- [ ] Verify the build creates a signed APK with `build-tools` only and does not execute ADB.
- [ ] Run the complete host suite and inspect exact counts/output.
- [ ] Confirm only P05-002 files changed and no production package or dependency was introduced.
- [ ] Commit one focused continuation commit only if the APK build and host tests both pass.
