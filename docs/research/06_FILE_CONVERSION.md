# Integrated File Conversion Research

Status: COMPLETE — feasibility/candidate research only  
Last verified: 2026-09-17

## Classification legend

**FACT** documented behavior; **INFERENCE** derived consequence; **RECOMMENDATION** proposed direction; **UNRESOLVED** requires prototype evidence.

## Executive finding

No single Android-native conversion engine cleanly covers all requested audio/video/image/document conversions. The lowest-risk architecture is a conversion capability layer with separate engines:

- Android/Media3/MediaCodec for common video/audio media workflows where supported;
- platform image codecs for common image conversion/resizing;
- optional source-built FFmpeg only where breadth justifies native/licensing cost;
- archive conversion through archive reader/writer pipelines;
- high-fidelity Office-to-PDF should remain unsupported locally unless a defensible renderer is later proven.

## Audio conversion

Target families:
- FLAC
- WAV
- MP3
- AAC/M4A
- Ogg Vorbis
- Opus
- ALAC where feasible

### Preservation model

Audio conversion has two separate products:

1. encoded audio samples;
2. container/tag metadata.

**RECOMMENDATION:** Treat metadata transfer as an explicit post-/pre-processing stage. Preserve only fields the destination format can represent, including:

- title, album, artist/album artist
- track/disc numbers
- date/year
- genre/comments
- embedded artwork where destination supports it
- ReplayGain-like fields only with explicit mapping
- sample rate/channel layout where user does not request change
- bit depth for PCM/lossless targets where meaningful

Do not claim “lossless metadata preservation” across incompatible tag/container models.

### Lossless vs lossy

- FLAC -> WAV can preserve decoded PCM fidelity but metadata models differ.
- WAV -> FLAC is lossless for PCM samples if sample format is preserved.
- lossless -> MP3/AAC/Vorbis/Opus is inherently lossy.
- lossy -> another lossy codec introduces generation loss and should be warned about.
- ALAC feasibility depends on chosen engine/container support.

### Media3 for audio

**FACT:** Media3 Transformer can transcode media and has muxers for MP4 plus dedicated AAC/Ogg/WAV/WebM-related outputs in current documentation. It uses platform MediaCodec for encoding, so exact codec availability varies by Android version/device.

Sources:
- https://developer.android.com/media/media3/transformer
- https://developer.android.com/media/media3/transformer/supported-formats
- https://developer.android.com/media/media3/transformer/transformations

**RECOMMENDATION:** Prototype Media3 first for AAC and other directly supported paths, but do not assume it replaces a general audio conversion suite (especially FLAC encoding, MP3 encoding, Opus/ALAC combinations) without format-by-format verification.

## Video conversion

Target containers:
- MP4
- MKV
- WebM
- MOV

Common codecs will include H.264/AVC, H.265/HEVC, VP9, AV1 and device-specific newer codecs where platform support exists.

### Remux vs transcode

**FACT:** Media3 Transformer distinguishes transmuxing/sample-copy from transcoding. Remuxing can change container without re-encoding when source tracks are compatible with the target muxer; transcoding decodes and re-encodes.

**RECOMMENDATION:** Prefer remux when codec/container compatibility allows. It is faster, avoids quality loss and reduces battery/thermal cost.

### Media3 Transformer

**FACT:** Transformer is the official Android Media3 transformation API, supports Android 6+, uses MediaCodec for hardware-accelerated codec work and OpenGL for image/video effects. Its API is currently marked `@UnstableApi`; output support is bounded by extractors, muxers and device codec availability.

**FACT:** Current docs show default MP4 output and support for WebM/AAC/Ogg/WAV through appropriate muxers/factories. HDR editing is supported on sufficiently capable devices; HDR/SDR conversion capabilities depend on API/device codecs.

**FACT:** Transformer instances are single-application-thread objects and exports per instance are sequential.

Sources:
- https://developer.android.com/reference/androidx/media3/transformer/Transformer
- https://developer.android.com/media/media3/transformer/supported-formats
- https://developer.android.com/media/media3/transformer/troubleshooting

**RECOMMENDATION:** Media3 Transformer is the preferred first prototype for mainstream Android video remux/transcode. Its official integration and hardware path lower maintenance risk versus making FFmpeg the default for everything.

