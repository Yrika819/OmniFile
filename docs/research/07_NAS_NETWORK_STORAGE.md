# NAS and Network Storage Research

Status: COMPLETE — protocol/library research only; no network implementation  
Last verified: 2026-09-17

## Classification legend

**FACT** documented behavior; **INFERENCE** derived consequence; **RECOMMENDATION** proposed direction; **UNRESOLVED** requires controlled prototype evidence.

## Executive finding

SMB2/3 should be the primary NAS protocol, with SFTP and WebDAV as separate provider families. They must not be flattened into “network paths”: authentication, seeking, locking, rename/copy behavior, reconnect semantics, metadata and security guarantees differ materially.

## SMB2 / SMB3

### Candidate: SMBJ

**FACT:** SMBJ is a pure-Java SMB2/SMB3 client implementation. Maven Central currently exposes `com.hierynomus:smbj:0.15.0` under Apache-2.0. Its configuration supports signing and SMB3-era security features; dependencies currently include Bouncy Castle and related runtime libraries.

Sources:
- https://github.com/hierynomus/smbj
- https://central.sonatype.com/artifact/com.hierynomus/smbj/0.15.0
- https://github.com/hierynomus/smbj/blob/master/src/main/java/com/hierynomus/smbj/SmbConfig.java

**Strengths:**
- SMB-focused Java API, avoiding JNI.
- SMB2/3 protocol coverage.
- file handles and offset-oriented I/O suitable for random access.
- signing/encryption configuration exposed.

**Risks:**
- exact dialect/server interoperability must be tested with Windows, Samba and common NAS appliances;
- transitive crypto dependency versions require routine vulnerability review;
- Android-specific network suspension/process death behavior is not solved by the library itself.

**RECOMMENDATION:** SMBJ is the preferred SMB prototype candidate, not yet a frozen dependency.

### Alternative: jcifs-ng

**FACT:** `jcifs-ng` is an LGPL-2.1 licensed cleaned-up jCIFS successor. Its documentation advertises SMB2 and some SMB3 support; current repository material still presents 2.1.9 as the stable line.

Source:
- https://github.com/AgNO3/jcifs-ng

**Assessment:** Viable fallback/interoperability comparison candidate, but its copyleft license and less complete SMB3 positioning make it a less attractive first choice than SMBJ for this product unless compatibility testing demonstrates a clear advantage.

## SMB authentication and transport security

Required cases to evaluate:
- username/password against local NAS/Windows accounts;
- domain/workgroup forms where needed;
- guest only if explicitly allowed by server/user;
- modern NTLM/Kerberos capability as supported by selected library/environment;
- SMB signing;
- SMB3 encryption where server supports it.

**RECOMMENDATION:** Do not silently downgrade security to make a connection succeed. Surface negotiated dialect/signing/encryption information in diagnostics, while keeping passwords out of logs.

## SMB locking and server-side operations

SMB is filesystem-like and may expose:
- open/share modes;
- byte-range or file locking;
- rename/move within the server namespace;
- server-side copy capabilities depending on server/library support.

**UNRESOLVED:** Exact SMBJ exposure of server-side copy/offload, durable handles and reconnect behavior needs API-level prototype testing. The provider should not advertise those capabilities until verified.

## SFTP

### Candidate: SSHJ

**FACT:** SSHJ 0.40.0 was released 2026-06-29. It is Apache-2.0 and supports SSH/SFTP. Project documentation warns that versions through 0.37.0 were affected by the Terrapin attack (CVE-2023-48795) and recommends 0.38.0+.

Sources:
- https://github.com/hierynomus/sshj
- https://github.com/hierynomus/sshj/releases
- https://central.sonatype.com/artifact/com.hierynomus/sshj/0.40.0

**RECOMMENDATION:** SSHJ is the preferred lightweight SFTP prototype candidate, with strict host-key verification enabled from day one.

### Alternative: Apache MINA SSHD

**FACT:** Apache MINA SSHD 2.19.0 was released in July 2026; it is a mature Apache project with client/server SSH functionality.

Sources:
- https://mina.apache.org/sshd-project/downloads.html
- https://release-catalog.apache.org/mina-sshd/index.html

