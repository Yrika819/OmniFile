# Architecture V1 — Archive and Conversion Boundaries

Status: PRINCIPLES FROZEN / ENGINE STACKS UNFROZEN

Source authority: `04_ARCHIVE_FORMATS.md`, `06_FILE_CONVERSION.md`, `11_SECURITY.md`, `14_LICENSES_DISTRIBUTION.md`, and the research synthesis at `b03a2ea99f24206f847f513fa4106e90268f3fc4`.

## Archive as browsable virtual storage — `ACCEPTED`

Where the selected engine/format permits listing, an archive is modeled as virtual storage rather than merely an “extract command”. Archive entries can participate in list/metadata/read concepts while honestly advertising their limitations.

Consequences:

- the outer archive has its own provider/source identity;
- inner entry identity is scoped to that archive source;
- virtual breadcrumbs are presentation metadata, not local filesystem paths;
- entries may be read-only;
- random entry access may be expensive or sequential for solid/compressed formats;
- whole-archive extraction is not required merely to browse.

Universal O(1) random access inside every archive — `REJECTED`.

## Secure extraction containment and limits — `ACCEPTED`

Archive extraction treats every entry name and metadata field as untrusted input.

Mandatory architectural protections include:

- trusted destination root;
- normalized/canonical containment check where real path semantics apply;
- rejection of absolute/escaping traversal paths;
- explicit symlink policy;
- expanded-byte limits;
- file-count limits;
- nesting limits;
- storage-space handling;
- malformed/truncated input handling;
- explicit overwrite/conflict policy;
- cancellation and recovery through Operation Manager;
- bounded memory.

Extraction security is an architecture property, not late hardening.

## Archive engine stack — `POC_REQUIRED`

Documentation alone is insufficient to choose the final archive stack.

The later engine PoC must compare at least:

- ZIP/ZIP64/encrypted/split ZIP;
- TAR and common compressor families;
- 7z, including solid/random-entry behavior;
- RAR4/RAR5, including encrypted/solid/multipart inputs;
- malformed/truncated/security corpus;
- 10k/100k-entry archive listing;
- SAF/remote seekable vs non-seekable origins;
- Android 16 native/16 KB compatibility if native code is evaluated.

Java-first candidates (Commons Compress, Zip4j, Junrar) and libarchive remain research candidates only. No dependency is selected here.

## Archive creation

Creating common open formats such as ZIP/ZIP64 and TAR-family outputs is architecturally compatible with Operation Manager — `ACCEPTED` as a possible product capability.

Exact V1 creation format promise — `DEFERRED` to feature-scope/user review plus engine evidence.

### RAR creation — `REJECTED` as an assumed V1 capability

RAR extraction/browsing evidence does not provide a legitimate encoder path. The current research notes UnRAR-derived licensing constraints and no proven maintainable RAR creation engine.

Replacement: RAR creation remains unsupported/uncommitted unless a separately licensed, technically credible encoder is proven later.

## Archive conversion — `ACCEPTED` as an Operation Manager workload

Format-to-format archive conversion may stream entries from source archive to destination archive when engines/capabilities permit, while applying the same traversal, symlink, expansion, conflict, partial, verification, and finalization rules as extraction.

Full temporary staging is not a universal requirement; hybrid staging may still be needed for formats/engines that require seek or local files.

Exact conversion matrix — `DEFERRED` until engine evidence.

## Conversion as an Operation Manager workload — `ACCEPTED`

Audio, video, image, and archive conversion use durable operation semantics:

`create partial destination -> process -> verify -> finalize`

If an engine cannot resume inside one file, the operation may restart the current item while preserving batch-level durable state.

## Mainstream audio/video conversion direction

A platform/Media3-first evaluation direction is `ACCEPTED` as a low-regret principle, not as dependency authority.

Reasons:

- official Android integration;
- access to platform MediaCodec/hardware paths;
- lower binary/native/license burden than treating FFmpeg as universal default.

This does not claim Media3 covers every desired codec/container.

### Media3 Transformer device capability — `POC_REQUIRED`

Real devices/API levels must validate the desired codec/container/HDR/thermal matrix before product promises are frozen.

## FFmpeg inclusion — `POC_REQUIRED`

FFmpeg is not a baseline architectural dependency.

A later decision to include it requires evidence that important product requirements remain unmet after platform/Media3 evaluation and must include:

- exact capability gap;
- reproducible source build;
- exact configure flags;
- LGPL/GPL consequence review;
- native ABI/16 KB validation;
- binary-size/security-update burden;
- codec/patent review as applicable.

Using abandoned opaque FFmpegKit binaries — `REJECTED`.

## Image conversion

Platform-first image conversion is `ACCEPTED` as the initial direction.

Architectural requirements:

- bounded/sample-sized decode for large images;
- explicit metadata-preserve/strip policy;
- correct EXIF orientation handling;
- alpha preservation when target supports it;
- no silent HDR/wide-gamut loss without user-visible policy.

Exact image codec/library stack and fidelity promise — `POC_REQUIRED` / `DEFERRED`.

## Office -> PDF — `DEFERRED`

High-fidelity local DOCX/PPTX/XLSX -> PDF is not supported by the current evidence. Apache POI parsing/manipulation is not evidence of a complete Office-compatible layout/rendering engine.

Architecture V1 therefore does not promise local Office->PDF conversion.

Reopen only if a later independent technology review identifies a credible engine, followed by a reference-corpus fidelity PoC.

## Metadata preservation

Metadata preservation is explicit, format-aware behavior — `ACCEPTED`.

A conversion must not imply that all source metadata can be represented by every destination format. Sensitive metadata such as GPS EXIF requires explicit user-visible policy.

## Security and hostile input

Malformed archives/media/images are expected inputs — `ACCEPTED`.

Future parsers/engines require curated malformed/security regression corpora, updateability, bounded resource use, and failure isolation where practical.

## Native-code boundary

Native archive/media components are not prohibited, but adoption requires a stronger acceptance gate — `ACCEPTED`.

Required later evidence includes maintained source provenance, reproducible build, license manifest, ABI inventory, Android 16/16 KB compatibility, parser security/update posture, and real-device testing.
