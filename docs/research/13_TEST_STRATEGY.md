# Future Test Strategy

Status: COMPLETE — strategy only; **no tests were implemented in this phase**  
Last verified: 2026-09-17  
Compatibility target under research: Android 12–16 / API 31–36

## Classification legend

- **FACT** — documented Android/testing behavior.
- **INFERENCE** — consequence for this product.
- **RECOMMENDATION** — proposed future test direction; not frozen implementation authority.
- **UNRESOLVED** — requires implementation/prototype evidence.

## Test philosophy

A file manager combines several failure-prone domains: Android permissions/storage, filesystem semantics, root, hostile archives/media, network protocols, OAuth/cloud APIs, long-running operations and adaptive UI. The strategy therefore needs a broad pyramid rather than relying on end-to-end tests alone.

**FACT:** Android's current testing guidance recommends choosing the lowest test layer that provides the required feedback, while retaining device-level tests for integration and release-candidate behavior.

Sources:
- https://developer.android.com/training/testing
- https://developer.android.com/training/testing/fundamentals/strategies

**RECOMMENDATION:** Keep pure/provider-independent logic heavily unit-tested; reserve instrumented/physical-device tests for behavior that genuinely depends on Android framework, filesystem, codecs, root or real protocols.

## Proposed future layers

| Layer | Main purpose | Typical environment |
|---|---|---|
| Unit | capability rules, planners, path/archive validation, conflict/retry state machines | host JVM |
| Component | provider adapter against fake backend, persistence/recovery components, parser wrappers | host or controlled Android environment |
| Integration | actual SQLite/provider/protocol adapter + fixture server/filesystem | emulator/device/local test server |
| Android instrumentation | SAF, ContentResolver, FDs, permissions, services/jobs, intents, UI/system integration | emulator/physical device |
| End-to-end / release candidate | complete user journeys under realistic devices/backends | physical devices + selected emulators |

Framework/dependency choices are deliberately **not frozen** here.

## Unit tests

High-value pure logic targets:

- storage capability negotiation;
- operation planning (`native move` vs `copy -> verify -> delete`);
- retry/idempotency classification;
- checkpoint/recovery state transitions;
- destination name/conflict policy;
- path containment and archive traversal rejection;
- filename/display normalization rules without changing identity;
- sorting/tie-break behavior;
- progress aggregation and 64-bit boundaries;
- cache key/version logic;
- OAuth/provider error mapping;
- resumable-transfer checkpoint arithmetic;
- archive expansion/file-count limits;
- security log redaction.

Property/fuzz-style generated tests are especially valuable for names, paths, sizes and state transitions.

## Provider contract tests

Every provider implementation should run against a shared behavioral contract that tests only capabilities it advertises.

Example contract categories:
- list/stat/read;
- sequential and random/range reads;
- create/replace/append;
- rename/move/copy semantics;
- deletion/mkdir;
- stable identity after rename;
- version/conflict token behavior;
- cancellation;
- source disappearance;
- expected structured errors.

**RECOMMENDATION:** A provider that does not support atomic rename should not fail a test for “atomic rename”; instead, a contract test must ensure it does **not advertise** that guarantee.

## Filesystem fixtures

Create generated, reproducible fixtures rather than committing huge trees.

Required classes:
- empty directory;
- 10k and 100k entry directories;
- 100k tiny files;
- nested tree;
- zero-byte files;
- >2 GiB and >4 GiB boundary cases;
- >100 GB sparse/generated file where environment permits;
- symlink/broken-symlink trees;
- optional sparse-file fixtures;
- case-collision/case-folding variants where filesystem permits;
- read-only and permission-denied files/directories;
- source removed during I/O;
- destination near/full storage.

Do not commit massive binary fixtures to Git. Generate deterministically in isolated test storage.

## Filename corpus

At minimum:
- Japanese and non-Latin scripts;
- emoji and ZWJ sequences;
- composed/decomposed combining-character equivalents;
- RTL/bidirectional text;
- spaces/newlines;
- leading hyphen/dot;
- shell metacharacters;
- percent/hash/question-mark URI-sensitive characters;
- maximum/near-maximum filename lengths;
- path-component collisions under case-insensitive filesystems.

The test oracle must distinguish display equality from actual provider identity.

## Malformed archive corpus

Required archive families:
- ZIP/ZIP64/AES/split;
- 7z/encrypted/solid;
- RAR4/RAR5/encrypted/multipart/solid;
- TAR + GZIP/BZIP2/XZ/Zstd;
- CPIO/LZ4 where supported.

