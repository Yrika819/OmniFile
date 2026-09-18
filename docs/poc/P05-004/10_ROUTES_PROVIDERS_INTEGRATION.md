# Retained Routes, Provider Matrix, and Integration Contracts — 2026-09-19

## Retained engine routes (evidence-backed)

| Format | Retained primary route | Evidence | Remaining gate |
|---|---|---|---|
| ZIP / Zip64 browse+extract | Commons Compress (`ZipArchiveInputStream` sequential; `ZipFile` seekable) | 4-entry traversal-visible listing; truncated→`EOFException`; 10k compressed (`2,494 ms` list) and 100k stored/Zip64 (`2,022 ms`, `100,000` entries) both enumerated (`04_RESULTS.md`) | split/multipart ZIP corpus; produces `IMPLEMENTATION_GATE` |
| Encrypted ZIP | Zip4j (`ZipInputStream` sequential; `ZipFile` local-file full-feature) | AES-256 correct-password read; wrong password→`ZipException: Wrong Password`; truncated→`Zip headers not found`; 100k open `2,437 ms` | split/multipart + provider-spool corpus (`IMPLEMENTATION_GATE`) |
| Split ZIP | Zip4j (only candidate with split support) | Not directly exercised | bounded split corpus before feature enablement (`IMPLEMENTATION_GATE`) |
| TAR | Commons Compress | 3 entries listed+read sequentially | — |
| GZ / TAR.GZ | Commons Compress | 3 entries listed+read sequentially (both layers) | — |
| BZ2 / TAR.BZ2 | Commons Compress | **Byte-proven 2026-09-19**: `commons-bz2-roundtrip` PASS; `commons-tar-bz2-via-spool` PASS (`RealEngineSpoolHarness` 13/13) | scale/limits corpus (`IMPLEMENTATION_GATE`) |
| XZ / TAR.XZ | Commons Compress (+ xz 1.10, `95c63c1a…`) | **Byte-proven 2026-09-19**: `commons-xz-roundtrip` PASS; `commons-tar-xz-via-spool` PASS | scale/limits corpus (`IMPLEMENTATION_GATE`) |
| ZSTD / TAR.ZST | Commons TAR parser + zstd-jni decode assist | Host: valid decode, 6 malformed inputs→`ZstdIOException` (≤36 partial bytes); Pixel 4 KiB: valid+TAR.ZST+20× + controlled errors; 16 KiB x86_64: 7/7 PASS (`08_16K_RUNTIME_CLOSURE.md`); AAR static `p_align=0x4000` all ABIs | production NOTICE/packaging revalidation (`RELEASE_GATE`) |
| RAR4 / RAR5 read | Junrar 8.1.1 (technical only) | 2-entry list+extract both formats (file); sequential-stream list for tested fixtures; `parent-dir` accepted (needs containment), `mkdir-escape`→`CorruptHeaderException`, corrupt-header→zero entries + `hasBrokenHeaders`, password fixtures map correctly | license (`EXTERNAL_LICENSE_REVIEW_REQUIRED`, RAR-feature gate); solid/multipart corpus (`IMPLEMENTATION_GATE`) |
| CPIO read | Commons Compress (`FORMAT_NEW` round-trip PASS) | Evidence-only: `commons-cpio-roundtrip` PASS (`RealEngineSpoolHarness`); **not** a retained CORE_V1 route, recorded as libarchive-counter-evidence | — |

No Cartesian re-testing was performed; only the retained routes above matter.

## Provider capability matrix (selected routes)

