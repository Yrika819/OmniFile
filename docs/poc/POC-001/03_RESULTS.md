# POC-001 — Results

Status: PARTIAL — direct storage and descriptor capability evidence complete on API 31 and API 36; user-selected SAF and physical-removable/provider matrices remain unmeasured because of environment limitations.

Architecture authority: `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`

## Runtime environments

|Environment|Android|API|Image / model|Result scope|
|---|---:|---:|---|---|
|AVD `poc_filemanager_api31`|12|31|Google APIs x86_64, `sdk_gphone64_x86_64`|direct shared storage + synthetic DocumentsProvider pipe|
|AVD `poc_filemanager_api36`|16|36|Google APIs x86_64, `sdk_gphone64_x86_64`|direct shared storage + synthetic DocumentsProvider pipe|
|Physical Android device|—|—|No device attached at campaign start|NOT_TESTED|
|SD card / USB OTG|—|—|No removable hardware attached|NOT_TESTED|
|Cloud-backed DocumentsProvider|—|—|None available in test environment|NOT_TESTED|

Exact build fingerprints are preserved in the raw `results/*-direct-pipe.jsonl` and `*-getprop.txt` captures.

## Capability matrix

Legend: YES / NO / CONDITIONAL / PROVIDER_DEPENDENT / UNKNOWN / NOT_TESTED.

|Provider / surface|Sequential Read|Seek|Random Read|Writable|Rename / move|Stable identity observed|FD type|Notes|
|---|---|---|---|---|---|---|---|---|
|API 31 direct shared storage|YES|YES|YES|YES|YES in tested same filesystem|YES for tested rename/move (`fileKey` unchanged)|REGULAR|Emulator result only; not universal filesystem authority|
|API 36 direct shared storage|YES|YES|YES|YES|YES in tested same filesystem|YES for tested rename/move (`fileKey` unchanged)|REGULAR|Emulator result only; not universal filesystem authority|
|Synthetic DocumentsProvider pipe, API 31|YES|NO|NO from same descriptor|NO in this fixture|NOT_TESTED|Provider-defined|FIFO|`lseek` -> `ESPIPE`|
|Synthetic DocumentsProvider pipe, API 36|YES|NO|NO from same descriptor|NO in this fixture|NOT_TESTED|Provider-defined|FIFO|`lseek` -> `ESPIPE`|
|User-selected local SAF tree|NOT_TESTED|NOT_TESTED|NOT_TESTED|NOT_TESTED|NOT_TESTED|UNKNOWN|UNKNOWN|DocumentsUI/System UI ANR prevented valid grant selection|
|Physical SD / USB|NOT_TESTED|NOT_TESTED|NOT_TESTED|NOT_TESTED|NOT_TESTED|UNKNOWN|UNKNOWN|No hardware attached|
|Cloud-backed SAF provider|NOT_TESTED|NOT_TESTED|NOT_TESTED|NOT_TESTED|NOT_TESTED|UNKNOWN|UNKNOWN|No provider available|

## Direct storage measurements

### Android 12 / API 31

- 64 MiB deterministic sequential read: 40.830946 ms, approximately 1567.33 MiB/s on the emulator.
- Random seek to byte offset 33,554,432: successful; measured seek/read probe 75.945 microseconds.
- Regular `ParcelFileDescriptor`: `fdType=REGULAR`, `statSize=67,108,864`, `lseek` successful.
- Same-parent rename: successful; recorded file key `(dev=28,ino=237617)` before and after; measured 23,119.843 microseconds.
- Cross-directory move within the test root: successful; same recorded file key before and after; measured 11,453.388 microseconds.
- >4 GiB sparse request: requested and observed 4,831,838,208 bytes; 427.164476 ms.
- Cooperative cancellation: stopped after 655,360 of 67,108,864 bytes.

### Android 16 / API 36

- 64 MiB deterministic sequential read: 24.380178 ms, approximately 2624.67 MiB/s on the emulator.
- Random seek to byte offset 33,554,432: successful; measured seek/read probe 84.846 microseconds.
- Regular `ParcelFileDescriptor`: `fdType=REGULAR`, `statSize=67,108,864`, `lseek` successful.
- Same-parent rename: successful; recorded file key `(dev=4a,ino=229453)` before and after; measured 15,648.261 microseconds.
- Cross-directory move within the test root: successful; same recorded file key before and after; measured 27,478.905 microseconds.
- >4 GiB sparse request: requested and observed 4,831,838,208 bytes; 6.204571 ms.
- Cooperative cancellation: stopped after 524,288 of 67,108,864 bytes.

Throughput and latency values above are emulator measurements and are not device-performance targets.

## Filename fixtures

The same pattern was observed on API 31 and API 36 direct shared storage:

|Fixture|API 31|API 36|
|---|---|---|
|zero byte|PASS|PASS|
|spaces|PASS|PASS|
|Unicode Japanese name|PASS|PASS|
|emoji name|PASS|PASS|
|shell metacharacters `$;[]{}`|PASS|PASS|
|long 224-ish character basename plus extension|PASS|PASS|
|name containing double quote|FAIL — `EPERM`|FAIL — `EPERM`|
|name containing newline|FAIL — `EPERM`|FAIL — `EPERM`|
|nested directories|PASS|PASS|

This is measured Android emulator shared-storage behavior, not a statement that all underlying Linux filesystems reject those characters.

## Descriptor evidence

The synthetic DocumentsProvider returned a real `ParcelFileDescriptor` backed by a pipe.

API 31:
- `fdType=FIFO`;
- sequential read succeeded;
- `Os.lseek` failed with errno 29, `ESPIPE (Illegal seek)`.

API 36:
- `fdType=FIFO`;
- sequential read succeeded;
- `Os.lseek` failed with errno 29, `ESPIPE (Illegal seek)`.

### Measured fact

A provider can return a valid file descriptor that supports sequential reading but does not support seeking.

### Inference

Architecture code must probe/advertise descriptor seekability independently from descriptor availability. `openFileDescriptor()` success is insufficient evidence for random access.

## Rename / move identity

On the two tested direct-storage emulator filesystems, the observed `fileKey` remained unchanged across both same-parent rename and cross-directory move within the same test filesystem.

### Measured fact

Identity continuity was observed for the tested direct filesystem operations.

### Limitation

This does not establish SAF URI stability, cloud object identity, removable-media identity, or cross-filesystem atomicity. Those remain provider-specific and unmeasured here.

## Modification time

Appending after a delay produced a non-decreasing modification timestamp on both tested direct-storage environments.

No claim is made about timestamp precision or reliability across providers.

## Revocation / disconnect

- Direct special-access revocation: recorded as CONDITIONAL in the harness; no complete revoke/recover sequence was accepted as final evidence.
- User-selected SAF persisted-grant revocation: NOT_TESTED because a valid picker grant could not be established.
- SD/USB disconnect: NOT_TESTED.
- Cloud/provider process disconnect: NOT_TESTED.

## Result integrity

No `NOT_TESTED` item has been converted into PASS. No single emulator success is treated as a universal Android storage guarantee.