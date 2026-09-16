# Licensing and Distribution Research

Status: COMPLETE — pre-selection audit only; no dependency or distribution architecture selected  
Last verified: 2026-09-17

## Classification legend

- **FACT** — supported by the cited project/license/policy source.
- **INFERENCE** — consequence for distribution/architecture.
- **RECOMMENDATION** — proposed direction, not legal advice and not frozen authority.
- **UNRESOLVED** — exact artifact/build/configuration must be reviewed before release.

> This document is an engineering license/policy inventory, not legal advice. Final release artifacts and enabled build options must be reviewed against their exact licenses and applicable jurisdiction/policies.

## Executive finding

The lowest-friction dependency path is predominantly permissive (Apache-2.0/MIT/BSD) and pure Java/Kotlin. The main distribution risks are:

1. FFmpeg configuration changing LGPL/GPL obligations;
2. Junrar/UnRAR's non-standard license and prohibition on using UnRAR code to recreate RAR compression;
3. any future native archive/media codecs and 16 KB page-size/ABI obligations;
4. broad Google Drive OAuth scopes and verification requirements;
5. Google Play `MANAGE_EXTERNAL_STORAGE` review for an all-files file manager;
6. foreground-service declarations/policy;
7. current Google Play target-API requirement (API 36 for new apps/updates as of 2026-08-31).

No production credentials, OAuth apps, dependency declarations, manifest permissions or publishing configuration were created in this phase.

## Dependency candidate license matrix

| Capability / candidate | License observed | Distribution implication | Current recommendation |
|---|---|---|---|
| AndroidX / Media3 | Apache-2.0 | preserve license/notices as applicable; low copyleft risk | strong candidate |
| AndroidX Material3 / Adaptive / WorkManager etc. | Apache-2.0 | standard AndroidX notice obligations | strong platform family |
| Apache Commons Compress | Apache-2.0 | include license/NOTICE requirements | strong archive baseline candidate |
| Zip4j | Apache-2.0 | permissive attribution/license compliance | strong ZIP candidate |
| Junrar / embedded UnRAR-derived code | **UnRAR freeware license / non-standard** | may be used for RAR handling, but license explicitly forbids using sources to recreate RAR compression; document terms | prototype/legal-review candidate |
| libarchive | New BSD | permissive; preserve copyright/license notice | native alternative |
| libsu | Apache-2.0 | permissive; root-specific security risk remains | root prototype candidate |
| SMBJ | Apache-2.0 | permissive; audit transitive crypto dependencies | preferred SMB prototype |
| jcifs-ng | LGPL-2.1 | copyleft/relinking/source-notice implications require care | compatibility alternative |
| SSHJ | Apache-2.0 | permissive; keep security updates current | preferred SFTP prototype |
| Apache MINA SSHD | Apache-2.0 | permissive | SFTP/SSH alternative |
| dav4jvm | MPL-2.0 | file-level copyleft: modifications to MPL-covered files carry MPL obligations | WebDAV candidate with review |
| Coil | Apache-2.0 | permissive image-loading candidate | optional; not frozen |
| jaudiotagger 3.0.1 line | LGPL | library modifications/relinking obligations; Android suitability/maintenance must be reassessed | existing FLACtify reference only; re-evaluate |
| Google API Java client/generated services | Apache-2.0 | permissive, but OAuth policy is separate | cloud API option |
| MSAL Android | MIT | permissive; identity/platform terms separate | OneDrive auth candidate |
| Dropbox Java SDK | MIT | permissive; current SDK/runtime compatibility must be checked | Dropbox API option |
| SQLDelight | Apache-2.0 | permissive | database alternative, not selected |
| Room / AndroidX SQLite | Apache-2.0 | permissive | persistence candidates, not selected |

### Primary license sources

- Media3: https://github.com/androidx/media
- Commons Compress: https://github.com/apache/commons-compress
- Zip4j: https://github.com/srikanth-lingala/zip4j
- Junrar license: https://github.com/junrar/junrar/blob/master/LICENSE
- libarchive: https://github.com/libarchive/libarchive
- libsu: https://github.com/topjohnwu/libsu
- SMBJ: https://github.com/hierynomus/smbj
- jcifs-ng: https://github.com/AgNO3/jcifs-ng
- SSHJ: https://github.com/hierynomus/sshj
- Apache MINA SSHD: https://mina.apache.org/sshd-project/
- dav4jvm: https://github.com/bitfireAT/dav4jvm
- Coil: https://github.com/coil-kt/coil
- jaudiotagger: https://jthink.net/jaudiotagger/
- Google Java API client: https://github.com/googleapis/google-api-java-client
- MSAL Android: https://github.com/AzureAD/microsoft-authentication-library-for-android
- Dropbox Java SDK: https://github.com/dropbox/dropbox-sdk-java
- SQLDelight: https://github.com/sqldelight/sqldelight

