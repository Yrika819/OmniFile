# Optional Root Access Research

Status: COMPLETE — research/design only; no root experiment performed  
Last verified: 2026-09-17

## Classification legend

- **FACT** — supported by cited project/platform documentation.
- **INFERENCE** — consequence of documented behavior.
- **RECOMMENDATION** — proposed direction, not frozen.
- **UNRESOLVED** — requires later controlled device validation.

## Boundary: baseline vs optional capability

### APPLICATION BASELINE REQUIREMENTS

The future File Manager must remain fully usable without root:

- shared/local storage within Android permissions
- SAF sources
- removable storage where exposed normally
- archives
- network providers
- cloud providers
- playback/conversion where ordinary Android APIs allow

### ROOT CAPABILITY

Root may add:

- access to otherwise protected filesystem paths
- richer Unix metadata
- elevated copy/move/delete
- chmod/chown
- symlink management
- selected operations requiring privileged file access

**RECOMMENDATION:** Root must be an opt-in storage provider/capability, never a global application mode and never a hidden prerequisite.

## Modern Android root environments

### Magisk

**FACT:** Magisk remains a major systemless-root ecosystem and exposes superuser authorization to apps through the `su` model. Mount namespaces and modules can affect what a privileged process sees.

Reference:
- https://github.com/topjohnwu/Magisk

### KernelSU

**FACT:** KernelSU provides kernel-based root and an app-profile model. Root profiles can constrain UID/GID/groups, Linux capabilities and SELinux context; “root granted” therefore should not be interpreted as a guarantee that every filesystem action will succeed.

References:
- https://kernelsu.org/
- https://github.com/tiann/KernelSU/blob/main/website/docs/guide/app-profile.md

### APatch

**FACT:** APatch is another kernel-oriented Android root solution with superuser/access-control concepts. Its existence reinforces that the application should avoid coupling to one root manager's private APIs unless unavoidable.

References:
- https://apatch.dev/
- https://github.com/bmax121/APatch

## `su` invocation vs persistent privileged service

### One-shot shell

A simple architecture can invoke `su` and execute commands.

Advantages:
- conceptually small
- useful for rare administrative actions

Risks:
- shell quoting and arbitrary filenames
- command injection
- parsing human-oriented output
- uncertain cancellation/process trees
- startup overhead per operation
- streaming and random access are awkward

**RECOMMENDATION:** Do not make command-string construction the primary file I/O mechanism.

### Persistent root shell

A retained privileged shell can amortize startup cost and support command sequences, but creates additional lifecycle concerns:

- root authorization can be revoked
- shell can die
- stdout/stderr framing is required
- concurrent commands need serialization/protocol framing
- process death must not leave operations in ambiguous state
- mount namespace may differ from the app process

**RECOMMENDATION:** If a shell is used, treat it as an unreliable transport with explicit request framing, separate stdout/stderr, timeouts, cancellation and restart logic.

### Privileged service / remote filesystem API

**FACT:** `libsu` provides higher-level root shell APIs, root-service IPC and an NIO-oriented remote filesystem module. Its maintainers' modern direction includes root service/NIO APIs rather than relying solely on older shell-file wrappers.

References:
- https://github.com/topjohnwu/libsu
- https://github.com/topjohnwu/libsu/blob/master/CHANGELOG.md
- https://github.com/topjohnwu/libsu/tree/master/nio

**RECOMMENDATION:** `libsu` is the leading candidate to prototype because it is purpose-built for Android root and can reduce direct shell-string exposure. Final selection remains unfrozen.

## libsu status

**FACT:** The project is actively maintained; current repository documentation/releases must be rechecked immediately before dependency freeze. Research observed the 6.x line and support for shell, service and NIO modules.

**LICENSE:** Verify exact module license/notice at dependency-freeze time from the repository. Do not copy a version from FLACtify or unrelated projects.

Reference:
- https://github.com/topjohnwu/libsu

## Arbitrary filenames and shell injection

A file manager must support names containing:

- whitespace
- quotes
- apostrophes
- `$`, backticks and shell metacharacters
- newlines
- leading hyphens
- Unicode and combining characters

**SECURITY RECOMMENDATION:** Never concatenate an arbitrary pathname into a shell command. Prefer a structured file API/root service. Where a shell command is genuinely unavoidable, use a rigorously defined argument transport/escaping layer, terminate options with `--` where supported, avoid parsing display-oriented command output, and maintain adversarial filename tests.

This research deliberately does not publish a generic “escape arbitrary shell input” utility because the correct encoding depends on the exact command/protocol chosen later.

## stdout and stderr

**RECOMMENDATION:** Preserve stdout and stderr separately. Treat exit code, transport failure and timeout as separate states. Binary file content must never share a textual control channel that is parsed by delimiters vulnerable to filename/output collisions.

## Timeout and cancellation

Root operations can block on:

- filesystem I/O
- FUSE/storage layers
- dead mounts
- child commands
- authorization UI

**RECOMMENDATION:** Each privileged request needs a cancellation path and bounded timeout policy appropriate to its operation. A timeout must not be reported as “operation did not happen”; the operation may have partially completed. Operation Manager therefore needs recovery/checkpoint semantics.

## Root denial/revocation

Expected states should include:

