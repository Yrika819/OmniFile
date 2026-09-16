# Security Threat Model and Review Checklist

Status: COMPLETE — threat model only; no security-sensitive implementation  
Last verified: 2026-09-17

## Classification legend

**FACT** documented platform behavior; **INFERENCE** derived threat; **RECOMMENDATION** proposed control; **UNRESOLVED** requires implementation review/fuzzing/device validation.

## Security posture

A full file manager is a high-trust application. It may eventually hold all-files access, optional root, NAS credentials, cloud OAuth tokens and the ability to parse arbitrary untrusted files. Security boundaries must therefore be architectural rather than added after features are complete.

Primary assets:
- user files and metadata;
- credentials/tokens/SSH keys;
- provider grants;
- root authority;
- operation queue and partial outputs;
- app-private caches/database;
- user trust in destructive operations.

Primary attacker/input classes:
- malicious file/archive/media/document;
- malicious filename/tree layout;
- malicious local app sending intents;
- malicious or compromised NAS/WebDAV server;
- hostile network;
- compromised cloud account/content;
- malicious symlink/race on writable filesystem;
- accidental user action plus unexpected provider semantics.

## Arbitrary filenames

Names can legally contain spaces, quotes, newlines, shell metacharacters, Unicode, combining characters and misleading bidirectional/control characters depending on backend.

**RECOMMENDATION:**
- treat names as opaque data, never executable text;
- never build root shell commands through raw interpolation;
- do not normalize Unicode for identity unless backend specifies it;
- visually disambiguate dangerous/truncated names where practical;
- test leading dots/hyphens, RTL/bidi, newline and combining sequences.

## Path traversal

**FACT:** Android documents path traversal as a vulnerability when attacker-controlled path elements can escape the intended directory.

Source:
- https://developer.android.com/privacy-and-security/risks/path-traversal

**RECOMMENDATION:** Normalize/canonicalize only where real path semantics apply and enforce destination containment. For provider IDs/URIs, never construct a filesystem path from display names.

## Archive traversal / Zip Slip

**FACT:** Android explicitly documents archive path traversal and notes the problem extends beyond ZIP to other archive formats.

Source:
- https://developer.android.com/privacy-and-security/risks/zip-path-traversal

Controls:
- reject absolute/escaping entry paths;
- validate final destination under the selected root;
- do not trust declared entry names;
- use safe extraction primitive when library supplies one, but still test it;
- combine with symlink defenses.

## Symlink attacks and TOCTOU

Threats:
- archive creates a symlink then later writes through it;
- target changes between validation and destructive action;
- recursive delete follows a symlink outside selected tree;
- root greatly amplifies impact.

**RECOMMENDATION:**
- default recursive destructive traversal must not follow symlinks;
- distinguish lstat-like entry from resolved target;
- use no-follow/descriptor-relative operations where practical on direct filesystem;
- revalidate identity/version before irreversible steps;
- reject archive symlinks where destination provider cannot safely create them.

## Root shell injection

Covered deeply in `03_ROOT_ACCESS.md`.

Controls:
- structured root service/NIO API preferred;
- no filename interpolation into shell commands;
- explicit privilege boundary;
- root-denied state remains normal behavior;
- elevated delete/chmod/chown require clear UX confirmation.

## Malformed media and archives

Broad parsers are exposed to attacker-controlled lengths, offsets, compression streams and metadata.

Controls:
- current maintained libraries;
- OS/Media3 codecs where appropriate;
- byte/count/dimension/depth limits;
- fuzz/malformed corpus;
- process/native crash monitoring;
- never trust extension or MIME alone;
- native parser dependencies receive urgent security update path.

libarchive's 2026 security/bugfix releases are a concrete reminder that archive parsers require ongoing patching:
- https://github.com/libarchive/libarchive/releases

## Decompression bombs

Use multiple independent limits:
- entry count;
- total expanded bytes;
- single entry size;
- nesting depth;
- actual bytes written;
- available-space reserve;
- optional expansion ratio/time/CPU budget.

Never rely solely on archive header sizes.

## Malicious NAS / WebDAV server

Threats:
- oversized or malformed directory responses;
- unexpected encoding/names;
- redirect credential leakage;
- inconsistent ETags/lengths;
- protocol downgrade;
- connection stalls;
- malicious SMB metadata;
- TLS certificate errors hidden as “connection failed.”