Adversarial cases:
- absolute-path entries;
- `../` traversal;
- mixed separator traversal;
- symlink escape;
- duplicate names;
- truncated central/header data;
- invalid sizes/offsets;
- very high compression ratio;
- enormous declared file size;
- huge file count;
- nested archives;
- password failure;
- multipart missing/corrupt parts.

**RECOMMENDATION:** Preserve a small curated regression corpus in Git only where licensing permits; generate large bombs safely with strict test-space limits. Fuzzing inputs should be isolated from user storage.

## Large-file fixtures

Use generated content and sparse files when the behavior under test does not require physical bytes. Use real allocated files for throughput/storage-full tests.

Required boundary checks:
- 2 GiB - 1 / 2 GiB / 2 GiB + 1;
- 4 GiB - 1 / 4 GiB / 4 GiB + 1;
- 64-bit progress arithmetic;
- interruption/resume near start/middle/end;
- hash/verification without integer truncation.

## Network fault simulation

Build controlled SMB/SFTP/WebDAV test servers or containers outside the Android process and inject:
- latency;
- jitter;
- packet loss/connection reset;
- mid-stream disconnect;
- server restart;
- authentication expiry/denial;
- wrong TLS certificate/hostname;
- SFTP host-key change;
- request timeout;
- malformed/oversized metadata response;
- WebDAV incorrect/missing Range/ETag behavior;
- SMB signing/encryption requirements;
- source mutation during read;
- destination conflict during upload.

**RECOMMENDATION:** Tests should assert reconciliation/idempotency, not merely that a retry loop eventually runs.

## Fake cloud providers

Provider-independent operation logic should use deterministic fakes that model:
- pagination;
- range reads;
- resumable upload sessions;
- server-side copy/move;
- expiring session tokens;
- 401/403 auth failures;
- 409/412 conflicts;
- quota/rate-limit/Retry-After;
- source version change;
- eventually visible metadata if provider behavior demands it;
- shared/team-drive boundaries.

Real Google Drive/OneDrive/Dropbox accounts should be a separate integration suite and must use dedicated test accounts/projects with no production credentials.

## Root / non-root matrix

Baseline tests must pass with **no root installed and root denied**.

Later root test matrix:
- no `su`;
- root manager present, user denies;
- user grants;
- authorization revoked during later operation;
- Magisk;
- KernelSU;
- APatch;
- SELinux denial;
- read-only mount;
- root process/service death;
- arbitrary hostile filenames;
- large root stream;
- chmod/chown/symlink successes and expected failures.

No destructive tests should target real system/user data. Use isolated test trees or disposable emulator/test-device partitions only.

## Permission-denied scenarios

Cover:
- no all-files access;
- all-files access revoked;
- SAF grant absent;
- persisted SAF grant revoked/provider removed;
- read-only document provider;
- Android notification permission denied where relevant;
- local-network access/discovery permission denied where Android version requires/introduces it;
- cloud OAuth scope insufficient;
- network credential invalid;
- FileProvider/intent grant missing.

Expected behavior: capability becomes unavailable/operation produces structured failure; unrelated file-manager functions remain usable.

## Process-death recovery

Critical test category for Operation Manager.

Inject process death at boundaries such as:
- before destination creation;
- after partial create;
- after checkpoint but before durable provider flush;
- immediately after data complete but before final rename/commit;
- after destination finalize but before operation record marks complete;
- during cross-provider move before source delete;
- immediately after source delete;
- during archive extraction between entries;
- during cloud upload with active resumable session;
- during foreground/UIDT notification lifecycle.

On restart, assert that recovery **reconciles actual provider state** and does not blindly duplicate destructive actions.

## Android version matrix: 12 / 13 / 14 / 15 / 16

Minimum platform coverage must retain API 31–36 even if Android 16 is the main physical-device target.

### Android 12 / API 31
- scoped-storage baseline;
- background FGS restrictions;
- SAF/direct-storage behavior;
- no UIDT API.

### Android 13 / API 33
- media/notification behavior changes relevant to app UX;
- continue storage/SAF baseline validation.

### Android 14 / API 34
- UIDT availability;
- stricter foreground-service type declarations;
- modern media/image capabilities.

### Android 15 / API 35
- foreground-service timeout behavior (`dataSync`/`mediaProcessing`);
- edge-to-edge transition behavior where relevant.

### Android 16 / API 36
- primary visual/device validation;
- target-36 behavior changes;
- mandatory modern edge-to-edge behavior for target 36;
- predictive-back behavior;
- JobScheduler/WorkManager quota changes;
- local-network protection evolution;
- 16 KB native compatibility on suitable devices/emulators.

