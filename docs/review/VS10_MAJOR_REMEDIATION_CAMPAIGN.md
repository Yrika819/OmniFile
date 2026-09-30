# VS10 major remediation campaign

Authority reconciled before edits on 2026-09-30 UTC:

- main: `a51f4de9a66b69206cb333eb467c7d66aa4d5ea6`
- VS10: `b10fc6f2b7320762a862746bcd1abda05f18b5d9`
- clean worktree; explicit branch fetch was necessary because the clone's fetch refspec included only main.

## M1 checkpoint

`ReadAcquisitionExecutor.appWide` owns two admissions for all PreviewViewModels and Local/SAF
reads used by Preview. An admission covers resolution, classification, staging, and delivery.
No nested admission occurs: snapshot staging uses the current scope when present. Renderer work
starts after acquisition delivery, outside its work lanes.

Two fixed work threads run provider calls; two fixed revocation threads drain cleanup. Both
executors have two-entry queues. There is no admission queue. A scope schedules at most one
revocation drainer. Repeated Busy retries schedule neither work nor cleanup. The scheduled
deadline executor has one thread and removes cancelled timer tasks. Notification awaits resume
on Default, ensuring even an Unconfined consumer cannot run provider work on the deadline thread.

ACTIVE changes once to SUCCEEDED, FAILED, EXPIRED, or CANCELLED. Completion makes a result
available while the scope still owns it. The terminal success/failure decision is at delivery,
under the same lock as expiry. The injected monotonic deadline starts before resolution and
is checked before/after operations and adoption. No progress resets the deadline. The production
maximum is ten seconds; constructor validation forbids increasing it.

Tickets register before provider operations. Expiry claims each still-registered cancellation
once and queues it. Lease adoption/rejection is atomic; a returning worker initially owns the
raw resource. A late resource gets cleanup ownership. Normal close claims its lease and close
ticket atomically and runs on the charged worker. Expiry never closes or cancels synchronously.
SAF queries and descriptor opens receive CancellationSignal; isChildDocument has no cancellable
variant. All signals run through revocation. Local authority/containment remains provider-owned.

Random `.candidate` files are written, synced, closed, validated, and logically adopted. There
is no `.ready` rename in acquisition. Reconciliation recognizes old `.partial`/`.ready` artifacts
for backward cleanup. Candidate deletion waits for physical worker return to prevent recreation
by a late open/write. Accepted snapshots stay owned by a delivery holder until transfer to the
controller. Expired undelivered snapshots are disposed.

An admission releases only after terminal state, physical worker return, completed cleanup,
and delivery relinquishment. Failed/interrupted cleanup permanently retains the admission and
its resource-bearing action. A strong executor registry retains such scopes; accounting alone
would not be an ownership proof. Shutdown rejects new work and never interrupts or reclaims
retained admissions. Idle isolated runners shut down their revocation executor.

Focused checkpoint: 93 tests, 0 failures/errors/skips. Acquisition primitive: 43 tests;
end-to-end PDF acquisition: 13; existing Preview/PDF suites: 37. Stress includes 1,000 varied
terminal/delivery orderings plus two 1,000-Retry saturation scenarios. No sleeps establish races.
A test-harness error (Kotlin expression-bodied JUnit methods returning Boolean) and an old
synchronous-state assertion were diagnosed and corrected without weakening outcome assertions.

Focused ownership review:

- Physical work escape: no provider dispatcher change in Local/SAF/resolver path; detached
  charged work remains owned by the runner until return.
- Late adoption: scope lock and terminal check reject it, including available-but-undelivered results.
- Deadline blocking: no close, signal cancellation, provider callback, or filesystem work in expiry.
- Retry growth: saturation rejects before worker submission; fixed work/revocation resources.
- Late handle: worker ownership transitions to lease or cleanup; no ownerless resource interval.
- Premature release: worker return, drainer completion, deferred candidate cleanup and delivery all gate it.

ViewModel/controller acceptance races and SharedMemory handoff remain the separately scheduled
M3/M2 findings; this checkpoint does not claim those are remediated.