## Apache-2.0 candidates

**FACT:** Media3, Commons Compress, Zip4j, libsu, SMBJ, SSHJ, Apache MINA SSHD, Google API Java client, SQLDelight and AndroidX components use Apache-2.0-family licensing according to their project metadata/license files.

**RECOMMENDATION:** Maintain a generated third-party notices inventory later, but manually review it before release. Do not assume Gradle dependency metadata captures every native/transitive notice correctly.

Commons Compress explicitly includes a NOTICE file and calls out notice/attribution requirements:
- https://github.com/apache/commons-compress

## Junrar / RAR licensing

**FACT:** Junrar's repository license is the UnRAR freeware license. It permits use of UnRAR sources for handling RAR archives but explicitly states the source may not be used to recreate the proprietary RAR compression algorithm.

Source:
- https://github.com/junrar/junrar/blob/master/LICENSE

**INFERENCE:** RAR extraction/browsing is materially different from implementing RAR creation. The project should not infer that an extraction library provides a legal/technical path to a RAR-compatible archiver.

**RECOMMENDATION:** Keep RAR creation unsupported unless a separately licensed, legitimate encoder path is later identified. Re-review the exact Junrar artifact/license before dependency freeze.

## libarchive

**FACT:** libarchive is distributed under a New BSD-style license.

Sources:
- https://www.libarchive.org/
- https://github.com/libarchive/libarchive

**Distribution implications:** permissive copyright/license notice, but native code introduces:
- ABI packaging;
- security patch ownership;
- Android 16 KB page-size compatibility;
- native crash/symbol/debug considerations.

16 KB reference:
- https://developer.android.com/guide/practices/page-sizes

## FFmpeg

### Base license

**FACT:** FFmpeg is LGPL 2.1-or-later by default. Enabling GPL components/configuration changes the resulting FFmpeg build to GPL according to FFmpeg's own legal guidance.

Source:
- https://ffmpeg.org/legal.html

### Consequences

LGPL use can require, depending on linking/distribution method and version/configuration:
- license/copyright notices;
- source availability for the LGPL-covered library/modifications;
- ability to replace/relink the LGPL component where required;
- preserving user rights under the LGPL.

GPL-enabled builds impose stronger copyleft obligations and may be incompatible with a desired proprietary distribution model.

**RECOMMENDATION:** If FFmpeg is eventually needed:
1. freeze the exact configure flags and included external codecs;
2. produce an automated license manifest from the actual build;
3. prefer LGPL-only build unless a GPL feature is deliberately accepted after review;
4. provide corresponding-source/relinking materials as required;
5. never import an opaque third-party binary with unknown flags.

### FFmpegKit status

**FACT:** The original `arthenica/ffmpeg-kit` project is retired/archived. `ffmpeg-kit-next` is the maintained continuation and expects source builds rather than publishing the old ready-made package model. Its documentation distinguishes LGPL-3.0 default builds from GPL-3.0 builds when GPL components are enabled.

Sources:
- https://github.com/arthenica/ffmpeg-kit
- https://github.com/arthenica/ffmpeg-kit-next

**RECOMMENDATION:** Abandoned FFmpegKit binaries are explicitly not candidates.

## Media3

**FACT:** AndroidX Media3 is Apache-2.0 licensed.

Source:
- https://github.com/androidx/media

The larger concern is not copyleft but codec/platform availability and any patent/licensing obligations associated with distributing/using particular media codecs. Android platform codec support does not automatically settle all patent/commercial licensing questions for every distribution scenario.

**RECOMMENDATION:** Prefer platform MediaCodec/Media3 where product requirements fit; audit any bundled software encoder separately.

## SMB

### SMBJ

**FACT:** SMBJ is Apache-2.0 licensed.

Source:
- https://github.com/hierynomus/smbj

**RECOMMENDATION:** Audit its actual transitive dependency graph at dependency-freeze time, particularly cryptography libraries, because transitive versions/licenses can change independently of the top-level API.

### jcifs-ng

**FACT:** jcifs-ng is LGPL-2.1 licensed.

Source:
- https://github.com/AgNO3/jcifs-ng

**INFERENCE:** It is distributable in many applications, but imposes more compliance work than Apache-licensed SMBJ. It remains an alternative if interoperability materially justifies it.

