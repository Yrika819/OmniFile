# P05-002 Fault-Injection Matrix

`POC-ONLY — NOT PRODUCTION AUTHORITY`

| Case | Injected condition | Expected classification | Safety invariant |
|---|---|---|---|
| F1 | Partial contains a valid 8-byte source prefix; durable checkpoint says 4 bytes | `RESUME_FROM_PARTIAL`, offset 8 | Observed partial reality outranks stale checkpoint metadata |
| F2 | Provider is unavailable during restart | `BLOCKED_PROVIDER` | No blind replay while provider authority is absent |
| F3 | Persisted grant is revoked; source cannot be opened | `BLOCKED_PERMISSION` | Permission failure is not an automatic retry |
| F4 | Source version differs from durable record | `CONFLICT_SOURCE_CHANGED` | Do not continue from an old source/version assumption |
| F5 | Partial bytes differ from the source prefix | `RESTART_REQUIRED` | Do not append to or finalize corrupt partial output |
| F6 | Final destination matches expected content during move finalization; source still exists | `FINAL_DESTINATION_OBSERVED`, action `REQUIRE_EXPLICIT_SOURCE_DELETE` | Reconciliation never deletes the source |
| F7 | Final destination exists but has wrong content | `RESTART_REQUIRED` | Length/existence alone is not completion proof |
| F8 | No final or partial destination exists | `RESTART_REQUIRED`, offset 0 | Incomplete work is not silently marked complete |

The matrix is deterministic and host-only. It does not measure provider behavior, latency, descriptor shape, URI mutation, Android lifecycle behavior, or storage capacity.