| Capability | Commons ZIP/TAR stream | Commons `ZipFile`/`TarFile` seekable | Zip4j `ZipInputStream` | Zip4j `ZipFile` | Junrar `Archive(File)` | Junrar `Archive(InputStream)` | zstd+TAR |
|---|---|---|---|---|---|---|---|
| DIRECT_SEQUENTIAL_STREAM | yes (proven) | n/a | yes (proven) | no (local-file) | n/a | partial (fixture-dependent; zero headers observed for some RAR4/5 shapes) | yes (proven) |
| REOPENABLE_STREAM | via spool | via reopen | via spool | via spool | via spool | via spool | via replay/spool |
| SEEKABLE_SOURCE | not required | required (15 seeks ZIP / 10 TAR observed) | not required | required | required | not sufficient alone | not provided |
| TEMP_SPOOL | not needed for pipes | n/a (direct file) | not needed for pipes | **required** for non-path source | **required** for SAF/non-path | **required** (stream-to-temp restored success) | staging for random access |

Architecture rule preserved: valid descriptor ≠ seekable source. No engine
is forced to support direct SAF; the bounded spool adapter satisfies
seek/reopen/local-file semantics.

## Real-engine spool integration (`RealEngineSpoolHarness`, 13/13 PASS)

Against the exact artifacts (Commons 1.28.0, Zip4j 2.11.6, zstd-jni
1.5.7-17, xz 1.10):

- Commons `ZipArchiveInputStream` (sequential) and `ZipFile` (seekable
  reopen) over the SAME spooled temp give identical 4-entry listings
  including traversal names (parser acceptance confirmed on the real
  engine; policy split holds);
- Zip4j `ZipFile` opens the spooled temp (4 entries) — the local-file
  full-feature path is spool-satisfiable;
- zstd-jni decodes bytes read through the spool (roundtrip PASS);
- TAR.BZ2 / TAR.XZ end-to-end through spool PASS; CPIO round-trip PASS;
- raw-crafted duplicate-name zip: Commons lists both `dup.txt` entries →
  policy `FIRST_ACCEPT` + `DUPLICATE_SKIP`;
- TAR-native symlink (`evil-link` → `../outside.txt`): detected via
  `isSymbolicLink()` → `REJECTED:SYMLINK`, never materialized.
  (ZIP symlink unix-mode did not survive this Commons ZIP authoring path —
  read `unixmode=0`; ZIP/Junrar symlink coverage stays gated.)

## Spool adapter PoC (`SpoolContainmentHarness`, 18/18 PASS)

Demonstrated host/JVM (stdlib-only policy layer, engine-neutral).
The name layer here does not evaluate symlink metadata
(`isSymbolicLink`); link rejection is proven on TAR-native symlinks in
`RealEngineSpoolHarness` (see above):

- sequential input → unique temp (`p05-spool-*.bin`), 8 KiB buffer,
  64-bit counters (100,000 bytes exact), complete-before-consume;
  `Spool.copy` closes in `finally` and self-cleans on the failure/cancel
  path (failure-path cleanup is therefore structural, not caller luck —
  fixed after independent review);
- cleanup on success, on failure (`SIMULATED_IO_ERROR`), and on cancel
  (0 ms latency for pre-cancelled; mid-extraction cancel observed at
  entry 9 with `CANCELLED` state, no residue);
- no assumption that source URI == local path (all inputs are streams).

## Containment (application policy, tested corpus)

- `../escape.txt` → `REJECTED:ESCAPE`; `/absolute.txt` →
  `REJECTED:ABSOLUTE_PATH`; `C:/drive.txt` → `REJECTED:DRIVE_PATH`;
  NUL rejected; no escape artifact on disk;
- `dir/../normalized.txt` → normalized inside root, extracted (policy:
  allow-in-root);
- `a\b\win.txt` → backslash kept LITERAL (single file directly under
  root; verified no `a/b/` subdir created). Rationale: on Android/Linux
  backslash is an ordinary filename character; flattening would create
  surprise directories and widen collision surface;
- `dup.txt` second occurrence → `DUPLICATE_SKIP` (never silent overwrite);
- `conflict` (file) then `conflict/file.txt` →
  `CONFLICT_SKIP:parent-is-file`;
