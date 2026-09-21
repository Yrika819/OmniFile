# User-Visible Regression Audit

## Scope

- Review date: `2026-09-21`
- Requested outcome: `review only`
- Continuation: `report only`
- Scope reviewed: `commit range`
- Baseline: `development/core-v1-search-v1 / 35dcf37ccadc7f86182ca1b038b59015a5962bba`
- Target: `development/core-v1-app-shell-v1 / d2c1525e4abaad4ed79841a64b034bfdc85ffb77`
- Completion: `Complete within reviewed scope`
- Assumptions: `This is a post-implementation evidence-resolution review; no production/test source changed during this closure, and JBR 25.0.3 is the authoritative supported host when available.`

## Gate Snapshot

- Recommendation: `Pass`
- Completion: `Complete within reviewed scope`
- Why now: `The same-environment JDK 21 comparison has identical 11-test failure sets with zero VS06-only failures, while JBR 25.0.3 runs both full suites green.`
- Must-review now: `None`
- Findings count: `Block 0 | Discuss 0 | Watch 0 | Intentional 0`
- Coverage confidence: `high`
- Behavior graph coverage: `skipped - no production behavior delta in this evidence-only closure; direct-path and runtime evidence cover the reviewed surface`
- Biggest blind spot: `None for the host regression question`

## Complete Findings Index

No user-visible regression findings identified in the reviewed scope.

## Block

None.

## Discuss

None.

## Watch

None.

## Intentional Changes

None. The only closure changes are evidence/documentation normalization; no user-visible product behavior changed.

## Coverage Ledger

| Surface / path | Touched files or entry points | Status | Result | Evidence |
| --- | --- | --- | --- | --- |
| VS05 existing Local/Operation journey | Unchanged LocalStorage and Operation paths | `Reviewed - no user-visible regression found` | JDK 21 control failures are fully accounted for; JBR control is 80/80 green. | Exact control run, JUnit identities, source blob parity, provider probe. |
| VS06 shell/root/Search journey | Published VS06 source and 17 focused host tests | `Reviewed - no user-visible regression found` | JDK 21 adds zero failures over VS05; JBR complete suite is 97/97 and focused suite is 17/17. | Exact differential, focused XML, prior 37/37 Pixel evidence. |
| APK/device evidence | Existing debug and androidTest APK artifacts | `Not user-visible` | Hashes match documented artifacts; no rebuild required because source identity is unchanged. | SHA-256 and APK metadata. |
| Closure documentation | `VERTICAL_SLICE_06_ADAPTIVE_APP_SHELL.md` | `Not user-visible` | Prior vague wording is replaced with exact environment and failure-set evidence. | Documentation diff check. |

## Evidence Appendix

### Behavior Graph Deltas

None built - the reviewed closure changes evidence wording only and does not change a product entry point, guard, transform, or output effect.

### Diff Inventory

| File or area | Classification | User-visible path considered |
| --- | --- | --- |
| LocalStorage/Operation implementation and tests | `dependency` / `test-only` | Existing Files mutation and operation paths |
| VS06 shell/root/Search implementation and tests | `surface` / `test-only` | Home, Search, Files contextual origin, root aggregation |
| `docs/production/VERTICAL_SLICE_06_ADAPTIVE_APP_SHELL.md` | `docs-only` | None; evidence only |
| New closure review artifacts | `docs-only` | None; review evidence only |

### Candidate Sweep Log

| Candidate | Decision | Reason |
| --- | --- | --- |
| `The 11 JDK 21 failures are a VS06 user-visible regression` | `dismissed` | VS05 and VS06 normalized failure sets are exactly equal; failure-path source/test blobs are identical. |
| `A same count hides different failures` | `dismissed` | All 11 class/method identities match; exception families and source mapping match. |
| `JBR green results are unavailable` | `dismissed` | Android Studio JBR 25.0.3 exists; VS05 is 80/80 and VS06 is 97/97. |
| `Focused VS06 evidence is 18 tests` | `dismissed` | Explicit XML count is 17: 7 + 5 + 3 + 2. |

### Verification Commands

- JDK 21 forced serialized full host suite on VS05 -> `80 total, 69 passed, 11 failed, 0 errors, 0 skipped`.
- JDK 21 forced serialized full host suite on VS06 -> `97 total, 86 passed, 11 failed, 0 errors, 0 skipped`.
- Normalized exact failure-set comparison -> `11 common, 0 VS05-only, 0 VS06-only, 0 same-name/different-root-cause`.
- JBR 25.0.3 forced serialized full host suite on VS05 -> `80/80 PASS`.
- JBR 25.0.3 forced serialized full host suite on VS06 -> `97/97 PASS`.
- JBR focused VS06 command -> `17/17 PASS`.
- Direct provider probe -> JDK 21 non-secure directory stream; JBR 25.0.3 secure directory stream.
- Existing APK SHA-256 -> both documented hashes matched.

### Supporting Code Links

| ID | Role | Link | Why it matters |
| --- | --- | --- | --- |
| `E1` | `provider contract` | [`LocalStorageProvider.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-app-shell-v1/app/src/main/java/com/omnifile/storage/LocalStorageProvider.kt#L429-L435) | The secure-directory capability boundary that differs by JDK. |
| `E2` | `VS06 focused path` | [`FilesSearchIntegrationTest.kt`](/Users/yuta/Desktop/File%20Manager-worktrees/omnifile-app-shell-v1/app/src/test/java/com/omnifile/files/FilesSearchIntegrationTest.kt#L23-L55) | Contextual Files origin regression coverage remains green. |

### Blind Spots

| Area | Risk introduced by the blind spot | What would resolve it |
| --- | --- | --- |
| `None for this closure question` | `No material host differential uncertainty remains.` | `No follow-up required before VS06 closure.` |

### Report Self-Check

- `yes` Every reviewed user-visible or unknown-impact surface appears in the Coverage Ledger.
- `yes` No findings appear in action sections or the index.
- `yes` No user-visible behavior graph was needed because there was no production delta.
- `yes` Blind spots are explicit.
- `yes` Recommendation follows the regression-review mapping.