Additional M1 exception review found that adopted snapshots need a lease through delivery,
including allocation/packaging failure after staging. Snapshot candidates now retain that lease
until the executor's atomic delivery transition. The added post-adoption exception test requires
physical cleanup and slot release. Combined M1/M2 host checkpoint: 111 tests, zero failures,
errors or skips; debug and instrumentation APK builds also passed with strict verification.

## M2 checkpoint

`OwningResponse` has WAITING/HOLDING/TRANSFERRED/DISPOSED ownership, with a resource-free
availability deferred. Callback offer atomically gives the holder ownership or closes the
rejected incoming response. Consumer transfer is atomic and checks cancellation. All pre-transfer
cancellation, timeout, disconnect, error and pending-removal paths dispose the holder; post-transfer
cleanup belongs solely to the consumer's `use`/`finally`. Message and SharedMemory travel together
in one owning response, including malformed dimensions and consumer exceptions. Duplicate, wrong
request and wrong session callbacks close incoming memory. Worker responses now carry the session
ID, which was previously absent. A touched binding cleanup race was also repaired: abort cannot
mark a not-yet-completed bind as unbound and then leave the late successful bind registered.

Host ownership checkpoint: 17 tests, including 500 varied deterministic handoffs and 500 concurrent
callback/failure barrier races; zero failures/errors/skips. Each fake resource rejects a second close.
The consumer-after-transfer test pauses with ownership and proves disconnect leaves it open until
consumer cancellation runs its final close.

API31 AOSP ATD real Android checkpoint: both `SharedMemoryOwnershipInstrumentedTest` cases
passed (2 tests, zero failures/errors/skips). Fifty measured actual SharedMemory cycles, after five
warmups: FD samples `[63,63,63,63,63,63,63,63,63,63]`. Twenty-five actual Messenger PAGE accept/
cancel-before-transfer cycles: `[63,63,63,63,63,63]`. No GC invocation or waiting establishes closure.
The debug observation carries Unit only, is session-scoped, does not block the callback, requires
BuildConfig.DEBUG and is inert through normal/release input. No monotonic FD retention appeared
in these measured races; this is targeted evidence, not a claim about unrelated resources.

The first software Google API31 invocation failed APK installation because PackageManager had
not booted; Gradle nevertheless said BUILD SUCCESSFUL. No tests ran, and it was not accepted as
evidence. After prolonged first-boot scanning, targeted work changed to AOSP ATD and required
boot-complete and PackageManager readiness. The later full matrix retains Google APIs images.

Focused callback/exception review found no ambiguous SharedMemory owner: callback until offer;
holder until transfer; consumer thereafter; closed after final disposal. Availability completion,
exceptional completion and map removal never establish or discard ownership on their own.

## M3 checkpoint and controller integration

Production ViewModel actions, generations, installed document, drain token, latest queued
page intent, current logical page, and published state are confined to Main.immediate.
Background operations offer immutable candidates into an OwningResponse; only Unit crosses
withContext. Main accepts and publishes without suspension. Stale candidates invalidate their
exact session. Old opening/drain finally blocks clear bookkeeping only by identity. Session
invalidation schedules independently owned cleanup and does not run I/O on Main.

Controller reservations serialize opening. Preview obtains the reservation during the same
read acquisition, before staging handoff: waiting consumes its absolute deadline and one slot.
The snapshot and reservation leases transfer together under the scope lock. Snapshot workspace
initialization/reconciliation is lazy and occurs in a registered acquisition operation, not
application construction. Metadata reads on a handle are also registered operations.

The controller retains an exact active cleanup owner before renderer creation/open can fail.
Accepted candidates waiting for prior cleanup have a strong pending-snapshot registry. Cleanup
runs on one serial IO lane and survives caller cancellation. A new native client cannot open
until the prior exact owner's cleanup completes. Cleanup failure retains the prior owner and
prevents unsafe native reuse. Controller clearing uses compareAndSet(exactOwner, null).