Controls:
- bounded parsers/response sizes;
- explicit redirect-origin/auth policy;
- modern SMB signing/encryption policy;
- strict TLS validation;
- structured timeouts;
- version/length revalidation before resume/finalization.

## TLS certificate validation

**FACT:** Android's network-security guidance requires real certificate validation and discourages insecure custom trust managers. Cleartext is unsafe. Network Security Config can control trust anchors and cleartext policy; Android 16 introduces optional Certificate Transparency configuration.

Sources:
- https://developer.android.com/privacy-and-security/security-ssl
- https://developer.android.com/privacy-and-security/security-config
- https://developer.android.com/privacy-and-security/risks/cleartext-communications

**RECOMMENDATION:** No “trust all certificates” option. If self-hosted WebDAV requires a private CA, support an explicit scoped trust setup rather than disabling validation globally.

## SFTP host keys

TLS rules do not cover SSH. Host-key verification is mandatory:
- trusted fingerprint/known-hosts persistence;
- first-use trust UX;
- block changed host key until user reviews;
- never auto-accept a changed key.

## OAuth tokens / cloud credentials

Threats:
- token leakage in logs/URLs/backups;
- excessive scopes;
- insecure redirect handling;
- stale accounts/tokens remaining after disconnect.

Controls:
- least-privilege scopes;
- standard provider/native public-client auth flows;
- protected token storage;
- redact headers, query strings and signed URLs from logs;
- revoke/delete local credentials when disconnecting where provider API allows;
- no production secrets committed to Git.

## SMB / WebDAV credentials and SSH private keys

**FACT:** Android Keystore protects cryptographic key material and can keep keys non-exportable/hardware-backed depending on device/capability.

Source:
- https://developer.android.com/privacy-and-security/keystore

**RECOMMENDATION:** Use Keystore-backed encryption/wrapping for locally persisted provider secrets. Do not place credentials in plain SharedPreferences, provider URLs or database diagnostic fields.

## Temporary files

Threats:
- plaintext conversion/extraction/tag-edit staging;
- crash leaves sensitive temporary data;
- wrong directory exposed through another app/provider;
- stale partial interpreted as complete.

Controls:
- app-private storage by default;
- unpredictable internal identity;
- track temp files in Operation Manager;
- distinguish partial/final state;
- startup cleanup/reconciliation;
- never share broad temp directories through FileProvider.

## Cache privacy

Caches can contain:
- filenames and folder structures;
- artwork/thumbnails;
- portions or complete remote media;
- cloud metadata.

**RECOMMENDATION:** Keep private by default and exclude sensitive credential/cache material from backup as appropriate. Clear-cache UX should distinguish metadata/media/temp/offline content.

Backup guidance:
- https://developer.android.com/privacy-and-security/risks/backup-best-practices

## Clipboard leakage

Android 13+ shows clipboard UI. Sensitive clipboard content can be flagged to suppress preview.

Sources:
- https://developer.android.com/about/versions/13/behavior-changes-all
- https://developer.android.com/develop/ui/compose/touch-input/copy-and-paste

**RECOMMENDATION:** Avoid copying credentials/tokens. If later copying sensitive provider data is ever intentional, mark it sensitive. Ordinary file paths/names may still be private and should not be copied implicitly.

## Logs

**FACT:** Android warns against logging sensitive information to logcat.

Source:
- https://developer.android.com/privacy-and-security/risks/log-info-disclosure

Log policy:
- no passwords/tokens/auth headers;
- redact signed cloud URLs;
- avoid complete sensitive local paths in release logs;
- no media metadata if unnecessary;
- structured error codes first, optional user-approved diagnostics later.

## Exported Android components

**FACT:** Android recommends explicitly setting component exported state; exported components can proxy app permissions if insufficiently protected.

Sources:
- https://developer.android.com/privacy-and-security/risks/android-exported
- https://developer.android.com/privacy-and-security/risks/access-control-to-exported-components
- https://developer.android.com/privacy-and-security/security-tips

**RECOMMENDATION:** Default internal activities/services/receivers/providers to non-exported. Any necessary external OPEN/VIEW/share surface validates caller-independent untrusted inputs and grants only minimal temporary URI permission.

## Intent handling / intent redirection

**FACT:** Android documents nested intent redirection as a security risk; Android 16 adds default launch hardening, but apps should still sanitize/validate intents rather than opting out casually.