### Hardware MediaCodec

**FACT:** `MediaCodec` exposes platform decoders/encoders. Mandatory codec support evolves by Android version; Android 14 mandates AV1 encoder/decoder support in the platform format table, while Android 16 adds APV requirements. Real device capabilities still matter for resolution/profile/throughput.

Source:
- https://developer.android.com/media/platform/supported-formats
- https://developer.android.com/reference/android/media/MediaCodec

**UNRESOLVED / DEVICE PROTOTYPE:** 4K/8K, HDR, AV1/HEVC encoder availability, thermal throttling, vendor codec bugs and simultaneous decoder/encoder capacity.

## FFmpeg and FFmpegKit status

### FFmpeg

**FACT:** FFmpeg is LGPL 2.1-or-later by default, but enabling/including GPL components changes the applicable license for the resulting FFmpeg build. Codec/patent obligations are distinct from open-source copyright licensing and can vary by jurisdiction/distribution model.

Source:
- https://ffmpeg.org/legal.html

### Original FFmpegKit

**FACT:** The original `arthenica/ffmpeg-kit` repository was archived on 2026-07-02 and is officially retired. Historical prebuilt binaries should not be selected as the basis of a new long-lived project.

Source:
- https://github.com/arthenica/ffmpeg-kit

### FFmpegKitNext

**FACT:** `arthenica/ffmpeg-kit-next` is the official continuation maintained by the original author. It does **not** publish ready-to-use packages like old FFmpegKit; it is source-built (Nix-based build flow) and uses LGPL-3.0 by default, GPL-3.0 when GPL libraries are enabled according to its project documentation.

Source:
- https://github.com/arthenica/ffmpeg-kit-next

**RECOMMENDATION:** If FFmpeg breadth is eventually required, use a reproducible source build based on maintained FFmpegKitNext or an equivalent maintained integration, with exact build flags/license manifest recorded. Do not consume abandoned FFmpegKit binaries.

### Android native implications

FFmpeg is native code, therefore requires:

- ABI packaging/build ownership;
- 16 KB page-size-compatible binaries;
- security update process;
- native crash testing;
- license-compliant relinking/source/notice obligations depending on configuration.

Source:
- https://developer.android.com/guide/practices/page-sizes

## Image conversion

Target families:
- JPEG
- PNG
- WebP
- AVIF
- HEIF/HEIC
- BMP

**FACT:** Android platform support includes JPEG/PNG, WebP, HEIF decoding from Android 8+, and mandatory baseline AVIF encoding/decoding from Android 14+. Exact encode APIs/quality features differ by format/API level.

Source:
- https://developer.android.com/media/platform/supported-formats

### Resize and quality

**RECOMMENDATION:** Decode with bounded/sample-sized dimensions, transform, and encode without holding multiple full-resolution bitmaps unnecessarily. Very large images can exceed heap even when compressed input is small.

### Metadata

Conversion should decide explicitly whether to preserve:

- EXIF capture time/camera/GPS
- orientation
- ICC/wide-color information
- XMP
- alpha/transparency
- HDR gain-map / Ultra HDR metadata

**SECURITY/PRIVACY:** GPS EXIF is sensitive. “Preserve metadata” and “strip metadata” should be user-visible policies rather than hidden behavior.

### EXIF orientation

**RECOMMENDATION:** Avoid double rotation. Either preserve encoded orientation metadata with unchanged pixel orientation, or normalize pixels and update/remove orientation consistently.

### Alpha

- PNG/WebP/AVIF can support alpha depending on encode path.
- JPEG cannot; conversion needs an explicit background/compositing policy.

### HDR / wide color

**RECOMMENDATION:** Do not silently downconvert HDR/wide-gamut assets. Detect unsupported target encoders and warn/offer SDR conversion policy. Prototype Ultra HDR/HEIF/AVIF behavior on API 34–36 hardware.

## Archive conversion

Examples:
- ZIP -> 7z
- 7z -> ZIP
- RAR -> ZIP

A safe conversion is conceptually **extract-stream -> validate -> write new archive**, not binary container relabeling.

### Temporary-space models