## SFTP / SSH

### SSHJ

Apache-2.0:
- https://github.com/hierynomus/sshj

### Apache MINA SSHD

Apache-2.0:
- https://mina.apache.org/sshd-project/

**RECOMMENDATION:** License is not the primary differentiator; Android footprint, cryptographic algorithm support, security patch cadence and protocol behavior should decide after prototype.

## WebDAV

**FACT:** dav4jvm is MPL-2.0 licensed.

Source:
- https://github.com/bitfireAT/dav4jvm

**INFERENCE:** MPL is file-level copyleft rather than whole-application GPL-style copyleft. Modifications to MPL-covered source files generally need to remain available under MPL terms, while separate application files can remain under other terms subject to the license.

**RECOMMENDATION:** If selected, avoid casually copying library source into app-owned files; preserve library boundaries and notices, and review modifications separately.

## Image codecs / image loading

### Android platform codecs

Using platform JPEG/PNG/WebP/HEIF/AVIF support avoids bundling a separate codec library for baseline conversion/preview. Exact format/encoder availability remains API/device-dependent.

Reference:
- https://developer.android.com/media/platform/supported-formats

### Coil

**FACT:** Coil 3.x is Apache-2.0 licensed and provides image loading/caching; research observed 3.6.2 in current documentation. It is a *loading/cache* candidate, not a replacement for all conversion codecs.

Sources:
- https://github.com/coil-kt/coil
- https://coil-kt.github.io/coil/

**RECOMMENDATION:** No image library is selected. Prefer platform codecs first; only add native image codecs if a proven format/fidelity gap justifies binary/license cost.

## Audio metadata library

FLACtify currently uses `net.jthink:jaudiotagger:3.0.1`.

**FACT:** JThink states jaudiotagger is available under the Lesser General Public License. The old 3.0.1 line remains the FLACtify reference. A newer maintained fork found during research explicitly removed Android compatibility, so it cannot be silently substituted.

Sources:
- https://jthink.net/jaudiotagger/
- https://github.com/RouHim/jaudiotagger

**RECOMMENDATION:** Treat jaudiotagger selection as **unresolved**. Before reuse, verify exact artifact license text, Android 12–16 behavior, malformed-file security, maintenance status and tag-write correctness. Alternatives should be compared rather than inheriting FLACtify's dependency automatically.

## Cloud SDKs

### Google Drive / Google Java clients

**FACT:** Google's Java API client/generated API service repositories are Apache-2.0 licensed.

Sources:
- https://github.com/googleapis/google-api-java-client
- https://github.com/googleapis/google-api-java-client-services

**RECOMMENDATION:** SDK vs direct REST remains open. Authentication/OAuth scope policy is a larger architectural constraint than the client library license.

### Microsoft MSAL / Graph

**FACT:** MSAL for Android is MIT licensed.

Source:
- https://github.com/AzureAD/microsoft-authentication-library-for-android

A future Graph client choice must be separately checked; MSAL is authentication, not the whole OneDrive file client.

### Dropbox

**FACT:** Dropbox's official Java SDK is MIT licensed. Current repository guidance states SDK v7.0.0+ is required for compatibility with Dropbox API server certificate changes from January 2026; current 8.x requires Java 21+, while 7.x supports older Java versions and Android.

Source:
- https://github.com/dropbox/dropbox-sdk-java

**RECOMMENDATION:** This is a concrete reason not to freeze “latest SDK” today. Future Android toolchain/JVM compatibility should decide SDK vs direct REST and exact version.

## Google Play: `MANAGE_EXTERNAL_STORAGE`

**FACT:** Google Play restricts the All files access permission. File managers are an explicitly recognized permitted core-use category, but the permission must be genuinely central to the app, requested appropriately and declared for review.

Sources:
- https://developer.android.com/training/data-storage/manage-all-files
- https://support.google.com/googleplay/android-developer/answer/10467955

**RECOMMENDATION:** Preserve the option but do not add the permission during research. Before Play distribution, document why SAF/MediaStore cannot fulfill the core general-purpose file-management function and complete the current Play declaration/review flow.

## Google Play: target API

**FACT:** From **2026-08-31**, Google Play requires new mobile apps and app updates to target **Android 16 / API 36 or higher**, subject to the policy's stated extension process. Existing-app availability has separate requirements.

Source:
- https://support.google.com/googleplay/android-developer/answer/11926878

**IMPORTANT:** This is a distribution fact, **not** a decision in this repository. `targetSdk` remains intentionally unfrozen; if public Play distribution is pursued under current policy, API 36+ would be required for a new/update submission now.

