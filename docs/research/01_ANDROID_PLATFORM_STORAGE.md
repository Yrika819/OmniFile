# Android Platform Storage Research

Status: COMPLETE — research recommendation only; no implementation authority  
Last verified: 2026-09-17  
Primary environment: Android 16 / API 36, compatibility floor under study: Android 12 / API 31

## Classification legend

- **FACT** — directly supported by cited platform documentation.
- **INFERENCE** — conclusion derived from documented behavior, but not itself an API guarantee.
- **RECOMMENDATION** — proposed direction for later user review.
- **UNRESOLVED** — documentation is insufficient; prototype/device validation is required.

## Executive finding

**FACT:** Android storage is not one uniform filesystem API. A file manager targeting Android 12–16 must coexist with app sandboxing, scoped storage, MediaStore, the Storage Access Framework (SAF), special `MANAGE_EXTERNAL_STORAGE` access, app-private directories, document providers, and provider-dependent `content://` semantics.

**RECOMMENDATION:** Treat direct filesystem paths, SAF document URIs, root paths, remote objects, and cloud objects as different storage capability domains. Do not normalize them prematurely into `java.io.File` semantics.

## Android 12–16 baseline

### Scoped storage

**FACT:** Scoped storage is the normal external-storage model for modern Android. Apps retain unrestricted access to their own app-specific storage, but general shared-storage access is mediated through MediaStore, SAF, or the special all-files access mechanism where policy and use case permit it.

**FACT:** Since Android 11, apps cannot use SAF's `ACTION_OPEN_DOCUMENT_TREE` to grant arbitrary tree access to the internal storage root, reliable-SD-card root, `Download`, `Android/data`, or `Android/obb`. Apps also cannot access another app's app-specific external directory merely through ordinary storage permissions.

**Android 12 implication:** API 31 inherits these Android 11 scoped-storage restrictions. A design that works only because an older device/provider exposes raw paths is invalid as a compatibility baseline.

Sources:
- https://developer.android.com/about/versions/11/privacy/storage
- https://developer.android.com/training/data-storage
- https://developer.android.com/training/data-storage/shared

### `MANAGE_EXTERNAL_STORAGE` / All files access

**FACT:** `MANAGE_EXTERNAL_STORAGE` allows an eligible app broad read/write access to shared storage, access to `MediaStore.Files`, and direct-path access to most shared-storage content. The special access can also cover roots of removable volumes such as SD/USB storage that are presented as shared storage.

**FACT:** It does **not** provide unrestricted access to other apps' app-specific directories under `Android/data` and comparable protected areas. Root privilege is a separate capability.

**FACT:** Google Play treats `MANAGE_EXTERNAL_STORAGE` as a high-risk permission. File managers are an explicitly recognized core-use category, but distribution through Google Play requires the permission to be essential to the app's core function and requires policy declaration/review.

**RECOMMENDATION:** For a true general-purpose file manager, retain `MANAGE_EXTERNAL_STORAGE` as a plausible later option, but keep the architecture capable of SAF-only operation. Do not freeze the permission decision until distribution strategy and exact feature scope are reviewed.

Sources:
- https://developer.android.com/training/data-storage/manage-all-files
- https://support.google.com/googleplay/android-developer/answer/10467955

## Storage Access Framework (SAF)

### SAF model

**FACT:** SAF exposes documents through `DocumentsProvider` and `DocumentsContract`. The stable identity is an opaque document ID/URI, not a filesystem pathname. Providers may represent local storage, USB devices, or cloud-backed content.

**FACT:** A provider advertises operations through document flags such as write, delete, rename, copy, move, metadata, and thumbnail support. Therefore, supported operations are capability-driven and provider-dependent.

**FACT:** Persistable URI grants can survive process death and reboot when the provider/grant supports the corresponding persistable permission and the app explicitly takes it. A persisted grant is still subject to the provider/document continuing to exist and provider policy.

**RECOMMENDATION:** Persist the URI plus granted mode flags and revalidate it on use. Never infer a stable raw path from a `content://` URI.

Sources:
- https://developer.android.com/guide/topics/providers/document-provider
- https://developer.android.com/reference/android/provider/DocumentsContract
- https://developer.android.com/reference/android/provider/DocumentsContract.Document
- https://developer.android.com/reference/android/content/ContentResolver

### `DocumentFile`

**FACT:** AndroidX `DocumentFile` is a convenience wrapper over file/document APIs. Its API surface does not elevate provider capability. For example, rename may change the document URI and invalidates assumptions that descendants keep the same reference identity.

**INFERENCE:** `DocumentFile` is useful at UI/integration edges but is too weak to be the semantic foundation of a high-capability file manager because it hides useful provider flags and cannot express all operation guarantees.

**RECOMMENDATION:** Use lower-level `DocumentsContract`/`ContentResolver` capabilities where precise behavior matters. `DocumentFile` may remain a convenience adapter, not the architecture's universal storage model.

Source:
- https://developer.android.com/reference/kotlin/androidx/documentfile/provider/DocumentFile

## `content://`, file descriptors, seeking and random access

