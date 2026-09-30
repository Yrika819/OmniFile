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