Sources:
- https://developer.android.com/about/versions/16/behavior-changes-16
- https://developer.android.com/about/versions/16/behavior-changes-all
- https://developer.android.com/develop/background-work/background-tasks/uidt

## Emulator tests

Emulators are valuable for:
- API-level matrix;
- deterministic permissions;
- process death/reboot;
- low-storage scenarios where controllable;
- UI/window sizes;
- instrumented storage fixtures;
- target behavior changes.

Android Gradle Managed Devices and Firebase Test Lab are future options, but CI/CD is explicitly out of scope in this research phase.

References:
- https://developer.android.com/studio/test/gradle-managed-devices
- https://firebase.google.com/docs/test-lab

## Physical-device tests

Physical devices are mandatory for behaviors an emulator cannot reliably represent:
- real removable SD/USB OTG attach/eject;
- vendor storage/FUSE quirks;
- real root ecosystems;
- thermal throttling during archive/media conversion;
- hardware MediaCodec/HDR/AV1/HEVC behavior;
- Wi-Fi roaming/transitions;
- background/battery/vendor process management;
- real 16 KB page-size devices where available;
- keyboard/mouse/trackpad/foldable hardware where practical.

**RECOMMENDATION:** Android 16 can be the primary physical-device focus, but retain at least one Android 12-class compatibility path before release.

## UI/adaptive/accessibility tests

Cover:
- phone portrait/landscape;
- compact/medium/expanded widths;
- tablet/foldable/two-pane;
- predictive back;
- edge-to-edge/gesture/three-button navigation;
- dynamic color/light/dark/high contrast;
- large font/display scaling;
- TalkBack semantics/manual validation;
- keyboard traversal/shortcuts;
- mouse right-click/context behavior;
- selection mode and destructive confirmations;
- 10k+ directory model under scroll/thumbnail load.

## Performance tests

Benchmark rather than assert microsecond-exact timings across devices. Track distributions/regressions for:
- time to first directory rows;
- 10k/100k enumeration;
- peak heap/native memory;
- scroll frame/jank;
- sort latency;
- 1M-index search latency;
- thumbnail throughput;
- copy throughput and tiny-file ops/sec;
- operation checkpoint overhead;
- playback seek latency from NAS/cloud;
- archive browse first-entry and random-entry latency;
- transcode speed/thermal behavior.

Official benchmark overview:
- https://developer.android.com/topic/performance/benchmarking/benchmarking-overview

## Security tests

- archive/path traversal;
- symlink/race behavior;
- shell-injection filename corpus;
- malformed intents and exported components;
- TLS invalid chains/hostnames;
- SSH host-key changes;
- log/token/path redaction;
- OAuth state/redirect validation;
- provider response bounds;
- malicious media/archive parsing corpus;
- dependency vulnerability review.

## Release-candidate matrix concept

A later release candidate should include a smaller number of comprehensive scenarios across:
- Android 12 phone;
- Android 16 primary phone;
- Android 16 tablet/large screen or foldable;
- non-root and root-enabled dedicated test device;
- local/SAF/removable;
- SMB/SFTP/WebDAV;
- at least the enabled first-class cloud providers.

This is not a CI/CD design and does not choose a device farm today.

## Test-data safety rules

- Never point destructive tests at real user folders.
- Test credentials/accounts contain no personal data.
- Root tests use disposable isolated trees.
- Storage-full tests run in dedicated volume/image/sandbox.
- Decompression-bomb tests have hard host/device disk limits.
- Keep production OAuth credentials out of fixtures and Git.

## Future quality gates

Before any public/release candidate:
1. provider contract tests pass for every advertised capability;
2. Android 12–16 compatibility suite passes for applicable behavior;
3. archive malformed/security corpus passes;
4. process-death recovery passes at all commit boundaries;
5. permission-denied/root-denied paths remain usable;
6. performance budgets are measured against documented fixtures;
7. network/cloud fault tests show no source loss/duplicate destructive action;
8. security checklist from `11_SECURITY.md` is reviewed.

## Recommendation summary

**Strong:** test contracts around capabilities and recovery state machines, not only UI paths.  
**Strong:** Android 12 compatibility remains a real test target while Android 16 receives primary real-device attention.  
**Strong:** physical-device testing is mandatory for root, removable storage, codecs, network transitions and thermal/background behavior.  
**Strong:** malformed archives/files, hostile names and process-death injection must be first-class regression suites.  
**Postpone:** exact testing frameworks, CI device farm and coverage thresholds until implementation/module architecture is reviewed.
