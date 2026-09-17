# P05-002 Limitations and Blockers

`POC-ONLY — NOT PRODUCTION AUTHORITY`

## What the host harness establishes

It establishes only that the selected reconciliation policy is executable and deterministic for the eight in-memory snapshots in `04_FAULT_INJECTION_MATRIX.md`. The tests make the source-preservation and stale-checkpoint rules observable without using user data or a live provider.

## What it does not establish

- Android SAF API behavior or provider conformance.
- Whether any provider exposes a seekable regular descriptor, writable pipe, atomic rename, or stable URI after rename/move.
- Persistable URI permission behavior across process death or reboot.
- Real executor termination, force-stop, power loss, filesystem corruption, ENOSPC, or partial write ordering.
- API 31 versus API 36 behavior, OEM differences, SD/USB disconnect, cloud-backed providers, or `Android/data` restrictions.
- A production persistence schema, checkpoint cadence, retry policy, executor mapping, UX, or performance budget.

## Blockers

1. No existing Android module/build project exists in this repository.
2. The local inspection found Java and Android SDK platform/build artifacts but no Gradle or Kotlin compiler executable in the inspected paths.
3. The requested boundary prohibits physical-device and ADB use, so runtime provider evidence cannot be collected.

The correct follow-up is a separately authorized disposable Android test project with explicit API/provider/device scope. This branch must not silently convert the host result into that missing evidence.
