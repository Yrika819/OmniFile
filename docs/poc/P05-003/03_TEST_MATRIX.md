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
| Host unit | Runtime event contract | Ordered lifecycle events, positions/duration, and redacted failure detail | PASS | `tools/p05_003_harness/test_p05_003_contract.py` |
| Host unit | Sequential source contract | Fixed-chunk reads, EOF, reopen, and explicit unsupported seek | PASS | Same test file |
| Android build | Actual Media3/ExoPlayer PoC | Isolated APK compiles/packages with P05-only Media3 pin | PASS | `tools/p05_003_harness/media3-poc/` Gradle output |
| Android component | Direct local WAV/FLAC through actual Media3 datasource | Runtime playback, duration, seek, EOF, stop, exact dependency, fixture, and logs | PASS | Pixel 7a logcat; `local-file` and `local-flac` |
| Android instrumentation | SAF regular-file child descriptor | Representative provider playback and seek probe | PASS | Pixel 7a logcat; `saf-tree-child`, direct and sequential modes |
| Android instrumentation | SAF pipe-backed descriptor | Seek failure/fallback behavior | NOT_TESTED | No instrumentation run; ADB prohibited |
| Device | Android API 31 local playback | Startup, pause, seek, metadata, cleanup | NOT_TESTED | No device run |
| Device | Android API 36 local playback | Same plus current target behavior | PASS | Pixel 7a API 36; fresh installed PoC APK |
| Remote POC | SMB FLAC | Startup, beginning/middle/end seek, bytes, cache, reconnect, version change | NOT_TESTED | POC-004 remains separately authorized work |
| Remote POC | SFTP/WebDAV/cloud range | Same provider-specific range/reconnect evidence | NOT_TESTED | No provider harness or credentials |
| Lifecycle | Process death and session reconnect | Active session survives UI recreation and state reconnects | NOT_TESTED | No MediaSession implementation |
| Security | Cache identity excludes locator/token | Provider/object/version cache key excludes transient locator | PASS | `tools/p05_003_harness/test_p05_003_contract.py` |
| Security | Named query-secret redaction in logs | Durable identity and log strings exclude transient credentials supplied through named query parameters | PASS | Host regression plus recorder source/detail redaction; path/user-info/fragment credentials remain provider-specific follow-up |
| Fidelity | Human audio acceptance | Listening on exact installed artifact | NOT_TESTED | No artifact/device run |

Host `PASS` rows are contract-model results only. Runtime `PASS` rows are
limited to the exact Pixel 7a API 36 PoC run and are not production, API 31,
pipe-provider, remote-provider, or human-audio acceptance claims.
