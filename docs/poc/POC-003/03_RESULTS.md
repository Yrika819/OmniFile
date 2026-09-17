# POC-003 — Results

Status: COMPLETE

Raw evidence:
- `poc/POC-003-archive-engines/results/host-results.jsonl`
- `poc/POC-003-archive-engines/results/pixel7a-api36-full-results.jsonl`

## Measured facts — Pixel 7a / Android 16 API 36

| Engine / capability | Result |
|---|---|
| Commons Compress ZIP normal / ZIP64 / 10k / 100k listing | YES |
| Commons Compress ZIP direct entry open | YES |
| Commons Compress encrypted ZIP read | NO — encrypted entries reported unsupported |
| Zip4j AES-256 ZIP read | YES |
| Zip4j traditional ZipCrypto read | YES |
| Zip4j split ZIP create/read | YES — 7 segments, 4 entries in synthetic test |
| TAR / TAR.GZ / TAR.BZ2 / TAR.XZ | YES |
| TAR.ZST with zstd-jni Android AAR | YES |
| 7z normal / solid list and entry open | YES |
| encrypted 7z with password | YES |
| RAR4 / RAR5 | YES |
| RAR4 / RAR5 solid | YES |
| RAR multipart fixtures | YES |
| RAR4 / RAR5 password fixtures | YES |
| RAR5 encrypted headers | YES |
| RAR creation through Junrar | NO |
| libarchive Android runtime | NOT_TESTED |

## Security-oriented measured facts

- Synthetic ZIP traversal names were rejected by the PoC containment guard; Zip4j's built-in extraction also threw on `../escape.txt` and did not escape the target directory.
- The synthetic ZIP symlink/link-then-write case did not escape the target directory. Zip4j created the symlink entry and then failed to create children through it; this is not treated as a sufficient universal policy, so explicit link handling remains required.
- TAR traversal and TAR symlink entries were identified/rejected by the PoC policy.
- Junrar traversal fixtures were identified by the PoC containment guard.
- The 64 MiB highly-compressible ZIP fixture was stopped by a 1 MiB streaming expansion limit for both Commons Compress and Zip4j paths.
- Truncated ZIP, TAR and 7z produced failure signals. Junrar's corrupt-header fixture reported two broken header failures rather than silently appearing clean.

## Performance observations

These are single-run PoC measurements, not production benchmarks.

Pixel 7a:
- Commons Compress ZIP: 10k listing 252 ms; 100k listing 1683 ms; measured retained-heap delta about 4.37 MiB and 37.7 MiB respectively.
- Zip4j: 10k listing 226 ms; 100k listing 2224 ms; measured retained-heap delta about 2.06 MiB and 20.4 MiB respectively.
- Commons Compress 7z: 10k fixture listing 161 ms.
- Commons Compress 7z 64 MiB highly-compressible extraction: about 89.6 ms / 714.7 MiB/s. This value is fixture-specific and must not be generalized to arbitrary archives.
- TAR.ZST two-entry list: 5 ms after the Android zstd native library was provided.

## Observed anomalies

- A pure-Java Android bundle intentionally omitting the zstd native dependency failed only TAR.ZST with `NoClassDefFoundError`; adding the official Android AAR classes and arm64-v8a native library changed the same test to PASS.
- `app_process` initially aborted when D8 input contained Java 26 class files. Recompiling the harness with `javac --release 17` resolved it. This was a PoC build-input issue, not an archive-format failure.

## Inference

A single archive library does not cover the full desired matrix well. The measured combination that survived is Commons Compress for broad formats/7z/TAR, Zip4j for encrypted/split ZIP, and Junrar for RAR reading, with a separate extraction-safety layer.

## Recommendation input, not a freeze

Keep the multi-engine architecture viable for the next architecture review. Do not freeze exact versions or APIs from these measurements alone.

## Unresolved questions

- libarchive Android build, JNI boundary, binary size, crash isolation, patch burden and 16 KiB-page-size behavior remain NOT_TESTED.
- 100k-entry 7z and very large real-world solid archives were not tested.
- cancellation latency under long-running solid-archive decompression needs a dedicated follow-up.
- physical Android 16 device used a 4 KiB page size; 16 KiB runtime compatibility was not measured.