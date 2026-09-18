# Adversarial Review Dispositions — 2026-09-19

Reviewer: fresh `the-fool` (Test-the-Evidence mode), no authorship.
Status of each finding: ACCEPTED (fixed with new evidence), ACCEPTED
(documented as explicit limitation/gate), or REJECTED (with reason).

## 1. 16 KiB runtime N=1, disposable chain — ACCEPTED + remediated

- Reproducibility: probe source + manifest + exact build recipe committed
  (`tools/p05-004/16k-probe/`). The v1 defect (`.so` without Java classes)
  is documented as a packaging rule with a `dexdump` verification step.
  Byte-identical APKs are not promised (debug cert, timestamps); behavior
  reproduction (exact 8 `P05_RESULT` lines) is the claim.
- Sample size stays N=1 full pass + no-crash logcat sweep (zero
  `FATAL EXCEPTION` from the probe package across 65k+ logcat lines).
  A second independent 16 KiB run + relaunch outside a death window is an
  explicit RELEASE_GATE, not claimed.
- `Broken pipe` root-cause stands on three independent observations:
  (a) `pm install` of a pushed APK reproduces it (not ADB streaming);
  (b) server-side `installation completed` log vs client timeout;
  (c) `DeadSystemException` cascades with 21.5 s `PackageManager` monitor
  contention and guest load 15–65.

## 2. Provenance inference to `b00e4d3` — ACCEPTED as limitation

Inference remains circumstantial (chronology + version file + GPG commit,
no tag/attestation). Counter-recorded: if tag `v1.5.7-17` later appears
elsewhere, or `1.5.7-18` shows `-17` was transient, the mapping must be
re-done — filed as a revalidation trigger, and production must hash-pin
the exact artifact (RELEASE_GATE). The `SUFFICIENT_WITH_NONREPRODUCIBLE_BUILD`
label already encodes this; no stronger claim is made.

## 3. Source build removed 16 KiB flags; smoke mixed artifacts — ACCEPTED + remediated

- Documented: the removed flags are Linux-`lld`-only; macOS `ld` rejects
  them, so upstream 16 KiB link intent was not compiled on host. The 16 KiB
  evidence for the decision rests instead on the published artifact
  (independent ELF parse: all `PT_LOAD p_align=0x4000`, congruent) plus the
  real `PAGE_SIZE=16384` 7/7-expected-outcome run — not on the host build.
- Smoke-test lib identity verified: JVM log shows
  `Loaded library …/zstd-hostbuild/libzstd-jni-1.5.7-17.dylib` before
  `roundtrip=hello-p05-004`. The built lib, not the bundled one, was
  exercised.
- Trivial-frame scope acknowledged: host smoke covers API linkage only;
  TAR.ZST/malformed shapes are covered by the device runs.

## 4. Spool PoC stdlib-only — ACCEPTED + remediated with real engines

`RealEngineSpoolHarness` (13/13 PASS, exact artifacts) now proves:
Commons sequential AND seekable-`ZipFile` over the same spooled temp with
identical listings; Zip4j `ZipFile` over spooled temp (4 entries);
zstd-jni decode of bytes read through the spool. Junrar shape-sensitivity
caveat retained: spool restores success only for tested fixtures;
solid/multipart RAR stays an IMPLEMENTATION_GATE.

## 5. Containment/cleanup/cancel/lifecycle/error-map limits — PARTLY remediated, rest gated

- Backslash: policy changed to keep-literal (no subdir creation verified);
  documented as the Android/Linux-correct choice.
- Duplicates through a real parser: raw-crafted same-name zip → Commons
  lists both → policy yields `FIRST_ACCEPT` + `DUPLICATE_SKIP`. Proven.
- Symlink: TAR-native `LF_SYMLINK` (`../outside.txt`) detected via
  `isSymbolicLink()` and rejected. ZIP-attribute and Junrar symlink paths
  remain IMPLEMENTATION_GATEs (plus a noted ZIP authoring quirk:
  symlink unix mode did not survive this Commons ZIP write path).
- Cancellation `<1 ms` stays PoC-scale only; device-scale latency is an
  IMPLEMENTATION_GATE. Kill-restart orphan recovery and exhaustion tests
  were not performed (explicitly out of scope); lifecycle stays a contract.
- Error map stays a feasibility table, not an end-to-end wrapper; real
  `CrcErrorException`-vs-password ambiguity resolution is an
  IMPLEMENTATION_GATE.

## 6. Zero-blocker reclassification as relabeling — REJECTED with record

The reclassification follows NEW evidence since `06_CONCLUSIONS.md`
(`NOT_READY`): the 16 KiB 7/7-expected-outcome run, provenance trace, host source build +
verified smoke, real-engine spool integration, and the 18/18 + 13/13 PoCs.
Each former blocker maps to a named evidence file. The 16 KiB relaunch
downgrade still requires independent-review confirmation — that is the
Phase 22 review now requested; if it rejects, the downgrade is reverted
and P05 stays open.

## 7. Libarchive circularity + machine/human split — PARTLY remediated

Circularity reduced with positive evidence: BZ2/XZ byte routes,
TAR.BZ2/TAR.XZ end-to-end via spool, and CPIO round-trip all PASS on the
retained Commons path, so `NOT_JUSTIFIED_FOR_CORE_V1` now rests partly on
demonstrated coverage rather than pure absence. Remaining genuinely
untested (solid 7z/RAR, fuzz, device-scale) are named gates; a single
concrete fixture where Java-first fails reopens libarchive by rule.
Machine `UNRESOLVED`/`OVERALL=NOT_CLOSED` semantics are intentionally
unchanged (outcome B); the human `CLOSED_WITH_EXPLICIT_GATES` label lives
in this document series, and the synthesis records the split explicitly.
RAR isolation caveat accepted: license failure reshapes RAR scope/cost,
recorded as the RAR-feature gate.