Source:
- https://developer.android.com/privacy-and-security/risks/intent-redirection

Controls:
- validate MIME, URI scheme, flags, extras and action;
- never trust display filename to infer safe file type;
- avoid forwarding untrusted nested intents;
- grant URI permissions minimally;
- handle malformed/missing extras without crashes.

## Opening untrusted APKs/files

A file manager may let the user open files in another app.

**RECOMMENDATION:** Opening is a user-directed external intent, not execution inside File Manager. APK handling should not attempt silent installation, bypass Android package-installer consent or execute arbitrary files. Share with narrow `content://` grants and explicit user action.

Android sharing/FileProvider guidance:
- https://developer.android.com/privacy-and-security/security-best-practices
- https://developer.android.com/privacy-and-security/risks/file-providers

## FileProvider

Never expose root/broad internal directories. Grant only a chosen file and minimum read/write mode for limited lifetime.

Source:
- https://developer.android.com/privacy-and-security/risks/file-providers

## Secure deletion limitations

**FACT:** Android devices use file-based encryption and modern flash storage stacks. Physical media behavior, wear leveling, filesystem copy-on-write/snapshots and controller remapping mean an app-level “overwrite N times then delete” cannot honestly guarantee physical erasure of all prior flash cells.

FBE reference:
- https://source.android.com/docs/security/features/encryption/file-based

**RECOMMENDATION:** Call ordinary delete **delete**, not “secure erase.” Do not promise forensic secure deletion on flash/removable/network/cloud storage. Provider-native secure erase, crypto-erase or cloud retention policies are separate features and would need backend-specific evidence.

## Search/index privacy

A local index can reveal file names/paths even if content remains protected.

Controls:
- private app database;
- credential-encrypted storage where appropriate;
- clear provider index when account/source removed;
- do not index protected/root locations without explicit opt-in;
- avoid storing unnecessary content snippets.

## Operation integrity

For copy/move/download/extract/convert:
- source version snapshot;
- partial destination state;
- validate final length/hash where practical;
- finalize before destructive source deletion;
- storage-full and cancellation leave source intact;
- post-crash reconciliation before retry.

## Security review checklist

### Storage/provider
- [ ] no raw-path assumption for `content://`
- [ ] capabilities checked before mutation
- [ ] symlink policy explicit
- [ ] path containment tested
- [ ] revoked permission/source disappearance handled

### Root
- [ ] root optional
- [ ] no untrusted shell interpolation
- [ ] SELinux/mount failure handled
- [ ] elevated destructive UX explicit

### Archives/conversion/media
- [ ] traversal/symlink defense
- [ ] expansion/entry/dimension limits
- [ ] malformed corpus/fuzzing
- [ ] current native parser versions
- [ ] partial output not marked complete

### Network/cloud
- [ ] TLS validation never disabled
- [ ] SFTP host keys verified
- [ ] SMB signing/encryption policy reviewed
- [ ] OAuth least privilege
- [ ] tokens/credentials protected
- [ ] redirect/auth leakage tested
- [ ] retry only after idempotency/reconciliation

### Android IPC
- [ ] exported state explicit
- [ ] incoming intents sanitized
- [ ] FileProvider narrow
- [ ] URI grants minimal/temporary
- [ ] no privileged exported proxy

### Privacy
- [ ] release logs redacted
- [ ] cache/temp private
- [ ] backup rules reviewed
- [ ] EXIF/GPS policy explicit
- [ ] clipboard sensitive flag where applicable
- [ ] delete wording does not promise secure erase

## Future security validation

- static analysis/lint and dependency vulnerability audit;
- archive/media malformed corpus;
- adversarial filenames;
- symlink races on local/root-capable test trees;
- malicious fake WebDAV/cloud provider;
- TLS bad cert/hostname/redirect tests;
- SSH host-key change tests;
- exported-intent fuzzing;
- process death at every operation commit boundary;
- root/non-root privilege-boundary review.

## Recommendation summary

**Strong:** provider, root and parser boundaries are security boundaries.  
**Strong:** credentials and token protection, TLS/SSH verification, safe archive extraction and intent validation are launch requirements.  
**Strong:** never market ordinary flash deletion as secure erasure.  
**Strong:** build adversarial fixtures/tests alongside each feature rather than scheduling security only at the end.
