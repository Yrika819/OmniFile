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
| Java-first preferred without evidence? | The actual host evidence supports a conditional direction, but retained format/provider and native gates keep the family `NOT_READY`; Junrar remains a RAR-feature gate, no dependency selected, no freeze executed |
| libarchive dismissed prematurely? | The focused Java/provider evidence exposed no required CORE_V1 capability that needs native libarchive; bounded CMake configuration was attempted but stopped before a comparator binary. Final disposition is `NOT_JUSTIFIED_FOR_CORE_V1`, not a global rejection. |
| Freeze blocker mislabeled as release-only? | Corrected: zstd source/build/runtime and the incomplete retained matrix remain family-level Technology Freeze blockers; Junrar is `RAR_FEATURE_FREEZE_BLOCKING_ONLY`; no Technology Freeze is executed here |

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

## Fresh independent audit dispositions — 2026-09-18

Six independent read-only audit agents reviewed the exact artifact, license,
native, Android, provider, security, and technology-severity questions. Their
common findings were reconciled here:

| Finding | Disposition |
|---|---|
| Junrar/UnRAR terms | Exact tagged license permits RAR handling but prohibits recreating RAR compression; technical outcome remains `EXTERNAL_LICENSE_REVIEW_REQUIRED`. Protected architecture keeps the engine stack `POC_REQUIRED` and exact RAR/Junrar selection open, so the severity is `RAR_FEATURE_FREEZE_BLOCKING_ONLY`, not a blocker for the entire archive technology family. |
| zstd-jni source/build | AAR/JAR/static ABI evidence is real; no `v1.5.7-17` source tag or release-to-source/compiler attestation was found. The candidate build recipe and CI toolchain differ, so source/build provenance remains a Technology Freeze blocker if TAR.ZST is retained. |
| provider/security | The 14-case fixture harness is application-policy-only. Actual parser probes do not prove extraction security. Required missing decision evidence includes real extraction-root containment, partial-output cleanup, cancellation, split/multipart/solid cases, and SAF seekable-versus-pipe behavior. |
| libarchive | An empty native row is not a defect by itself. The parent completed a bounded libarchive configuration/decision check, not a libarchive comparison or approval. No Java-first failure requiring native libarchive was demonstrated; the disposition is `NOT_JUSTIFIED_FOR_CORE_V1` and may reopen only on a concrete Java-first gap. |
| technology severity | No new architecture blocker was found. Junrar legal disposition is a RAR-feature freeze blocker only because protected authority leaves exact engine selection and the RAR subset open. zstd source/build/runtime acceptance if TAR.ZST is retained and the retained provider/format/security matrix remain family-selection gates. Other P05 lifecycle, containment, packaging, notice, API, and human-acceptance gaps remain implementation or release gates. |

The audit did not authorize dependency selection or Technology Freeze. One
additional requested audit slot was unavailable because the host reported the
subagent thread limit; the parent completed the bounded libarchive
configuration/decision check and recorded its result rather than treating the
slot as approval.

The requested fresh final review-agent pass was `REVIEW_NOT_COMPLETED`: the
same host-level subagent thread limit rejected the dispatch. No independent
review approval is claimed; the parent performed only the documented manual
diff, matrix, provenance, and scope checks.

## Device-priority evidence disposition — 2026-09-18

The physical Pixel 7a pass is `PASS` for the exact disposable APK’s arm64
native load, valid decode, TAR.ZST decode, repeated use, controlled malformed
errors, process restart, and one real Downloads DocumentsProvider/PFD URI.
It is `NOT_16K_RUNTIME_EVIDENCE` because the authoritative runtime query was
`PAGE_SIZE=4096`. The SDK’s apparent 16 KiB image directory was empty; no
emulator was installed before the Pixel pass and no 16 KiB runtime claim is
made.

## Authorized 16 KiB runtime attempt — 2026-09-19

The campaign then installed exactly one official API 36 `google_apis_ps16k`
x86_64 image and created one isolated AVD. The guest identity was real and
repeatable (`PAGE_SIZE=16384`), and the exact x86_64 APK passed packaging and
signature checks. The guest nevertheless failed the only permitted install
retry with `cmd: Failure calling service package: Broken pipe (32)` after
Package Manager/system-server instability. The app never reached a trustworthy
launch/decode state.

Disposition: `16K_RUNTIME_NOT_COMPLETED / ENVIRONMENT_BLOCKER`. This closes
neither native-load nor zstd/TAR.ZST runtime acceptance. It is not evidence of
a zstd defect, and no additional image or repeated emulator retry is authorized
by this campaign.

## Junrar severity re-audit — 2026-09-19

The architecture authority requires the later engine comparison to cover
RAR4/RAR5, including encrypted, solid, and multipart inputs; it does not
authorize a RAR encoder or freeze Junrar. The exact Junrar 8.1.1 artifact and
tagged source remain `EXTERNAL_LICENSE_REVIEW_REQUIRED`: the UnRAR-derived
terms permit RAR handling but prohibit recreating RAR compression, and no
formal legal clearance is present. Protected authority leaves exact engine
selection and the proven RAR subset open, so the disposition is
`RAR_FEATURE_FREEZE_BLOCKING_ONLY`, not `FAMILY_FREEZE_BLOCKING`. If product
authority later makes RAR read/extract mandatory for Core_V1, this issue must
be promoted before that feature is frozen; it does not currently block the
whole archive technology family.