**FACT:** `ContentResolver.openFileDescriptor()` can expose a `ParcelFileDescriptor` when a provider supports it. However, a provider may serve data through a pipe, transform data, or otherwise provide something that is not a regular seekable local file.

**FACT:** `ParcelFileDescriptor` can expose an OS file descriptor and stat size when meaningful, but file-descriptor existence alone does not guarantee regular-file semantics or random seekability.

**RECOMMENDATION:** Model `open sequential read`, `open seekable read`, `known length`, and `native/OS descriptor available` as distinct capabilities.

**UNRESOLVED / PROTOTYPE REQUIRED:** Test representative SAF providers (AOSP DocumentsUI/local provider, Google Drive if available through a document provider, removable storage, vendor providers) for seek, random read, append modes, rename URI changes, cancellation and descriptor lifetime.

Sources:
- https://developer.android.com/reference/android/content/ContentResolver
- https://developer.android.com/reference/android/os/ParcelFileDescriptor

## MediaStore

**FACT:** MediaStore is optimized for indexed shared media collections and exposes media metadata through a content-provider API. It is not a universal directory-tree abstraction and should not be treated as a replacement for a file provider when the user expects arbitrary filesystem hierarchy, archive files, source trees, ROM images, etc.

**RECOMMENDATION:** Use MediaStore primarily for category/media discovery where it improves speed or metadata coverage; preserve provider/path-based browsing separately.

Sources:
- https://developer.android.com/training/data-storage/shared/media
- https://developer.android.com/reference/android/provider/MediaStore

## FileProvider

**FACT:** `FileProvider` safely exposes app-selected files to other apps as `content://` URIs with temporary URI permissions. It is a sharing boundary, not an all-files-access mechanism.

**SECURITY:** Android explicitly warns against overbroad `FileProvider` mappings such as exposing filesystem root-like directories. Share only narrow intended locations and grants.

Sources:
- https://developer.android.com/reference/androidx/core/content/FileProvider
- https://developer.android.com/privacy-and-security/risks/file-providers

## Direct filesystem APIs

**FACT:** For paths the app is genuinely allowed to access, normal Java/Kotlin file APIs and `android.system.Os` can expose real filesystem semantics. `android.system.Os` includes POSIX-like operations such as stat/chmod/chown/link and descriptor operations, but Linux DAC, app UID sandboxing, SELinux, mount policy, read-only mounts and scoped-storage restrictions still determine whether an operation succeeds.

**RECOMMENDATION:** Direct-local storage should expose richer metadata than SAF when available: device/inode where useful, mode bits, uid/gid, symlink identity, timestamps and seekable descriptors. These fields must remain optional at the cross-provider layer.

Source:
- https://developer.android.com/reference/android/system/Os

## Removable SD and USB OTG

**FACT:** Removable storage may be visible through SAF roots; providers can add/remove roots dynamically, so disconnect is a first-class event. All-files access can expose broad shared/removable storage where Android presents it as such, but this is not a guarantee for every USB mass-storage stack/vendor combination.

**RECOMMENDATION:** Represent a volume/source separately from entries so that eject/disconnect invalidates an entire source cleanly. Operations must tolerate disappearance during list/read/write/copy.

**UNRESOLVED / PHYSICAL DEVICE:** Validate exFAT/FAT32/portable-storage behavior, USB OTG disconnect during I/O, vendor-specific permission prompts, SD-card rename/atomicity, and 4 GiB limits on FAT32 volumes.

Source:
- https://developer.android.com/guide/topics/providers/document-provider

## `Android/data`, `Android/obb`, private directories

**FACT:** Modern Android deliberately restricts cross-app access to app-private and app-specific directories. SAF tree selection cannot be used as a generic bypass for `Android/data` or `Android/obb`. All-files access does not turn these into unrestricted shared folders.

**RECOMMENDATION:** Standard/non-root mode must show honest capability limits rather than present inaccessible system locations as normal writable folders. Optional root support is a separate provider/capability.

Source:
- https://developer.android.com/about/versions/11/privacy/storage

## Symlinks and filesystem metadata

**FACT:** Direct Linux filesystem access can distinguish symlinks and operate on metadata where permissions permit. SAF document providers are not required to preserve or expose Unix symlink, uid/gid, mode or inode semantics.

**INFERENCE:** A universal `isSymlink`, POSIX permission field, or inode field would either lie for SAF/cloud/network providers or become nullable/provider-specific.

**RECOMMENDATION:** Put Unix metadata behind optional capability/data facets. Never silently follow symlinks during destructive recursive operations without a defined policy.

## POSIX permissions

**FACT:** Android's userspace can expose POSIX-style APIs, but ordinary third-party apps cannot chmod/chown arbitrary protected paths. Even root may be constrained by SELinux, mount flags, kernel policy, or read-only/verified partitions.

**RECOMMENDATION:** chmod/chown belong to an optional elevated/direct capability, never to the baseline storage interface.

## Large files, descriptors, mmap

**FACT:** Android/Linux and Java/Kotlin file APIs support files larger than 4 GiB when the underlying filesystem does. A 4 GiB ceiling is typically a filesystem/protocol issue (for example FAT32), not a general Android API limit.