1. **Full staging:** simplest but can require expanded archive size + output size simultaneously.
2. **Entry streaming:** read one entry and write it directly to destination archive; minimizes disk space if both engines stream.
3. **Hybrid staging:** required where target/source engine needs seek/local file or when metadata/order/encryption makes pure streaming impossible.

**RECOMMENDATION:** Prefer streaming conversion with the same traversal/symlink/decompression guards used for extraction. RAR -> ZIP is read-only RAR + ZIP writer; do not imply RAR creation.

## Document conversion: DOCX/PPTX/XLSX -> PDF

### Apache POI

**FACT:** Apache POI provides Java APIs to parse/manipulate Office OLE2/OOXML formats. Its own documentation describes Word XWPF support as incomplete and exposes parsing/text extraction; PowerPoint has rendering utilities, but this is not equivalent to a complete Microsoft Office layout engine across Word/Excel/PowerPoint.

Sources:
- https://poi.apache.org/
- https://poi.apache.org/components/
- https://poi.apache.org/components/document/quick-guide-xwpf.html
- https://poi.apache.org/components/slideshow/ppt-wmf-emf-renderer.html

**INFERENCE:** POI alone is not credible evidence for high-fidelity general DOCX/XLSX/PPTX -> PDF conversion on Android. Font availability, layout engines, pagination, charts, equations, macros and proprietary rendering behavior create fidelity gaps.

### LibreOffice-style conversion

LibreOffice desktop has headless conversion capabilities, but this research found no current supported Android embedding path that can simply be treated as a small in-app high-fidelity Office-to-PDF SDK. Porting a desktop office suite would add enormous native size, fonts, process/runtime and maintenance cost.

**RECOMMENDATION:** Mark high-fidelity local DOCX/PPTX/XLSX -> PDF as **unsupported/postponed** for initial architecture. Later options can include opening/exporting through an installed office app, a user-configured external/cloud service, or a separately proven engine. Do not market local fidelity without corpus testing against reference renderers.

## Operation Manager integration

Conversions can be long-running and must be jobs with:

- source/destination identity
- selected format/options
- progress where measurable
- cancellation
- persisted recovery state where engine supports restart/resume
- partial output marker
- storage-space preflight
- atomic/explicit finalization
- thermal/battery-aware UX

Video/audio conversion belongs under Android background execution constraints documented in `09_BACKGROUND_OPERATIONS.md`.

## License concerns

| Engine | License concern |
|---|---|
| Media3 | Apache-2.0; low copyleft risk |
| Android platform codecs | platform API, but codec patent/distribution questions may still matter for media content/features |
| FFmpeg | LGPL by default; GPL configuration can materially change obligations |
| FFmpegKitNext | wrapper/build project documents LGPL-3.0 default / GPL-3.0 with GPL components |
| Apache POI | Apache-2.0; fidelity is the larger issue, not license |

No statement here is legal advice. Exact built artifacts and enabled codecs must be audited before distribution.

## Security concerns

- media/image/document inputs are untrusted parser inputs;
- bound dimensions, duration, sample counts and output size;
- never trust extension alone for content type;
- temp files must be private and cleaned/recovered;
- preserve metadata only according to explicit privacy policy;
- native FFmpeg increases memory-safety/supply-chain surface;
- conversions must not overwrite a good source until output is verified/finalized.

## Prototype-required questions

1. Media3 remux/transcode matrix for MP4/MKV/WebM/MOV inputs and desired outputs.
2. Device encoder matrix Android 12–16: H.264/HEVC/VP9/AV1, HDR and thermal behavior.
3. Audio encode gaps that would actually require FFmpeg.
4. FFmpegKitNext reproducible Android build, LGPL-only configuration, 16 KB pages and binary size.
5. Image EXIF/ICC/HDR/alpha fidelity.
6. Streaming archive conversion without full staging.
7. If Office PDF conversion is reconsidered: large reference corpus and layout-diff validation first.

## Recommendation summary

**Strong:** Media3/MediaCodec first for mainstream video; platform APIs first for images.  
**Strong:** keep FFmpeg optional and source-built only if format gaps justify it.  
**Strong:** do not claim high-fidelity local Office -> PDF.  
**Strong:** conversion is an Operation Manager workload with partial/finalized output semantics.  
**Postpone:** exact engine/dependency versions, FFmpeg configuration, codec promise and document-conversion product scope.
