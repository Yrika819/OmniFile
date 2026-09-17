# P05-004 Architecture Impact

## Decisions confirmed by the evidence package

1. Archives remain virtual storage, with outer-source identity and inner-entry identity scoped to that archive.
2. Sequential, seekable, and random access remain distinct capabilities.
3. Extraction containment, symlink policy, expansion limits, malformed-input handling, cancellation, and bounded memory remain application-level architecture requirements.
4. A layered Java-first candidate direction remains lower integration risk than immediately adopting a broad native parser.
5. RAR creation remains outside the demonstrated V1 capability set.

These principles are already accepted/frozen in the architecture boundary and branch-local P0 reconciliation ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:7-41`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md), [`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:64-94`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md)).

## Decisions deliberately not made

- no production dependency was selected;
- no final version or wrapper API was frozen;
- libarchive was neither adopted nor rejected;
- zstd-jni was not accepted as a distributable native dependency;
- Junrar licensing was not finally approved;
- native crash isolation and 16 KiB compatibility were not closed;
- production safety thresholds and 100k+ indexing strategy were not frozen.

The unresolved list matches the branch-local P0 reconciliation and archive boundary record ([`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:51-62`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md), [`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:110-125`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md)).

## Recommended next evidence sequence

1. Freeze exact candidate artifacts and transitive manifests.
2. Run the full licensed/generated format and adversarial corpus.
3. Measure direct, SAF seekable/non-seekable, and remote/range access.
4. Measure 10k/100k listing, first-entry latency, solid-entry latency, and memory.
5. If native candidates remain justified, perform reproducible ABI/16 KiB/size/crash testing on Android 12–16.
6. Revisit the candidate matrix and only then decide whether a dependency freeze is warranted.

The branch-local P0 readiness assessment specifically calls for archive production-candidate closure including licensing, packaging/native acceptance, security corpus, and the supported format matrix ([`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:146-159`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md)); the archive boundary retains 10k/100k listing, SAF/remote origins, and native 16 KiB checks as required evidence ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:43-56`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md)).

## Final disposition

`P05-004 ARCHITECTURE REVIEW INPUT: READY_WITH_EXPLICIT_GATES`

`P05-004 PRODUCTION CANDIDATE CLOSURE: NOT CLOSED`

The evidence package supports a reviewable Java-first direction with explicit
security, provider-access, artifact/provenance, and scale gates. It does not
authorize Technology Freeze or production implementation. zstd-jni remains an
optional conditional native path; libarchive is `NOT_JUSTIFIED_FOR_CORE_V1`.
The disposable harness conservatively remains `OVERALL=NOT_CLOSED` until the
recorded gates are supplied.