- root manager absent
- `su` binary unavailable
- user denied authorization
- authorization pending/timeout
- authorization previously granted then revoked
- privileged service crashed
- action denied by SELinux or mount policy despite root

**RECOMMENDATION:** All must degrade to standard-provider behavior without corrupting app state.

## SELinux

**FACT:** Android uses SELinux mandatory access control in addition to Unix UID/mode checks. Root UID alone does not mean every SELinux action is allowed in every environment/context.

References:
- https://source.android.com/docs/security/features/selinux
- https://source.android.com/docs/security/features/selinux/concepts

**RECOMMENDATION:** Treat SELinux denials as a distinct privileged-operation failure category when diagnostics are available. Do not attempt to disable SELinux as a product feature.

## Mount namespaces

**FACT/INFERENCE:** Modern root solutions can alter mount-namespace behavior. A path visible in the normal app process may resolve differently in a privileged shell/service, and systemless overlays may affect the mounted view.

**RECOMMENDATION:** Do not cache assumptions that normal-process and root-process mount views are identical. For root sources, enumeration and file operations should occur consistently in the privileged context that owns the root provider.

Reference:
- https://github.com/topjohnwu/libsu/blob/master/core/src/main/java/com/topjohnwu/superuser/Shell.java

## Read-only/verified filesystems

**FACT:** Some Android partitions may be mounted read-only or protected by verified boot/dynamic partition mechanisms. Root authorization does not create a universal writable system partition.

References:
- https://source.android.com/docs/security/features/verifiedboot
- https://source.android.com/docs/core/ota/dynamic_partitions

**RECOMMENDATION:** Report actual mount/write capability; never promise that root makes `/system`, `/vendor`, etc. writable.

## chmod/chown

**RECOMMENDATION:** Expose chmod/chown only when the root/direct provider confirms capability and the target type supports the operation. Preserve numeric uid/gid/mode metadata where available; do not map cloud ACLs or SMB ACLs into fake POSIX mode values.

## Symlinks

Root browsing should distinguish:

- symlink object
- symlink target text
- resolved target metadata when explicitly requested
- broken symlink

**SECURITY RECOMMENDATION:** Recursive delete/copy must have explicit link-follow policy. Default destructive traversal should not cross a symlink boundary unexpectedly.

## Root file streaming

**RECOMMENDATION:** Large root file reads/writes must stream using bounded buffers or a remote-file/service API. Never marshal an entire large file through a single in-memory byte array or textual base64 channel.

Possible future mechanisms to prototype:

1. libsu NIO/root-service-backed channels;
2. pipe/file-descriptor bridging where lifecycle is safe;
3. bounded binary stream over a privileged service.

## Root file descriptors

**UNRESOLVED:** Whether a privileged process can safely hand a useful file descriptor to the ordinary app process depends on Android Binder/SELinux/access semantics and how the descriptor was opened. Possession of an already-open FD can differ from pathname permission checks, but this must not be assumed architecturally without a dedicated prototype.

**PROTOTYPE REQUIRED:** Test on representative Magisk, KernelSU and APatch devices only after root support phase begins. No destructive root experiment belongs in this research phase.

## Root operation auditability

**RECOMMENDATION:** Privileged destructive operations should be clearly marked in UI and internally tagged as root-elevated. Logs must avoid sensitive full paths by default, but diagnostic state should distinguish normal-provider failure from root-provider failure.

Potential high-risk actions (system paths, chmod/chown, delete/move) should require stronger UI confirmation than ordinary shared-storage actions, consistent with `UI_DESIGN_V1.md`.

## Security checklist for root layer

- No raw filename interpolation into shell strings.
- No hidden automatic elevation.
- User explicitly enables root and grants authorization.
- Keep non-root provider operational at all times.
- Separate privileged process/service lifecycle from UI lifecycle.
- Bound command/stream memory.
- Separate stdout/stderr/control/binary channels.
- Cancellation and timeout are partial-operation states, not automatic rollback.
- Never disable SELinux as a workaround.
- Recheck target before destructive action; avoid TOCTOU where possible.
- Explicit symlink policy.
- Validate mount identity/read-only state before mutation.
- Do not persist root credentials/tokens in plaintext.

## Compatibility matrix to validate later

| Environment | Detect | List/read | Stream large file | chmod/chown | symlink | protected paths | mount namespace |
|---|---|---|---|---|---|---|---|
| No root | required | baseline provider only | baseline | no arbitrary protected | baseline only | denied as designed | normal |
| Magisk | prototype | prototype | prototype | prototype | prototype | prototype | prototype |
| KernelSU | prototype | prototype | prototype | prototype | prototype | profile-dependent | prototype |
| APatch | prototype | prototype | prototype | prototype | prototype | prototype | prototype |

## Recommendation summary

**Strong:** root is an additive provider, never baseline authority.  
**Strong:** prefer structured root-service/NIO/file APIs over shell command strings; prototype libsu first.  
**Strong:** stream large files; do not buffer entire content.  
**Plausible:** persistent root service for filesystem-heavy workflows.  
**Postpone:** exact root library/version, FD bridge design, module boundaries.  
**Prototype required:** Magisk/KernelSU/APatch behavior, SELinux/mount namespaces, descriptor passing and large-file streaming.
