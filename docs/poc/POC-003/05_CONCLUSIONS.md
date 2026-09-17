# POC-003 — Conclusions

Status: COMPLETE

## Measured fact

The host run and the physical Pixel 7a / Android 16 API 36 run both completed the archive matrix. The final Pixel run produced 54 result records and `CONTROL/RUN_DONE=PASS`.

The following candidate split survived the measured matrix:

- Apache Commons Compress 1.28.0: normal/ZIP64 ZIP, TAR, TAR.GZ, TAR.BZ2, TAR.XZ, TAR.ZST when zstd-jni is supplied, normal/solid/password 7z, large-entry streaming, 10k-entry 7z listing, and malformed-input signaling.
- Zip4j 2.11.6: normal/ZIP64 ZIP plus AES-256, traditional ZipCrypto, split ZIP creation/read, built-in traversal rejection for the measured fixture, and bounded streaming.
- Junrar 8.1.1: RAR4/RAR5, solid archives, password-protected archives, encrypted RAR5 headers, and multipart fixtures for reading/extraction.

RAR creation is not supported by Junrar and was not claimed.

## Security conclusion

No tested engine is treated as the extraction security boundary. A File Manager-side policy remains required for:

- normalized target containment;
- explicit symlink/hard-link policy;
- entry-count and nesting limits;
- streaming expansion limits;
- cancellation;
- malformed-input normalization and diagnostics.

The safe synthetic traversal, symlink/link-then-write, decompression-limit and malformed fixtures support keeping these responsibilities outside the archive codec/parser itself.

## Performance conclusion

The measured libraries were fast enough to keep virtual archive browsing technically viable on the Pixel 7a for the tested 10k-entry cases. The 100k ZIP tests were also usable as a reference measurement, but memory growth was material enough that production indexing/paging behavior should not assume whole-archive metadata is free.

Single-run numbers are evidence only, not production SLAs.

## Native dependency conclusion

TAR.ZST required zstd-jni on Android. The official `zstd-jni 1.5.7-16` Android AAR contained native libraries for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`; the Pixel 7a arm64 library successfully enabled the TAR.ZST test.

This does not establish 16 KiB-page-size compatibility. The physical Pixel 7a used a 4096-byte page size during this run.

`libarchive` itself was not built or executed on Android and remains `NOT_TESTED` for Android build viability, JNI overhead, crash isolation, binary size, patch burden, and 16 KiB page-size behavior.

## License conclusion

The Junrar source corpus used for RAR fixtures carries the UnRAR license terms, including the restriction that the sources may not be used to recreate the proprietary RAR compression algorithm. This makes RAR support a separate explicit license-review gate even though the measured read/extract path worked technically.

RAR creation remains outside the measured and recommended V1 capability set.

## Candidate disposition

- Commons Compress: `SURVIVED` for broad archive parsing/extraction evaluation.
- Zip4j: `SURVIVED` for encrypted and split ZIP evaluation.
- Junrar: `SURVIVED_TECHNICALLY_WITH_LICENSE_GATE` for RAR read/extract.
- libarchive: `UNRESOLVED / NOT_TESTED` on Android.
- single-library universal archive engine: `CHALLENGED` by the measured capability gaps.

These are architecture-review inputs, not a Technology Freeze.

## Unresolved questions

- Android 16 KiB page-size runtime behavior for native archive/compression dependencies.
- libarchive Android build/runtime cost and failure isolation.
- cancellation latency for long-running solid archives.
- 100k-entry 7z/RAR and much larger real-world solid archives.
- fuzz/adversarial corpus beyond the bounded synthetic and Junrar test fixtures.
- final production dependency versions and API wrappers.

## Artifact classification

- `poc/POC-003-archive-engines/src/ArchivePoc.java`: `REFERENCE_ONLY`.
- fixture/dependency acquisition scripts: `REFERENCE_ONLY`.
- generated archives, downloaded JAR/AAR/native binaries, compiled DEX/classes: `DISPOSABLE`.
- raw JSONL results: `REFERENCE_ONLY`.
- security-policy patterns demonstrated by the harness: `POTENTIALLY_REUSABLE_AFTER_REVIEW`, but not production authority.

Production Android application implementation was not started.