## Google Play: foreground services

**FACT:** Google Play requires foreground-service usage to fit permitted user-beneficial use cases and requires foreground-service type declarations/details for relevant Android 14+ submissions.

Source:
- https://support.google.com/googleplay/android-developer/answer/13392821

**RECOMMENDATION:** Long-running operations should use Android's purpose-specific mechanisms (including UIDT where appropriate) and retain a durable Operation Manager. Do not declare broad FGS types preemptively “just in case.”

## Google Drive OAuth scope policy

**FACT:** Drive scopes have classifications. `drive.file` is recommended/non-sensitive for many per-file/user-selected use cases, while broad `drive`/`drive.readonly` access is restricted and can require verification; restricted-scope data handling may trigger additional security-assessment obligations.

Sources:
- https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- https://developers.google.com/identity/protocols/oauth2/production-readiness/restricted-scope-verification

**RECOMMENDATION:** Prototype the narrowest scope that supports the intended UX before requesting broad all-Drive access. No OAuth app/client was registered in this phase.

## Native library / 16 KB page-size distribution

**FACT:** Android requires native libraries to be compatible with modern 16 KB page-size environments according to current Android guidance. This matters for libarchive, FFmpeg or any future native codec.

Source:
- https://developer.android.com/guide/practices/page-sizes

**RECOMMENDATION:** If native code is selected, require reproducible source builds, ABI inventory, ELF/page-size validation and physical/emulator testing as dependency acceptance gates.

## Codec-specific concerns

Open-source library copyright license and media patent/license obligations are different questions. Potential areas requiring release-time review can include:
- H.264/AVC;
- H.265/HEVC;
- AAC;
- some commercial/proprietary codecs/containers.

**RECOMMENDATION:** Do not infer “Apache/LGPL library = no codec licensing concern.” Prefer platform codec availability where possible and review any bundled encoder/decoder separately.

## Source-distribution / notice obligations by family

### Permissive Apache/MIT/BSD
Typically require retaining copyright/license/NOTICE terms but do not impose whole-app copyleft. Exact NOTICE requirements vary by project/artifact.

### LGPL
Can require source for covered modifications and user relinking/replacement rights depending on linkage/distribution details. Android static/native packaging details must be reviewed against the exact LGPL version/artifact.

### MPL-2.0
File-level source disclosure obligations attach to covered files/modifications rather than automatically to the entire separate larger work.

### GPL
Strong copyleft. If a bundled FFmpeg build becomes GPL, release architecture/source obligations must be deliberately accepted before distribution.

### UnRAR non-standard license
Permits RAR handling under stated terms but explicitly restricts recreating RAR compression. Do not treat as ordinary Apache/MIT-style permissive code.

## Future dependency acceptance checklist

Before any dependency is added:
- [ ] current release/maintenance/security status checked;
- [ ] exact artifact + transitive dependency licenses recorded;
- [ ] license text/NOTICE obligations captured;
- [ ] native ABI/page-size implications recorded;
- [ ] known CVEs/security advisories reviewed;
- [ ] Android 12–16 compatibility verified or scheduled;
- [ ] binary size and R8/native stripping impact measured;
- [ ] source/rebuild path available for native critical dependency;
- [ ] GPL/LGPL/MPL consequences explicitly accepted if applicable;
- [ ] codec/patent/commercial terms reviewed when relevant.

## Public-distribution checklist

Before Google Play publication:
- [ ] current target-API requirement rechecked;
- [ ] `MANAGE_EXTERNAL_STORAGE` core-use eligibility/declaration reviewed;
- [ ] FGS types and Play declarations reviewed;
- [ ] Data safety/privacy disclosures reviewed against real features;
- [ ] cloud OAuth consent/scopes/verification complete;
- [ ] third-party notices/licenses included;
- [ ] native 16 KB page-size compliance validated;
- [ ] no production secret embedded as a confidential credential;
- [ ] test accounts/keys removed from release;
- [ ] dependency/security audit completed.

## Recommendation summary

**Strong:** prefer permissive, maintained, source-verifiable dependencies when functionality is comparable.  
**Strong:** treat FFmpeg build flags, Junrar/UnRAR terms, native binaries and broad Drive scopes as explicit review gates.  
**Strong:** preserve Google Play distribution as an option by designing for current all-files, FGS, OAuth and API-36 policy constraints without freezing implementation settings today.  
**Postpone:** exact dependency versions, SDK vs REST cloud clients, metadata library, native codecs and release channel until user review/prototypes.
