# P05-004 Archive Security Evidence

## Security boundary

The archive library is not the extraction-security boundary. P0 explicitly records application-level containment, symlink handling, expanded-byte and entry/nesting limits, cancellation, conflict policy, malformed-input normalization, and recovery ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:62-64`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md)).

## Required controls

| Threat/control | Required behavior | Evidence state |
|---|---|---|
| Absolute or escaping entry path | reject absolute paths and normalized escapes | Required architecture; fixture execution remains open |
| Mixed separators and drive-like paths | normalize and reject platform escapes/confusing names | Required by research; `NOT_TESTED` here |
| Symlink escape / TOCTOU | explicit symlink policy; no-follow or reject where unsafe; revalidate near mutation | Architecture requirement; `NOT_TESTED` here |
| Decompression bomb | bound entry count, single-entry bytes, total expanded bytes, nesting, actual writes, space reserve, and optionally time/CPU | Thresholds intentionally unfrozen |
| Duplicate/conflicting names | explicit skip/rename/replace policy; never silent unrelated overwrite | Policy required; exact implementation absent |
| Malformed/truncated input | fail safely per archive/entry where practical; no process crash | Larger corpus and native crash evidence open |
| Cancellation/recovery | cancellation reaches long-running extraction; partial destination is not presented as complete | Solid-archive latency explicitly unresolved |
| Nested archives | explicit maximum depth and resource accounting | Threshold intentionally unfrozen |

The source security research calls out archive traversal beyond ZIP, symlink attacks, malformed parser inputs, and independent decompression limits ([`docs/research/11_SECURITY.md:53-120`](../../research/11_SECURITY.md)).

## Corpus required for closure

The repository’s test strategy names ZIP/ZIP64/AES/split, encrypted/solid 7z, RAR4/RAR5 encrypted/multipart/solid, TAR compressors, CPIO/LZ4, traversal, symlink, duplicate, truncation, invalid sizes/offsets, high-ratio, enormous declarations, nested archives, password failures, and missing/corrupt multipart cases ([`docs/research/13_TEST_STRATEGY.md:114-138`](../../research/13_TEST_STRATEGY.md)).

## Truthful status

The continuation ran 14 controlled host-only application-policy fixtures and
then added actual JVM parser/native probes. It did not run Android runtime or
symlink filesystem mutation. The fixture pass remains wrapper-policy evidence;
actual parser rows are bounded to the cases recorded below.

| Controlled result | Classification | Remaining limitation |
|---|---|---|
| 14 security cases | `PASS` for application-policy fixture harness | Candidate parser and real extraction integration open |
| 10k and 100k synthetic entry indexes | `PASS` for bounded host harness | Not parser, UI, Android, or device scale |
| Optional capability truth table | `PASS` for declaration model | No production wrapper exists; native capabilities remain absent |
| Local Junrar inspection | `LICENSE_REVIEW_REQUIRED` | No local Junrar repository/license/source was available to inspect |
| Native packaging probe | `NO_LOCAL_ARTIFACT` / `UNRESOLVED_4K_ONLY` | No AAR/ELF or 16 KiB measurement; inherited P0 was 4 KiB only |

## Review finding dispositions — continuation 2026-09-18

### Rejected false hypothesis: `fixtures/duplicate.tsv`

| Field | Record |
|---|---|
| Finding | `fixtures/duplicate.tsv` prevents archive closure or should be removed |
| Disposition | `REJECTED / FALSE HYPOTHESIS` |
| Reason | `duplicate.tsv` is intentional negative coverage and the harness passes with it present |
| Evidence | `tools/p05-004/test_archive_evidence_matrix.sh:27-32` requires duplicate-manifest rejection; fresh run exits `0` with `PASS: archive evidence matrix harness`; `OVERALL=NOT_CLOSED` comes from unresolved closure gates, not duplicate detection |
| Action | Fixture retained; no harness change made for this finding |

`PASS: archive evidence matrix harness` (the documented matrix harness behaves
as expected) and `OVERALL=NOT_CLOSED` (production-readiness evidence gates
remain) are distinct results and are not contradictory.

### Adversarial review (`the-fool`, evidence-audit mode)

Steelmaned thesis: the package truthfully supports a Java-first review
direction with explicit gates while remaining `OVERALL=NOT_CLOSED`.

| Attack | Outcome |
|---|---|
| Duplicate fixture finding correctly rejected? | Confirmed: rejection test is explicit at `test_archive_evidence_matrix.sh:27-32` and reproduced green |
| Junrar licensing understated? | No: `LICENSE_REVIEW_REQUIRED` with absent local repository/license/source, RAR creation unsupported, engineering register not legal advice; final license classification `EXTERNAL_LICENSE_REVIEW_REQUIRED` |
| Pixel execution confused with 16 KiB proof? | No: `NATIVE_16K=UNRESOLVED_4K_ONLY`; 4 KiB observation must not be relabeled as 16 KiB compatibility |
| Static ELF evidence overclaimed? | No: local AAR/ELF counts are `0`/`0`; absence finding only, no `readelf` claim |
| Fixture coverage called security proof? | No: wording is `tested corpus failed safely under the enforced application policy`; parser rows stay `UNKNOWN`/`NOT_TESTED` |
| Scale extrapolated? | No: synthetic host metadata-index only; parser/UI/Android/device scale explicitly open; maximum actually tested scale is the synthetic 100k entry index |
| Java-first preferred without evidence? | The actual host evidence supports a direction, but retained required-format and license/native gates keep the family `NOT_READY`; no dependency selected, no freeze executed |
| libarchive dismissed prematurely? | Finding retained: libarchive remains `UNRESOLVED` until the retained Java-first matrix is closed; no native alternative was added |
| Freeze blocker mislabeled as release-only? | Corrected: Junrar license, zstd source/build/runtime, and the incomplete retained matrix remain Technology Freeze blockers; no Technology Freeze is executed here |

### Code review (continuation diff)

Intent: extend closure evidence with bounded host-only probes while keeping
`OVERALL=NOT_CLOSED`. No critical issues: documentation plus disposable
standard-library-only harnesses; no production code, no secrets, no
dependency declarations; `duplicate.tsv` preserved; negative testing intact;
no P05-001/002/003 files touched. Verdict: Approve.

## Real parser hostile-input findings — 2026-09-18

| Engine/case | Actual behavior | Interpretation and gate |
|---|---|---|
| Commons ZIP traversal names | Parser returned `../escape.txt`, `/absolute.txt`, and a normalization edge name | Parser acceptance is not extraction permission; wrapper containment remains required. |
| Commons/Zip4j truncated ZIP | Controlled `EOFException` / `ZipException` | Positive bounded behavior for the tested fixture; not corpus closure. |
| Junrar `parent-dir.rar` | Listed/extracted the parent-directory fixture | Requires application containment and output-policy enforcement. |
| Junrar `mkdir-escape.rar` | `CorruptHeaderException` during extraction | Controlled rejection for this fixture. |
| Junrar corrupted header | Accepted archive with zero entries, `hasBrokenHeaders=true`, two header failures | Wrapper must treat broken-header state as an error/diagnostic, not successful empty archive. |
| Junrar password fixtures | Missing/wrong password produced controlled Java exceptions; correct `junrar` password extracted | Error normalization is feasible for tested cases; broad encrypted/multipart corpus remains open. |
| zstd-jni malformed inputs | Controlled `ZstdIOException`; truncated frames emitted partial output before failure | Caller must write to partial output and discard on failure; no host crash/hang observed. |

The actual-engine harness never intentionally exhausted the host. The stalled
first compressed 100,000-entry attempt was terminated by its 120-second
watchdog; the optimized stored/Zip64 generator completed the 100,000-entry
case. This is a measurement boundary, not a pass inferred from 10,000 entries.

## 2026-09-18 process anomaly disposition

The existing `test_archive_evidence_matrix.sh` was initially run through a
30-second non-interactive command window and appeared hung. Reproduction with a
single TTY process completed in approximately 65 seconds and exited `0`, after
six `javac` invocations, negative-case runs, synthetic 100k validation, Junrar
inspection, and native scan. Two overlapping parent-launched runs were
terminated; no repository files were changed. Root cause was bounded command
timeout plus overlapping expensive JVM invocations, not a harness correctness
failure. The final verification uses a single serialized run.