**Assessment:** Strong maintained alternative, likely heavier than needed for a file-manager SFTP client. Compare Android footprint, algorithm support and SFTP behavior before selecting.

## SFTP host-key security

**SECURITY REQUIREMENT:** Password/key authentication is not sufficient if the server host key is not authenticated.

Recommended model:
- first connection shows fingerprint and requires explicit user trust unless a trusted known-hosts source exists;
- persist trusted host key/fingerprint, not “accept all” policy;
- changed host key produces a blocking warning, not automatic replacement;
- support modern algorithms based on selected SSH library;
- never disable verification for convenience.

## SFTP random access and rename

SFTP supports offset-based reads/writes at protocol level in common implementations. POSIX rename semantics can depend on server/version/extensions such as OpenSSH extensions.

**RECOMMENDATION:** advertise random-read if the chosen library/server path proves it; advertise atomic rename only where the server extension/behavior is positively identified. Do not treat ordinary SFTP rename as universally POSIX-atomic.

## WebDAV

### Candidate: dav4jvm

**FACT:** `bitfireAT/dav4jvm` is a maintained Kotlin/Java WebDAV library under MPL-2.0. Release 4.1.0 was published 2026-09-01. It is used in the DAVx ecosystem and has active 2026 maintenance.

Sources:
- https://github.com/bitfireAT/dav4jvm
- https://github.com/bitfireAT/dav4jvm/releases

**RECOMMENDATION:** Preferred WebDAV protocol-layer prototype candidate, subject to checking how well its current HTTP stack and Android integration fit large file transfer/range streaming.

### Older alternative: sardine-android

Source:
- https://github.com/thegrizzlylabs/sardine-android

**Assessment:** Useful comparison but appears less actively evolving than dav4jvm. Do not select an older DAV wrapper simply because examples exist.

## WebDAV semantics

WebDAV may expose:
- PROPFIND listing/metadata;
- GET/PUT;
- DELETE;
- MKCOL;
- MOVE/COPY;
- HTTP Range for partial reads when the server/resource supports it;
- ETag/Last-Modified for conflict detection where correctly implemented.

**INFERENCE:** WebDAV interoperability is server-dependent. Capability probes and defensive handling of redirects/status codes are essential.

Protocol references:
- WebDAV RFC 4918: https://www.rfc-editor.org/rfc/rfc4918
- HTTP Range RFC 9110: https://www.rfc-editor.org/rfc/rfc9110

## Connection pooling

**RECOMMENDATION:** Reuse bounded connections/sessions rather than reconnect per file operation, but isolate provider sessions from UI lifecycle. Pooling rules differ:
- SMB: connection/session/tree/share hierarchy;
- SFTP: SSH connection plus SFTP subsystem/channels;
- WebDAV: HTTP connection pooling.

Idle timeout and reconnect must not cause the durable Operation Manager state to disappear.

## Timeout policy

Separate timeouts for:
- DNS/discovery;
- TCP connect;
- authentication/handshake;
- metadata request;
- idle read/write;
- whole operation (usually **not** one short fixed timeout for huge files).

**RECOMMENDATION:** A 100 GB transfer needs activity/idle timeouts and cancellation, not a simplistic total timeout.

## Reconnect/retry

Safe retry depends on operation idempotency:

- listing/read at stable offset: generally retryable with source-version check;
- upload chunks: retry only according to protocol/session semantics;
- create/rename/delete: may have happened before disconnect, so re-query state before repeating;
- move: verify both source and destination.

Use exponential backoff with bounded retry policy, but never hide authentication, certificate/host-key or user-cancellation errors as transient retries.

## Resume and partial transfer

Download/upload provider should expose:
- stable source identity/version;
- total size when known;
- partial local/remote destination identity;
- verified offset/chunk state;
- ability to reopen at offset;
- finalization step.

**RECOMMENDATION:** `.partial`-style names are one possible UI/storage detail, but Operation Manager should use provider-native temporary identity where available and persist checkpoints independently.

## Network disconnect and Wi-Fi transition

Expected failures:
- Wi-Fi toggled off;
- AP roaming or IP change;
- device switches Wi-Fi <-> cellular;
- NAS sleeps/reboots;
- app process is killed;
- DHCP/DNS changes.

**RECOMMENDATION:** Treat network identity/session as ephemeral. Re-resolve/reconnect and validate source version before continuing from persisted offset.