**FACT:** Memory mapping (`mmap`) requires a real seekable file descriptor/mappable object. A generic `content://` source delivered through a pipe cannot safely be assumed mappable.

**RECOMMENDATION:** The operation engine should stream large files in bounded buffers and should not require mmap. mmap can be a local/provider-specific optimization after descriptor/seekability validation.

**UNRESOLVED / PROTOTYPE REQUIRED:** Benchmark descriptor-based random access and mmap on direct local files and selected SAF providers. Validate >100 GB sparse/non-sparse test files only on safe dedicated test media.

## Path vs URI constraints

| Property | Direct path | SAF / `content://` |
|---|---|---|
| Stable textual path | Usually within same mount | Not guaranteed; document URI/ID is canonical identity |
| Native `File` APIs | Yes if permission allows | Not generally |
| POSIX metadata | Potentially | Provider dependent / often absent |
| Seek | Regular files: yes | Provider/descriptor dependent |
| mmap | Regular FD: feasible | Only if provider yields suitable regular FD |
| Atomic rename semantics | Underlying FS dependent | Not guaranteed by SAF rename capability |
| Watch with local FS mechanisms | Often feasible | No universal SAF watch contract |

## Capability matrix

Legend: **Y** = normally available with stated access; **P** = provider/protocol/filesystem dependent; **N** = no portable semantic; **E** = elevated/root-only in ordinary protected areas. “Network” aggregates SMB/SFTP/WebDAV and therefore deliberately uses P where semantics differ. “Cloud” means first-class Drive/OneDrive/Dropbox APIs, not SAF wrappers.

| Operation | Local/direct | SAF | Root | Network | Cloud |
|---|---:|---:|---:|---:|---:|
| list | Y | Y | Y | Y | Y |
| stat/basic metadata | Y | P | Y | P | Y |
| read | Y | Y | Y | Y | Y |
| random read | Y | P | Y | P | P |
| write/replace | Y | P | Y | P | Y |
| append | Y | P | Y | P | N/P |
| rename | Y | P | Y | P | Y/P |
| atomic rename | P (same FS rules) | N/P | P | P | N/P |
| copy | Y (client-side) | P | Y | P | Y/P |
| move | Y/P | P | Y/P | P | Y/P |
| delete | Y | P | Y | Y/P | Y |
| mkdir | Y | P | Y | Y/P | Y |
| chmod | P/E | N | Y/P | P (SFTP/extensions) | N |
| chown | E | N | Y/P | P (SFTP/extensions) | N |
| symlink | P/E | N/P | Y/P | P | N |
| seek | Y | P | Y | P | P (range APIs) |
| file watching | Y/P | N | Y/P | P (protocol-specific) | P (delta/change APIs) |

### Matrix interpretation

**RECOMMENDATION:** Never convert **P** into a fake universal “yes”. Each concrete source should report runtime capabilities for the current entry/source. Atomicity, resumability and random access should be explicit guarantees, not inferred from method names.

## Android 16-specific notes

**FACT:** If the future app targets API 36, Android 16 makes edge-to-edge behavior and predictive-back changes relevant to UI, but these do not alter the storage model directly. Android 16 also changes background execution/job accounting; that matters for large storage operations and is treated in `09_BACKGROUND_OPERATIONS.md`.

**FACT:** Native dependencies must be evaluated for 16 KB page-size compatibility. This especially affects a possible native archive/FFmpeg stack and is covered in archive/conversion research.

Sources:
- https://developer.android.com/about/versions/16/behavior-changes-16
- https://developer.android.com/about/versions/16/behavior-changes-all
- https://developer.android.com/guide/practices/page-sizes

## Security requirements

1. Treat names/URIs/provider metadata as untrusted input.
2. Do not reconstruct raw paths from document display names.
3. Do not silently follow symlinks for recursive writes/deletes.
4. Validate destination containment during extraction/copy from untrusted hierarchies.
5. Bound memory regardless of reported file size.
6. Handle provider disappearance and revoked URI permissions as expected failures.

## Physical-device validation required later

- Android 12 and Android 16 direct/shared-storage behavior.
- API 36 `MANAGE_EXTERNAL_STORAGE` UX on real devices.
- SD and USB OTG attach/eject during copy.
- Representative SAF providers: list flags, rename URI identity, seek, descriptor type, write/append modes.
- `Android/data` behavior in non-root and optional-root modes.
- >4 GiB and >100 GB operations on filesystems that support them.
- FileObserver/direct watcher reliability on internal/removable filesystems.

## Recommendation summary

**Strong:** Keep direct-path and URI/provider storage distinct internally; model seekability, atomic move, POSIX metadata, descriptors and server-side operations as capabilities.  
**Plausible:** All-files access for the eventual file-manager product if distribution/policy review justifies it; SAF remains necessary for provider-mediated sources.  
**Postpone:** final permission set, min/target SDK, exact API wrappers, and whether mmap is worth using.  
**Prototype required:** SAF descriptor/seek semantics, removable media, huge-file behavior, and protected-path behavior across representative devices/providers.
