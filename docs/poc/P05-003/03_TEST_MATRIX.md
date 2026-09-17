# P05-003 Test and Evidence Matrix

Status vocabulary: `PASS` means fresh evidence supports only the stated row; `FAIL` means the stated row was exercised and failed; `NOT_TESTED` means no execution occurred; `BLOCKED` means the required environment was unavailable; `N/A` means the row is outside this disposable harness.

| Layer | Scenario | Required evidence | Status | Evidence location |
|---|---|---|---|---|
| Host unit | Seekable native descriptor | Resolver chooses direct descriptor and no cache requirement | PASS | `tools/p05_003_harness/test_p05_003_contract.py` |
| Host unit | Requested random/range access | Resolver chooses random/range source | PASS | Same test file |
| Host unit | Seekable non-random source | Resolver chooses seekable source | PASS | Same test file |
| Host unit | Sequential-only source | Non-seek request selects cached sequential mode | PASS | Same test file |
| Host unit | Invalid seek claim | Sequential-only source is unsupported for required seek | PASS | Same test file |
| Host unit | No capabilities | Resolver returns explicit unsupported result | PASS | Same test file |
| Host unit | Cache identity | Provider/object/version included; locator/token excluded | PASS | Same test file |
| Android component | Direct file/descriptor through actual Media3 datasource | Android project, exact dependency, fixture, and logs | NOT_TESTED | No Android project/toolchain in worktree |
| Android instrumentation | SAF regular-file descriptor | Representative provider and seek probe | NOT_TESTED | No Android instrumentation target; ADB prohibited |
| Android instrumentation | SAF pipe-backed descriptor | Seek failure/fallback behavior | NOT_TESTED | Same blocker |
| Device | Android API 31 local playback | Startup, pause, seek, metadata, cleanup | NOT_TESTED | No device run |
| Device | Android API 36 local playback | Same plus current target behavior | NOT_TESTED | No device run |
| Remote POC | SMB FLAC | Startup, beginning/middle/end seek, bytes, cache, reconnect, version change | NOT_TESTED | POC-004 remains separately authorized work |
| Remote POC | SFTP/WebDAV/cloud range | Same provider-specific range/reconnect evidence | NOT_TESTED | No provider harness or credentials |
| Lifecycle | Process death and session reconnect | Active session survives UI recreation and state reconnects | NOT_TESTED | No MediaSession implementation |
| Security | Token/path redaction | Durable identity and logs exclude transient credentials | PASS | Cache-key unit test covers identity exclusion only; log audit not run |
| Fidelity | Human audio acceptance | Listening on exact installed artifact | NOT_TESTED | No artifact/device run |

The seven `PASS` rows are contract-model results only and must not be promoted to playback claims.
