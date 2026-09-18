# Libarchive Guard Resolution, Gate Reclassification, Readiness — 2026-09-19

## Libarchive machine-guard resolution (Phase 18, outcome B)

Inspected: `tools/p05-004/test_archive_evidence_matrix.sh:23-25` asserts
`OVERALL=NOT_CLOSED` and `libarchive 3.8.9=UNRESOLVED` as **expected**
output; `ArchiveEvidenceMatrixHarness` computes `UNRESOLVED` for any
candidate without a full PASS gate matrix and without an executed binary.

The guard therefore intentionally means "no real comparator binary
executed and no full gate matrix passed". That remains factually true:
no libarchive binary was built or executed in this campaign (the bounded
3.8.9 CMake configuration attempt stopped during feature probes; no
Android/JNI artifact was produced).

Resolution: machine output stays `UNRESOLVED` (semantics unchanged — not
recolored to fake green). The separate architectural disposition,
`NOT_JUSTIFIED_FOR_CORE_V1`, stands because no exercised Java-first
evidence exposed a required CORE_V1 capability needing native libarchive.
The `UNRESOLVED` machine state is therefore explicitly **not** a
Technology Freeze blocker. Reopen only on a concrete demonstrated
Java-first gap or unacceptable measured cost. No optional bounded rebuild
was attempted: host CMake/make exists, but an Android JNI comparator for
an already-unjustified component would not change the disposition.

## RAR gate preservation (Phase 17)

Unchanged: Junrar 8.1.1 technical `READY_WITH_GATES`;
licensing `EXTERNAL_LICENSE_REVIEW_REQUIRED` (UnRAR-derived terms permit
handling but prohibit recreating RAR compression; no legal clearance
claimed or impersonated); effect `RAR_FEATURE_FREEZE_BLOCKING_ONLY`.
Does not block ZIP/TAR/ZSTD family selection. RAR creation remains
unsupported/uncommitted. If product authority later makes RAR
read/extract mandatory for CORE_V1, promote before that feature freeze.

## Final gate reclassification (Phase 19)

ARCHITECTURE_BLOCKER: none found.

TECHNOLOGY_FREEZE_BLOCKER: none remaining.
- zstd-jni source/build provenance → closed as
  `SOURCE_PROVENANCE_SUFFICIENT_WITH_NONREPRODUCIBLE_BUILD`
  (`09_ZSTD_PROVENANCE_BUILD.md`).
- 16 KiB application runtime → `16K_RUNTIME_VERIFIED (single-process)`
  for API 36 ps16k x86_64 (`08_16K_RUNTIME_CLOSURE.md`: 7/7 expected
  outcomes = 5 PASS + 2 controlled errors); multi-process relaunch on
  16 KiB and broader ABI/device coverage move to RELEASE_GATE
  (single-process full pass verified; relaunch blocked by guest
  infrastructure, and the same probe logic already demonstrated relaunch
  PASS on 4 KiB Pixel).
- Retained format/provider matrix → closed by `10_*` (routes, spool PoC
  18/18, containment, cleanup, cancellation, lifecycle contract, error
  normalization, bounded hostile corpus).

IMPLEMENTATION_GATEs: split/multipart ZIP corpus (Zip4j route); RAR
solid/multipart corpus; ZIP-attribute and Junrar symlink fixtures;
BZ2/XZ scale/limits corpus (byte routes proven); nested-archive depth
accounting; device-scale cancellation-latency measurement; broader
per-format hostile expansion; real `CrcErrorException`-vs-password
ambiguity resolution in the error wrapper.

RELEASE_GATEs: complete transitive NOTICE register (Commons/Zip4j/Junrar/
zstd-jni externals); production APK packaging + stable 16 KiB
relaunch/device evidence; Android API coverage 12–16; update ownership;
external Junrar license approval before RAR enablement; human acceptance.

## Candidate dispositions (Phase 20)

| Candidate | Disposition |
|---|---|
| Commons Compress 1.28.0 | `READY_FOR_TECHNOLOGY_FREEZE` (Apache notices = RELEASE_GATE) |
| Zip4j 2.11.6 | `READY_WITH_GATES` (implementation corpus gates above) |
| Junrar 8.1.1 technical | `READY_WITH_GATES` |
| Junrar licensing | `EXTERNAL_LICENSE_REVIEW_REQUIRED` → `TECHNOLOGY_FREEZE_ALLOWED_WITH_EXPLICIT_LICENSE_GATE` (RAR feature only) |
| zstd-jni 1.5.7-17 | `READY_WITH_GATES` (RELEASE_GATEs: NOTICE/packaging revalidation) |
| Java-first retained family | `READY_WITH_EXPLICIT_GATES` |
| libarchive 3.8.9 | `NOT_JUSTIFIED_FOR_CORE_V1` (machine `UNRESOLVED` retained, not a blocker) |

P05-004 overall: `CLOSED_WITH_EXPLICIT_GATES`.

Adversarial review (`12_ADVERSARIAL_DISPOSITIONS.md`): 7 findings, all
dispositioned — 4 remediated with new evidence (committed probe source,
verified smoke lib identity, real-engine spool integration, backslash/
duplicate/symlink/BZ2/XZ/CPIO coverage), 2 accepted as explicit
limitation/gates (N=1 sample, circumstantial tag inference), 1 rejected
with record (relabeling charge answered by evidence mapping). The 16 KiB
relaunch downgrade remains subject to Phase 22 independent-review
confirmation.
