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
- libarchive was not adopted; its CORE_V1 disposition is `NOT_JUSTIFIED_FOR_CORE_V1`
  because no concrete Java-first capability gap was demonstrated;
- zstd-jni was not accepted as a distributable native dependency;
- Junrar licensing was not finally approved;
- native crash isolation and 16 KiB runtime compatibility were not closed;
- production safety thresholds and 100k+ indexing strategy were not frozen.

The unresolved list matches the branch-local P0 reconciliation and archive boundary record ([`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:51-62`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md), [`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:110-125`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md)).

## Recommended next evidence sequence

1. Freeze exact candidate artifacts and transitive manifests.
2. Run the full licensed/generated format and adversarial corpus.
3. Measure direct, SAF seekable/non-seekable, and remote/range access.
4. Measure candidate-specific 10k/100k listing, first-entry latency, solid-entry latency, and memory; the current synthetic host result is not a parser result.
5. If a concrete Java-first gap remains, perform reproducible ABI/16 KiB/size/crash testing on Android 12–16 for the justified native candidate.
6. Revisit the candidate matrix and only then decide whether a dependency freeze is warranted.

The branch-local P0 readiness assessment specifically calls for archive production-candidate closure including licensing, packaging/native acceptance, security corpus, and the supported format matrix ([`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:146-159`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md)); the archive boundary retains 10k/100k listing, SAF/remote origins, and native 16 KiB checks as required evidence ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:43-56`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md)).

## Final disposition

`P05-004 ARCHITECTURE REVIEW INPUT: NOT_READY`

`P05-004 PRODUCTION CANDIDATE CLOSURE: NOT CLOSED`

The real-evidence continuation supports a Java-first evidence path with explicit
gates, but not a freeze-ready technology family. Commons Compress, Zip4j, and Junrar 8.1.1 were actually invoked on host
fixtures; provider evidence makes spooling/seek costs visible rather than
claiming universal provider neutrality. zstd-jni 1.5.7-17 has static 16 KiB
ELF alignment across all four AAR ABIs, and the disposable final APK passed
16 KiB zip alignment plus real Pixel 7a 4 KiB native/TAR.ZST/SAF acceptance.
The authorized API 36 16 KiB guest confirmed `PAGE_SIZE=16384` but its package
service failed with `Broken pipe (32)` during installation, so 16 KiB app
runtime evidence is `NOT_COMPLETED`, not a pass or a zstd failure. Its
source/build attestation remains open. Junrar remains
`EXTERNAL_LICENSE_REVIEW_REQUIRED`, RAR creation remains unsupported, and the
protected authority treats the unresolved Junrar license as
`RAR_FEATURE_FREEZE_BLOCKING_ONLY` while exact RAR scope remains open.
libarchive is `NOT_JUSTIFIED_FOR_CORE_V1` after a bounded CMake configuration
attempt; no library or comparator binary was built or invoked. No concrete
Java-first gap was demonstrated; this is not a global rejection.
The disposable manifest remains `OVERALL=NOT_CLOSED` because the remaining
family gates can still change the selected stack.

## Technology Freeze reassessment input

`NO` at the CORE_V1 technology-family level: zstd source/build and 16 KiB
runtime acceptance, and selected retained provider/format matrix remain capable
of changing the selected family. The unresolved Junrar license is currently a
RAR-feature freeze blocker, not a whole-family blocker, because protected
authority leaves exact RAR scope and engine selection open. The physical Pixel
evidence is explicitly 4 KiB; the authorized 16 KiB
guest only proved environment page size and packaging before its Package
Manager/system-server failed during install. This is a readiness assessment
only; Technology Freeze itself was not executed, exact production dependency
versions were not frozen, and no production OmniFile module was initialized.

## Final campaign status

`P05_FINAL_CLOSURE_INCOMPLETE — zstd source/build provenance, trustworthy 16 KiB app runtime evidence, and the retained provider/format/security matrix remain open; Junrar/UnRAR remains RAR_FEATURE_FREEZE_BLOCKING_ONLY pending exact RAR scope and legal review; the authorized 16 KiB guest failed during APK installation with Package Manager Broken pipe (32).`