## Discovery: mDNS / NSD

**FACT:** Android exposes Network Service Discovery through `NsdManager` for DNS-SD/mDNS-style discovery.

Source:
- https://developer.android.com/reference/android/net/nsd/NsdManager

**FACT:** Android is tightening local-network permissions. Android 16 introduces local-network protection changes/opt-in behavior, and Android 17 targets broader enforcement through local-network permission controls.

Sources:
- https://developer.android.com/privacy-and-security/local-network-permission
- https://developer.android.com/about/versions/16/behavior-changes-16

**RECOMMENDATION:** Discovery is optional convenience. Manual hostname/IP must always work. Architect now so local-network permission denial disables discovery/connections cleanly rather than breaking unrelated storage.

## Credentials storage

Never store NAS/SFTP/WebDAV passwords in plaintext preferences, logs, URIs or operation records.

**RECOMMENDATION:** Store secret material through Android Keystore-backed encrypted credential storage or an equivalent reviewed solution; persist only credential references in provider configuration. For SFTP, private keys need equivalent protection and host keys are trust metadata rather than secrets.

References:
- https://developer.android.com/privacy-and-security/keystore
- https://developer.android.com/privacy-and-security/security-config

## Direct FLACtify-style streaming from NAS

### Requirement

For a large FLAC on NAS, playback should begin without full download and seeking should not restart the transfer from byte zero.

### SMB

SMB file handles/offset reads make a custom Media3 `DataSource` or provider-backed random-read adapter plausible.

### SFTP

Offset reads are plausible, but latency/round-trip cost and channel behavior require measurement.

### WebDAV

Efficient seek depends on HTTP Range support and stable resource versions/ETags. If Range is absent, remote random seek may require local caching or be reported as limited.

**RECOMMENDATION:** Implement no product promise until a Media3 custom-source prototype demonstrates smooth start/seek/reconnect for FLAC over realistic Wi-Fi latency.

## Capability comparison

| Capability | SMB2/3 | SFTP | WebDAV |
|---|---|---|---|
| encrypted transport | SMB3 encryption; signing separate | SSH | HTTPS |
| random read | strong candidate | strong candidate | HTTP Range dependent |
| partial write/resume | protocol/library dependent | offset writes practical | server/method dependent |
| rename | yes | yes | MOVE |
| atomic rename | server/filesystem dependent | extension/server dependent | not POSIX guarantee |
| server-side copy | SMB capability/server dependent | not a universal SFTP primitive | COPY |
| locking | SMB-native | limited/different semantics | WebDAV LOCK optional/interoperability varies |
| Unix permissions | not native POSIX abstraction | commonly available | generally not |
| discovery | SMB/mDNS/NetBIOS ecosystem varies | mDNS optional | mDNS/manual URL |

## Security checklist

- SMB1 should not be required as baseline; focus SMB2/3.
- Prefer modern signing/encryption; never silently weaken to succeed.
- Strict SFTP host-key validation.
- Strict TLS validation for WebDAV; no trust-all certificate mode.
- Validate redirects and prevent credential leakage across unexpected origins.
- Bound directory/list response sizes and metadata strings.
- Protect credentials with Keystore-backed storage.
- Sanitize logs.
- Treat malicious NAS/WebDAV servers as hostile input sources.

## Prototype-required matrix

- Windows SMB 3.x, Samba, at least one consumer NAS.
- signing required / encryption required / auth failure cases.
- 10k- and 100k-entry network directories.
- 100 GB interrupted/resumed file transfer.
- SMB/SFTP/WebDAV FLAC seek while network latency is injected.
- Wi-Fi off/on and network change during transfer/playback.
- mDNS discovery permission denied/granted.
- SFTP host-key change detection.
- WebDAV Range absent/present and broken ETag servers.

## Recommendation summary

**Strong:** SMBJ first for SMB; SSHJ first for SFTP; dav4jvm first for WebDAV — all remain prototype candidates.  
**Strong:** credentials, TLS and SSH host-key trust are architecture requirements.  
**Strong:** provider capabilities and durable transfer checkpoints must survive session/network loss.  
**Postpone:** exact versions, auth feature breadth, discovery UX and server-side copy guarantees until interoperability testing.
