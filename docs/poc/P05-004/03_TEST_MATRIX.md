# P05-004 Format and Operation Evidence Matrix

## Required format matrix

The repository’s target matrix covers ZIP/ZIP64, 7z, RAR/RAR5, TAR and compressed TAR families, GZIP/BZIP2/XZ/Zstandard/LZ4, and CPIO ([`docs/research/04_ARCHIVE_FORMATS.md:14-31`](../../research/04_ARCHIVE_FORMATS.md)). The following status is the closure target, not a claim that every row has been executed in this worktree.

| Format/operation | Commons Compress | Zip4j | Junrar | zstd-jni | libarchive | Evidence state |
|---|---|---|---|---|---|---|
| ZIP / ZIP64 browse-read-extract | candidate | primary | n/a | n/a | candidate | P0 broad ZIP evidence; edge coverage remains bounded by fixture IDs |
| ZIP AES / legacy encryption | partial/format-specific | primary | n/a | n/a | format-specific | Zip4j P0 survival; full negative corpus remains open |
| split ZIP | partial | primary | n/a | n/a | format-specific | Zip4j P0 survival; missing/corrupt part cases remain open |
| TAR + GZIP/BZIP2/XZ | primary | n/a | n/a | n/a | alternative | Required fixture matrix; no new execution here |
| TAR.ZST | possible compressor path | n/a | n/a | codec path | alternative | Pixel path reported; native/page-size gate open |
| CPIO / LZ4 | primary candidate | n/a | n/a | n/a | alternative | Required by research; exact P0 row mapping absent |
| 7z basic | candidate | n/a | n/a | n/a | alternative | Broad P0 survival; exact format rows absent |
| 7z encrypted / solid / random entry | candidate | n/a | n/a | n/a | alternative | `UNKNOWN` / further fixture evidence required |
| RAR4 / RAR5 read-extract | n/a | n/a | primary | alternative | Junrar technical survival; full edge corpus open |
| RAR encrypted / solid / multipart | n/a | n/a | primary | alternative | `UNKNOWN` / long-running cancellation open |
| RAR creation | unsupported | unsupported | unsupported | unsupported | not promised | Must not be inferred from read support |

The architecture requires comparison of encrypted/split ZIP, TAR/compressors, 7z solid/random behavior, RAR4/RAR5 edge cases, malformed input, large listings, provider origins, and native compatibility where applicable ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:43-56`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md)).

## Evidence record schema

Each executed fixture should produce:

```text
candidate, exact artifact/version, fixture ID, source kind, operation,
expected result, observed result, status, elapsed time, peak managed memory,
peak native memory, output digest/length, failure class, log/artifact reference
```

The current disposable manifest compresses this to `candidate`, `gate`, `status`, and `note`; it is a closure guard, not a replacement for raw fixture output. The continuation additionally records `scale-10k`, `security-fixture-policy`, `optional-capability-wrapper`, and native `native-packaging`. The two scale rows distinguish synthetic metadata handling from candidate parser behavior.

## Pass/fail rules

- A candidate cannot be `PASS` without functional, provider, security, scale, and license/provenance gates.
- A native candidate additionally requires exact provenance, ABI inventory, reproducible build, 16 KiB, crash, size, symbols, update-ownership, and notices evidence.
- Security closure requires individual negative-case gates for traversal, symlinks/TOCTOU, expansion, duplicates, truncation, malformed input, cancellation, nesting, password failures, and multipart failures.
- Any missing, `NOT_TESTED`, or `UNKNOWN` material gate prevents closure.
- A passing parser/codec fixture does not pass extraction containment, symlink, expansion, or cancellation policy.
- The 14-case security fixture result is application-policy evidence only; it does not change the individual candidate parser/security rows.
- `4096` page-size evidence is not `16384` page-size evidence. A native candidate without a local AAR/ELF and 16 KiB measurement remains open.
