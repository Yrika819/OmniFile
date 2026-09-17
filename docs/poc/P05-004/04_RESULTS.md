# P05-004 Provider and Access Evidence

## Why access is a separate gate

P0 measured direct files, local SAF documents, and pipe-backed descriptors as materially different capabilities. A valid descriptor did not imply seekability, and the tested SAF provider’s behavior was explicitly provider-specific ([`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:23-35`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md)). Archive engines must therefore be evaluated against access capabilities rather than a universal `File` assumption.

## Proposed matrix

| Source kind | Required access cases | Candidate-sensitive questions | Current state |
|---|---|---|---|
| Direct regular file | sequential, seekable, random-offset | central-directory/index access; repeated entry opens | P0 storage evidence exists; candidate-specific mapping `UNKNOWN` |
| SAF seekable document | sequential + seekable | does listing require local staging; are offsets stable | P0 tested one local provider; not universal |
| SAF pipe/non-seekable document | sequential only | can listing/extraction stream; does an engine wrongly assume seek | P0 synthetic FIFO evidence; candidate matrix `NOT_TESTED` |
| Remote/range source | range/offset where advertised | block cache, repeated central-directory reads, retry behavior | research requirement; `NOT_TESTED` in this package |
| Solid 7z/RAR | expensive sequential revisit | first-entry versus later-entry cost; cancellation latency | explicitly unresolved ([`docs/architecture/14_P0_EVIDENCE_INCORPORATION.md:55-62`](../../architecture/14_P0_EVIDENCE_INCORPORATION.md)) |

## Required measurements

For every candidate/format/source combination, record:

1. whether open/list/read works with the source’s actual access capability;
2. time to first listing row and time to open an early, middle, and late entry;
3. whether the engine requires seek, local staging, or a range cache;
4. peak managed/native memory;
5. cancellation latency and failure classification;
6. whether a retry reopens safely without duplicating output.

The P0 backlog specifically requires local direct and SAF seekable/non-seekable cases, single-entry cost, and native checks where applicable ([`docs/architecture/12_POC_BACKLOG.md:99-111`](../../architecture/12_POC_BACKLOG.md)).

## Architecture consequence

Archive entries remain virtual storage with provider-scoped identity. Random entry access must advertise cost rather than promise O(1), particularly for TAR and solid archives ([`docs/architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md:7-20`](../../architecture/05_ARCHIVE_AND_CONVERSION_BOUNDARIES.md)). No candidate in this package closes the provider/access gate.
