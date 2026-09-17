# POC-001 — Architecture Impact

Architecture authority remains unchanged at `79fc0f18c7f5e1d8e0ae714808f89977ff178b62`.
This document records evidence only and does not amend Architecture V1.

|Architecture V1 decision|Classification|Evidence / reason|
|---|---|---|
|Capability-based storage model|CONFIRMED|Direct regular files and provider pipe descriptors expose materially different capabilities under the same broad concept of readable storage.|
|Provider-scoped opaque identity|CONFIRMED|Direct presentation paths changed across rename/move while observed filesystem identity remained stable; this supports separating identity from presentation path. SAF identity remains unmeasured.|
|Path string as universal identity rejected|CONFIRMED|Rename/move changed path text while observed direct file key remained stable.|
|Sequential vs seekable vs random access separated|CONFIRMED|Synthetic provider descriptor was sequentially readable but `lseek` failed with `ESPIPE`.|
|Native descriptor availability as optional facet|CONFIRMED|Both regular and FIFO descriptors existed, but their semantics differed.|
|Universal mmap/seek assumptions rejected|CONFIRMED|A descriptor alone did not imply seekability. mmap itself was not separately tested.|
|Provider-specific write/append/random-write semantics|CONFIRMED|Direct operations succeeded, but provider-backed write semantics were not assumed from them.|
|Provider lifecycle/disconnect first-class|UNAFFECTED|Physical disconnect/revocation could not be measured. No contrary evidence.|
|Unknown metadata remains unknown|CONFIRMED|The synthetic pipe intentionally had no meaningful size and did not require fabricated metadata.|
|Exact SAF adapter semantics|UNAFFECTED / STILL POC_REQUIRED|Normal picker could not be validated because of emulator UI ANRs.|
|Rename atomicity is never inferred from method name|CONFIRMED|The test records success/identity only and does not elevate local rename success into a universal atomicity claim.|

## Refinement input

### Filename validity

Classification: `REFINED` input for later design review.

Observed direct shared-storage behavior rejected a double-quote filename and newline filename with `EPERM` while accepting Unicode, emoji, spaces, and tested shell metacharacters. A future provider capability model or validation layer should therefore treat filename admissibility as provider/storage-surface specific rather than a universal path grammar.

This is not an Architecture V1 contradiction.

## Challenged / contradicted principles

None from the completed measurements.

## Unresolved evidence needed before Technology Freeze

- real user-selected SAF tree behavior on API 31 and API 36;
- rename/move URI and document-ID stability for representative DocumentsProviders;
- persisted grant restart/revocation;
- SD and USB disconnect behavior;
- cloud-backed provider descriptor/seek/write semantics;
- OEM-specific deviations.

No production interfaces are frozen by this PoC.