The Android client observes CLOSED for its exact shutdown session/request, or actual service
/Binder death, before unbinding and completing cleanup. Generic transport failure does not
prove death and retains ownership. The request send/close decision is serialized off Main.
A cancelled request with no response sends CANCEL; cancelling an already accepted PAGE closes
the holder without killing a reusable worker. The worker's PdfRenderer alone owns its input
descriptor after successful construction. Native close failure kills the isolated process and
cannot emit CLOSED. These are directly touched ownership fixes, not a new state architecture.

M3 host inventory: 14 ViewModel ownership tests and four controller ownership tests. The VM
suite exercises 500 varied acquisition/replacement/drain/Back orderings with deferred gates.
Existing PDF host and Preview regressions remain enabled. Checkpoint totals: 129 focused tests,
zero failures/errors/skips; debug APK and instrumentation APK build passed with strict verification.
Full Cloud Host gate: 263 tests in 36 suites, zero failures/errors/skips; lint zero errors and
27 warnings; assembleDebug and assembleDebugAndroidTest passed; strict dependency verification
passed. No dependency added.

Failures diagnosed in M3: eager-workspace fixture assumptions and synchronous physical-cleanup
assertions were changed to explicit initialization/cleanup barriers. One new cancellation test
initially cancelled during staging rather than controller acceptance; it now waits for the
pending-snapshot ownership boundary. A missing async import and misordered Gradle task option
were corrected. No test disabled, no outcome assertion weakened, no unexplained flake.

Focused M3 review: stale A has no Main-owned write after generation/identity rejection. Old
cleanup cannot clear B (CAS), and old drains cannot erase B's drain or queue (identity). Worker
death remains qualified by session/request and exact ViewModel owner. Main performs memory-only
logical transitions; acquisition, bind, native operations, SharedMemory copy and cleanup run off
Main. Main confinement and controller CAS apply at different ownership layers; no competing
ViewModel synchronization system remains. No unresolved Blocker/Major identified at checkpoint.

Remote authority rechecked at 19:50 UTC: main and VS10 remote still matched the frozen starting
SHAs. Regression review against main and pre-remediation VS10 found no new dependency, permission,
minSdk change, provider, ZIP PDF, sharing/conversion, persistent cache, Copy/Move or playback change.

CI evidence strengthened: 17 mandatory named PDF/acquisition/ownership tests; missing XML,
malformed XML, duplicate mandatory evidence, skips/assumptions and absent cases fail. Eight
synthetic audit scenarios passed with their expected exit codes. Generated results are cleared
before each invocation. Floors increased by six new instrumentation methods to 85 (31–34/36)
and 82 (35); existing audio exclusions unchanged. Resource logs receive separate output.

## Final local candidate review and evidence

A fresh whole-diff review found that the direct controller open entry point waited for its
reservation outside admission. It now uses the same scoped preparation as PreviewEngine, via
the snapshot store's runner, with no nested admission. New tests prove direct-open Busy under
two occupied slots and reservation waiting expiring under the acquisition deadline. A separate
controller test proves old cleanup can time out a replacement without creating another native
owner. The old cleanup owner stays retained. The unused legacy snapshot dispatcher parameter
was removed; fixed acquisition lanes are the only staging execution policy.

Host test counts after these additions: ReadAcquisition 43; end-to-end PdfAcquisition 14;
OwningResponse 17; PreviewOwnership 14; PdfControllerOwnership 7; existing Preview/PDF 37.
Focused total 132. Full total 266 in 36 suites, no failures/errors/skips. The injected Unconfined
host-test seam serializes all ownership actions and background resumptions through a dispatcher
that creates no threads; release ownership always uses Main. Production's default remains Main.
The controller ownership observation is debug guarded, resource-free and local to the fixture.

API31 AOSP ATD targeted evidence (software emulation; no KVM):

- Real SAF query expiration: actual ten-second deadline, retained slot until physical query
  return, late Cursor closed. Real SAF open expiration: late descriptor closed. Two cases green.
- SharedMemory: 50 measured varied actual-memory handoffs after five warmups, samples
  `[63,63,63,63,63,63,63,63,63,63]`.
