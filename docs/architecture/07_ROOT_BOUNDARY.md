# Architecture V1 — Root Boundary

Status: ROOT PRINCIPLES FROZEN / ROOT IMPLEMENTATION UNFROZEN

Source authority: `03_ROOT_ACCESS.md`, `11_SECURITY.md`, and research synthesis SHA `b03a2ea99f24206f847f513fa4106e90268f3fc4`.

## Root is optional — `ACCEPTED`

Root is an additive provider/capability boundary. It is never required for baseline operation and never becomes a global application mode.

The normal application remains independently functional when:

- no root manager exists;
- `su` is unavailable;
- authorization is denied;
- authorization is revoked;
- a privileged service/shell crashes;
- SELinux or mount policy denies an individual operation;
- a protected filesystem remains read-only despite root.

Root-required baseline browsing or application startup — `REJECTED`.

## Root authorization failure isolation — `ACCEPTED`

Root detection/authorization failure must not corrupt or disable Local/SAF/network/cloud/archive behavior.

The architecture treats root manager absence, denial, timeout, revocation, SELinux denial, and mount/read-only failure as explicit root-provider outcomes.

## Isolated privileged provider boundary — `ACCEPTED`

Enumeration and privileged file operations should occur through a clearly isolated root-capable provider/transport so elevation is explicit and accidental privilege crossing is difficult.

The root provider may expose richer capabilities such as:

- protected-path read/write where actually permitted;
- owner/group/mode metadata;
- chmod/chown;
- symlink object/target operations;
- elevated copy/move/delete;
- mount/filesystem diagnostics where useful.

These remain capability-dependent even after root authorization.

## Root does not imply universal power — `ACCEPTED`

Root UID does not guarantee every operation succeeds. SELinux, mount namespaces, verified/read-only partitions, kernel policy, app-profile restrictions, and filesystem semantics remain relevant.

The UI/provider must report actual capability rather than claim that root makes `/system`, `/vendor`, or every protected location writable.

## Shell-string file transport — `REJECTED` as the normal architecture

Arbitrary filenames may contain spaces, quotes, newlines, shell metacharacters, Unicode, and option-like prefixes. Concatenating untrusted path/name text into shell command strings creates injection and correctness risk.

Replacement principle: prefer structured privileged file APIs/services/channels. If an unavoidable shell command is later introduced, it requires a narrow separately reviewed argument/framing layer and adversarial filename tests.

Binary file content must not be multiplexed through fragile delimiter-framed textual shell output.

## Structured privileged service/NIO direction

Structured root-service/NIO-like transport is a preferred research direction, but the exact implementation is `POC_REQUIRED`.

No root library is selected here.

## Exact root implementation — `POC_REQUIRED`

The later root PoC must evaluate at least:

- Magisk authorization and persistent privileged behavior;
- KernelSU app-profile/capability/SELinux behavior;
- APatch behavior;
- root service/process lifecycle;
- mount namespace differences;
- large-file streaming;
- cancellation/timeouts;
- symlink/chmod/chown behavior;
- protected-path access;
- optional descriptor/FD bridging;
- denial/revocation recovery.

## Candidate technologies remain unfrozen

The following are explicitly **not** architecture decisions:

- libsu;
- custom root service;
- persistent privileged shell;
- FD bridge;
- exact Binder/IPC shape;
- exact root process model;
- Magisk-specific private APIs;
- KernelSU-specific private APIs;
- APatch-specific private APIs.

Their state is `POC_REQUIRED` or `DEFERRED` until the root implementation campaign.

## Root file streaming — `ACCEPTED`

Large root reads/writes use bounded binary streaming or structured remote-file APIs. Whole-file in-memory buffering and base64-as-normal-file-transport are `REJECTED`.

## Mount-namespace consistency — `ACCEPTED`

The architecture must not assume the ordinary process and privileged process see identical mounts. Root-provider enumeration and operations must be internally consistent with the privileged context used for the operation.

Exact mount-namespace handling — `POC_REQUIRED`.

## File descriptor bridge — `POC_REQUIRED`

Whether a privileged process can safely expose a useful descriptor to the non-root process for media/archive operations depends on Binder/SELinux/open-descriptor semantics and must not be assumed before device testing.

## Symlink policy — `ACCEPTED`

Root browsing distinguishes a symlink object from its target where available. Recursive destructive operations require an explicit link-follow policy and must not unexpectedly cross symlink boundaries.

## Protected/system paths — `ACCEPTED`

Destructive mutation of protected/system paths requires stronger UX safeguards than ordinary user-storage operations, consistent with `UI_DESIGN_V1.md`.

The architecture must make privileged elevation visible and intentional; hidden automatic elevation is `REJECTED`.

Exact warning text/confirmation UX — `DEFERRED` to implementation design, while the stronger-safeguard principle is frozen.

## Root audit/security rules — `ACCEPTED`

- no raw filename interpolation into arbitrary shell strings;
- no hidden elevation;
- preserve normal mode at all times;
- separate privileged lifecycle from UI lifecycle;
- bound stream memory;
- separate control/stdout/stderr/binary channels when shell/process transport exists;
- cancellation/timeout may mean partial side effects and require reconciliation;
- never disable SELinux as a workaround;
- revalidate target before destructive mutation where feasible;
- keep explicit symlink policy;
- do not persist root-related secrets in plaintext;
- avoid sensitive path/token leakage in logs.
