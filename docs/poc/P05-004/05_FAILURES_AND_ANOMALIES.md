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

The continuation ran 14 controlled host-only application-policy fixtures. They
passed for the modeled outcomes, but no archive parser, native library, Android
runtime, symlink filesystem mutation, or device test was run. The fixtures
therefore support the wrapper-policy gate only; the individual parser/security
rows remain `UNKNOWN` or `NOT_TESTED` rather than being inferred from the
fixture pass or the 54-record P0 summary.

| Controlled result | Classification | Remaining limitation |
|---|---|---|
| 14 security cases | `PASS` for application-policy fixture harness | Candidate parser and real extraction integration open |
| 10k and 100k synthetic entry indexes | `PASS` for bounded host harness | Not parser, UI, Android, or device scale |
| Optional capability truth table | `PASS` for declaration model | No production wrapper exists; native capabilities remain absent |
| Local Junrar inspection | `LICENSE_REVIEW_REQUIRED` | No local Junrar repository/license/source was available to inspect |
| Native packaging probe | `NO_LOCAL_ARTIFACT` / `UNRESOLVED_4K_ONLY` | No AAR/ELF or 16 KiB measurement; inherited P0 was 4 KiB only |