- Messenger accepted PAGE / cancellation: 25 cycles, samples `[63,63,63,63,63,63]`.
- Worker death with an accepted, untransferred PAGE: five death/Retry cycles, samples
  `[63,63,63,63,63]`; holding responses reject transfer and close on disconnect.
- Native renderer: 32 large-page open/render/close cycles passed, no staging artifacts.
- Ten worker-death/Retry cycles passed, samples `[63,63,63,63,63,63,63,63,63,63]`.
- Twenty-five Main ViewModel document/page replacement cycles passed in an isolated run;
  samples `[63,63,64,63,63]`. B remained navigable, Back awaited exact physical cleanup.
- Concurrent native page/replacement case passed separately; Local render, SAF pipe, malformed/
  truncated/oversize, isolation boundary and single worker-death cases passed.
- Files recreation/page preservation/Back passed. Search results/preview/Back passed separately
  after matching CI's zero animation scales and disabled spell checker. Five Preview screen
  regressions passed. In total 21 unique targeted methods have passing local Android evidence.

Failure record: the first new SAF fixture omitted DocumentsProvider's required MANAGE_DOCUMENTS
protection declarations; both cases failed before acquisition. The fixture was corrected to
match the existing test setup, with no app permission change. A combined class selector executed
only its first class, so later targeted work explicitly selected each class and checked XML counts.
The nine-case native stress invocation passed seven cases, including the 32-cycle and ten-death
stress, but two opens timed out before worker application creation. Android started/bound the
processes (PIDs 3735 and 3765), then killed them when the unchanged ten-second startup timeout
expired; no OmniPdfInit/worker_created event occurred for either. System logs show slow operations
and runtime GC pauses of ~0.7–0.8 seconds. This is classified as unaccelerated emulator startup
limitation. Both affected tests passed separately without a product limit change. The replacement
test now observes Error immediately rather than masking it behind a later twenty-second waiter.
Search's first timeout occurred waiting for results, before PDF acquisition; matching the CI UI
settings and isolating the case produced a pass. These failed invocations remain in the evidence
record; authoritative KVM matrices must still validate the final SHA. No product test excluded,
no assertion weakened, no safety limit raised, no GC correctness mechanism used.

Resource observations show no monotonic main-process FD retention attributable to these races.
Worker logs show intentional restarts/deaths and OS "isolated not needed" disposal; no claim of
perfectly identical system telemetry or provider physical termination is made.

The final mandatory CI contract contains 19 named tests. Floors are 87 for API31–34/36 and 84
for API35 (eight added instrumentation methods; legacy audio exclusions unchanged). Eight
negative/positive XML-gate audit scenarios passed. Missing/skipped mandatory ownership cases,
absent/malformed XML and duplicate evidence fail. No new PDF exclusion exists.

Final local review: zero unresolved Blocker, zero unresolved Major. The direct-open admission
finding, false native-close acknowledgement risk, snapshot post-adoption packaging ownership,
and cancellation/response handoff issues are fixed inside the frozen scope. Directly touched
small findings include exact callback session qualification, late bind cleanup, late unpublished
bitmap disposal, legacy staging suffix assertions, eager filesystem initialization and obsolete
staging dispatcher injection. No unrelated Minor/Nit cleanup performed. The original independent
audit's detailed Minor/Nit inventory was not supplied or present in the repository; it cannot
be claimed closed. Lint reports 27 warnings, including inner-Handler
lifetime heuristics; callback resources have explicit lifetime owners.


Additional targeted acquisition evidence: replace A during a real non-cooperative SAF query;
B renders through Local on the other admission and remains navigable. A is CANCELLED while its
physical query still retains one slot. Releasing A closes its late Cursor and returns admission;
it cannot publish into B. Back awaits B's physical close and leaves zero candidate artifacts.
The three ReadAcquisition instrumentation cases passed together. Twenty-two unique targeted
Android methods now have passing evidence. GitHub Host CI run 36778208036 passed on c31bb9b:
36 suites, 266 tests, zero failures/errors/skips, lint and both APK builds green under strict
verification. The added Android case advances the final CI SHA; that earlier Host run is a
checkpoint, not final authority. No further production changes were made after that checkpoint.