- symlink/link policy: REJECT, never materialize — demonstrated on
  TAR-native symlink (`RealEngineSpoolHarness`); ZIP-attribute and Junrar
  symlink paths remain an `IMPLEMENTATION_GATE` fixture.

Parser acceptance is not extraction permission: Commons returned
`../escape.txt` and `/absolute.txt` as listing names; Junrar extracted
`parent-dir.rar`. The wrapper policy above is the boundary.

## Partial-output cleanup classification

- Cancel during spool / enumeration / mid-extraction → `CLEAN_NO_OUTPUT`
  (temp deleted; nothing written after cancel in PoC).
- Malformed later entry after earlier writes is exercised as a hard
  mid-extraction I/O failure during the second entry (1 file written,
  failure observed, operation-level delete) → `CLEANED_AFTER_FAILURE`.
  (An earlier variant truncated only the central directory, which
  `ZipInputStream` ignores — replaced after independent review.)
- Wrong password / resource-limit rejection → `CLEANED_AFTER_FAILURE`
  (destination entry deleted; temp deleted).
- No `UNSAFE_RESIDUAL_OUTPUT` was observed in the tested corpus.
  Production rule: partial destination is never presented as complete;
  orphan recovery on restart is a durable-state reconciliation item below.

## Cancellation

Bounded and deterministic: cancel flag observed at entry granularity
(entry 9 of 9-entry hostile set) and at byte-stream granularity (spool
loop checks per 8 KiB chunk). Instantaneous pre-emption is not required
and not claimed; completion latency after request was unmeasurable at
PoC scale (<1 ms) and must be re-measured on device-scale inputs
(`IMPLEMENTATION_GATE`).

## Lifecycle / recovery boundary (contract only, no engine rebuild)

P05-001/P05-002 evidence is used read-only. Archive integration must
persist enough to reconcile after process death (conceptual fields, exact
production schema NOT frozen):

```text
source identity (provider-scoped URI, not raw path)
destination identity
operation kind (browse / extract)
engine + format route
spool identity (temp path or marker)
completion phase (spooled / enumerating / extracting-n-of-m / cleaning)
cleanup/reconciliation requirement flag
```

Rule preserved: persisted checkpoint ≠ storage truth — on restart the
destination is re-scanned and partial output is cleaned or resumed per
policy before any completion is reported.

## Error normalization (feasibility proven)

Observed → category mapping demonstrated in-PoC:

```text
EOFException / Zip headers-not-found / CorruptHeaderException / ZstdException
  -> INVALID_ARCHIVE / CORRUPT_OR_TRUNCATED
InitDeciphererFailedException / missing password -> PASSWORD_REQUIRED
WrongPasswordException / ZipException:Wrong Password / CrcErrorException(on wrong pw)
  -> BAD_PASSWORD
RESOURCE_LIMIT -> RESOURCE_LIMIT_EXCEEDED
cancel flag -> CANCELLED
SIMULATED_IO_ERROR / provider open/read failure -> IO_FAILURE
unsupported route -> UNSUPPORTED_FORMAT / UNSUPPORTED_FEATURE
```

Kotlin type names are NOT frozen. Broad encrypted/multipart corpus
remains an `IMPLEMENTATION_GATE`.

## Hostile corpus (bounded representative pass, selected routes)

Covered: traversal, absolute, drive-like, mixed separators, duplicates,
file/dir conflict, malformed header (Junrar corrupt-header), truncated ZIP
(EOF/headers-not-found), truncated/mutated/bad-magic/zero/random zstd (6
shapes), bad password (ZIP AES + RAR4/5), ratio/entry-count stress (100k
synthetic index + 100k real stored/Zip64), cancellation. Nested-archive
depth accounting and symlink-filesystem mutation remain
`IMPLEMENTATION_GATE`s. No intentional disk/memory exhaustion was performed;
compressed-100k attempt hit its 120 s watchdog once (measurement boundary,
not a pass).
