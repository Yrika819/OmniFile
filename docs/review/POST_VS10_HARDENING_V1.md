# Post-VS10 hardening V1

Baseline: main `048bcfd84bd7e8120822e938f4f6f5dd5edb1672`.
This is a separate hardening slice. VS10 remains `CLOSED_GREEN_WITH_LIMITATIONS`;
its historical closure record is preserved. VS11 is not started. No merge is authorized.

## A — MIN-1 / NIT-1: classification

Supported PNG/JPEG/BMP magic at byte zero wins, followed by bounded PDF evidence,
known ZIP/unsupported handling, then binary/text heuristics. MIME/extension hints
retain their existing empty-text use; neither authorizes PDF.

OmniFile's application classifier contract recognizes the complete five-byte
`%PDF-` signature whose **start** offset is 0 through 1024 inclusive. Starts at
1019, 1020, 1023 and 1024 qualify; 1025 does not. An incomplete signature at 1024
fails recognition. This is not a universal PDF specification requirement.
Printable text containing eligible PDF evidence retains PDF classification.

The real Android regression decodes a valid PNG containing a CRC-correct early
tEXt chunk with `%PDF-1.7`, despite a PDF extension. It also checks normal
PNG/JPEG/BMP decoding, valid JPEG with PDF-looking COM metadata, valid BMP
with PDF-looking pixel bytes, and verifies no staged PDF artifacts. Files and Search
navigation require an image Preview and absence of PDF/error UI. Three new cases
are mandatory under a separate post-VS10 contract; the original 19-case contract
is untouched. Full matrix floors increase from 106/103 to **109/106** (API35).
The targeted mode runs 28 affected cases, retaining all 22 mandatory cases.

## B — MIN-2: evidence integrity

Identity is the exact `(classname, name)` pair emitted by AGP, including any
parameter suffix. Every duplicate fails closed, including identical reports and
conflicting statuses. Counts come solely from unique concrete records. Nested
suite roll-ups add no evidence. Declared test totals must agree with concrete
descendants; positive anonymous counts fail. Empty zero-count suites are valid.
Explicit skipped and assumption records remain visible and subject to named
skip policy; mandatory cases must pass regardless of skip authorization.

Compatibility authority: generated Emulator CI run **36782453459**, API36 job
**110115777002**, artifact **11128313613**. Its unmodified AGP XML has a
`testsuites` root, 25 class suites, and 106 concrete records with classname/name/time.
The parser reports 104 passed, one genuine failure, and one assumption skip;
the original report was a failing run, not hardening acceptance evidence.
Assumptions appear as a failure body containing `AssumptionViolatedException`.
No parameterized cases were present. A reduced, sanitized three-case fixture
preserves nesting and assumption representation; synthetic parameter suffix
checks protect exact-name behavior without claiming observed parameterization.
No serials, private paths, timestamps or machine metadata are committed.

The 30 deterministic Python self-tests include the required duplicate, nested,
anonymous, skip, mandatory, missing/malformed, API35 and compatibility checks.
The exact five false-green exploit classes reject or normalize: duplicate
nonmandatory record rejects; anonymous tests=1 rejects; parent105/child105 counts
105; duplicate report rejects; anonymous skipped count rejects.

Discovery uses only `app/build/outputs/androidTest-results/connected`, which the
runner removes before Gradle. No fallback selects other invocations/variants.
Exclusions and wall-clock diagnostics are cleared too. Gradle nonzero still
propagates, and the mandatory runner audit shares the aggregate parser.
Parser self-tests run separately in Host CI.

## C — MIN-3: deadline precedence

For a newly observed exception in ACTIVE, `fail()` checks the monotonic absolute
deadline under the same terminal-state lock. At/after the deadline, EXPIRED and
AcquisitionTimeout win even with an undispatched timer. Before it, the original
exception wins. Existing terminal states are never rewritten. Completion and
adoption keep their existing absolute-deadline rejection checks.

Deterministic tests control time and cleanup queues; no sleep proves this race.
Coverage includes boundary exceptions, permissions/I/O/cancellation, prior
terminal states, typed failure values, no late success/adoption, exactly-once
cleanup, worker-return admission retention, blocked/failed cleanup, and Preview
AcquisitionTimeout mapping followed by Retry. Resource ownership, ticket/lease
semantics and physical slot release are unchanged.

## D — NIT-2: terminology

Stress evidence is described as:

- 1,000 iterations across four deterministic terminal/delivery scenarios.
- 500 iterations across four deterministic generation/drain scenarios.
- 500 iterations across five callback/cancellation templates.
- 500 concurrent callback/failure race attempts; schedule diversity unmeasured.

Only host test names/comments change. Iteration counts and mandatory Android
names are unchanged. Historical VS10 closure wording is preserved.

## Acceptance and next review

Require the strict full host gate, separate parser self-tests, targeted Android
CI, and two complete API31–36 matrices on one unchanged final SHA. Record exact
counts and compare Gradle execution with aggregate and mandatory evidence.
A green run alone does not establish parser correctness.

The next step after automation and whole-diff review is an independent read-only
Sol re-audit of baseline through the final branch SHA: image/PDF/text/ZIP/hint
classification; unique concrete AGP evidence and freshness; deadline terminal
precedence and ownership; terminology and scope boundaries. Resolve Blocker/Major
findings before declaring readiness. Do not merge or reopen VS10/start VS11.
After re-audit, physical acceptance is limited to a small API37 MIN-1 image/PDF
check; no full Pixel or VS10 physical campaign belongs to this slice.
