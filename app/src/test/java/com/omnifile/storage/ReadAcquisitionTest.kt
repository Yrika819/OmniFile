package com.omnifile.storage

import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Fake time, explicit physical-return boundaries, and manually drained revocation lanes. */
class ReadAcquisitionTest {
    private class Fixture {
        val time = AtomicLong(0)
        val revoked = ArrayDeque<Runnable>()
        var releases = 0
        val scope = ReadAcquisitionScope(time::get, 10, { revoked.add(it) }, { releases++ })
        fun drain() { while (revoked.isNotEmpty()) revoked.removeFirst().run() }
        fun returned() { scope.workerReturned(); drain() }
    }
    private fun failure(kind: ReadAcquisitionFailure, block: () -> Unit) {
        try { block(); fail("Expected $kind") } catch (error: ReadAcquisitionException) { assertEquals(kind, error.failure) }
    }
    private fun lateOperation(cancel: Boolean = false, resource: Boolean = false) {
        val f = Fixture()
        val calls = AtomicInteger()
        val disposed = AtomicInteger()
        failure(if (cancel) ReadAcquisitionFailure.CANCELLED else ReadAcquisitionFailure.TIMEOUT) {
            f.scope.blockingOperation(cancel = { calls.incrementAndGet() }, dispose = { _: Int -> disposed.incrementAndGet() }) {
                if (cancel) f.scope.cancel() else f.scope.expire()
                7
            }
        }
        assertEquals(0, calls.get()) // deadline performs no revocation inline
        f.scope.workerReturned()
        assertEquals(0, f.releases)
        f.drain()
        assertEquals(1, calls.get())
        assertEquals(1, disposed.get())
        assertEquals(1, f.releases)
    }

    @Test fun classificationReadStallLogicallyExpires() = stalled()
    @Test fun resolutionStallLogicallyExpires() = stalled()
    @Test fun openStallLogicallyExpires() = stalled()
    @Test fun stagingReadStallLogicallyExpires() = stalled()

    private fun stalled(cancel: Boolean = false) = runBlocking {
        ReadAcquisitionExecutor(timeoutMillis = 10_000).use { runner ->
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            lateinit var scope: ReadAcquisitionScope
            val result = async(start = CoroutineStart.UNDISPATCHED) {
                try { runner.acquire { scope = it; started.countDown(); release.await(); 1 }; null }
                catch (error: ReadAcquisitionException) { error.failure }
            }
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS))
                if (cancel) scope.cancel() else scope.expire()
                assertEquals(if (cancel) ReadAcquisitionFailure.CANCELLED else ReadAcquisitionFailure.TIMEOUT, result.await())
                assertEquals(1, runner.retainedSlots)
            } finally { release.countDown() }
            assertTrue(runner.awaitIdle())
        }
    }

    @Test fun continuousTrickleCannotResetAbsoluteDeadline() {
        val f = Fixture()
        repeat(9) { f.time.incrementAndGet(); f.scope.blockingOperation { 1 } }
        f.time.incrementAndGet()
        failure(ReadAcquisitionFailure.TIMEOUT) { f.scope.blockingOperation { fail("late work"); 1 } }
        assertEquals(ReadAcquisitionState.EXPIRED, f.scope.state)
    }
    @Test fun replacementDuringResolutionRejectsLateResult() = lateOperation(cancel = true)
    @Test fun replacementDuringClassificationRejectsLateResult() = lateOperation(cancel = true)
    @Test fun replacementDuringOpenClosesLateHandle() = lateOperation(cancel = true, resource = true)
    @Test fun replacementDuringStagingRejectsLateResult() = lateOperation(cancel = true)
    @Test fun cancellationDuringResolutionIsLogical() = stalled(cancel = true)
    @Test fun cancellationDuringClassificationIsLogical() = stalled(cancel = true)
    @Test fun cancellationDuringOpenIsLogical() = stalled(cancel = true)
    @Test fun cancellationDuringStagingIsLogical() = stalled(cancel = true)
    @Test fun cooperativeCancellationReturnsAndReleasesSlot() {
        val f = Fixture(); val cancelled = AtomicInteger()
        failure(ReadAcquisitionFailure.TIMEOUT) { f.scope.blockingOperation(cancel = { cancelled.incrementAndGet() }) { f.scope.expire(); 1 } }
        f.drain(); assertEquals(1, cancelled.get()); assertEquals(0, f.releases)
        f.returned(); assertEquals(1, f.releases)
    }
    @Test fun nonCooperativeCancellationRetainsSlot() = stalled()
    @Test fun lateQueryResultIsDisposed() = lateOperation()
    @Test fun lateOpenResultIsDisposed() = lateOperation(resource = true)
    @Test fun lateReturnedHandleClosedExactlyOnce() {
        val f = Fixture(); val closed = AtomicInteger()
        f.scope.expire()
        failure(ReadAcquisitionFailure.TIMEOUT) { f.scope.own(Any()) { closed.incrementAndGet() } }
        f.returned(); assertEquals(1, closed.get()); assertEquals(1, f.releases)
    }
    @Test fun cancellationRegistrationAfterExpiryNeverStartsOperation() {
        val f = Fixture(); f.scope.expire()
        failure(ReadAcquisitionFailure.TIMEOUT) { f.scope.blockingOperation(cancel = { fail("not registered") }) { fail("must not start") } }
        f.returned(); assertEquals(1, f.releases)
    }
    @Test fun operationCompletionBeforeExpiryDisarmsCancellation() {
        val f = Fixture(); f.scope.blockingOperation(cancel = { fail("completed ticket") }) { 1 }
        f.scope.expire(); f.returned(); assertEquals(1, f.releases)
    }
    @Test fun adoptionVsExpiryRejectsExpiredLease() {
        val f = Fixture(); val disposed = AtomicInteger(); val lease = f.scope.own(1) { disposed.incrementAndGet() }
        f.scope.expire(); failure(ReadAcquisitionFailure.TIMEOUT) { lease.adopt { fail("late adoption") } }
        f.returned(); assertEquals(1, disposed.get())
    }
    @Test fun twoPhysicalSlotsRemainOccupied() = saturated(1)
    @Test fun thirdAcquisitionFailsBusyImmediately() = saturated(1)
    @Test fun retrySaturatedDoesNotSpawnWorkers() = saturated(1000)
    private fun saturated(retries: Int) = runBlocking {
        ReadAcquisitionExecutor(timeoutMillis = 10_000).use { runner ->
            val started = CountDownLatch(2); val release = CountDownLatch(1)
            val scopes = java.util.Collections.synchronizedList(mutableListOf<ReadAcquisitionScope>())
            val requests = List(2) { async(start = CoroutineStart.UNDISPATCHED) {
                try { runner.acquire { scopes.add(it); started.countDown(); release.await(); 1 } } catch (_: ReadAcquisitionException) { 0 }
            } }
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS)); scopes.forEach { it.expire() }
                requests.forEach { assertEquals(0, it.await()) }; assertEquals(2, runner.retainedSlots)
                repeat(retries) {
                    try { runner.acquire { fail("Saturated runner started work") }; fail("Expected Busy") }
                    catch (error: ReadAcquisitionException) { assertEquals(ReadAcquisitionFailure.BUSY, error.failure) }
                }
                assertEquals(2, runner.workThreadCount)
            } finally { release.countDown() }
            assertTrue(runner.awaitIdle()); assertEquals(3, runner.acquire { 3 })
        }
    }
    @Test fun cancellationActionItselfBlockingRetainsSlot() = blockedCleanup(useLease = false)
    @Test fun handleCloseItselfBlockingRetainsSlot() = blockedCleanup(useLease = true)
    @Test fun slotRetainedUntilCleanupReturns() = blockedCleanup(useLease = true)
    private fun blockedCleanup(useLease: Boolean) {
        val f = Fixture(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        val action = { entered.countDown(); release.await(); Unit }
        if (useLease) { f.scope.own(1) { action() }; f.scope.expire() }
        else failure(ReadAcquisitionFailure.TIMEOUT) { f.scope.blockingOperation(cancel = action) { f.scope.expire(); 1 } }
        f.scope.workerReturned()
        val thread = Thread { f.drain() }.apply { start() }
        try { assertTrue(entered.await(5, TimeUnit.SECONDS)); assertEquals(0, f.releases) }
        finally { release.countDown(); thread.join(5000) }
        assertFalse(thread.isAlive); assertEquals(1, f.releases)
    }
    @Test fun physicalWorkerReturnReleasesAdmission() {
        val f = Fixture(); f.scope.cancel(); f.drain(); assertEquals(0, f.releases)
        f.scope.workerReturned(); assertEquals(1, f.releases)
    }
    @Test fun retrySucceedsAfterPhysicalSlotRelease() = saturated(1)
    @Test fun timeoutNeverAdoptsSnapshot() {
        val f = Fixture(); var adopts = 0; var disposes = 0
        val candidate = f.scope.own(1) { disposes++ }; f.time.set(10)
        failure(ReadAcquisitionFailure.TIMEOUT) { candidate.adopt { adopts++ } }
        f.returned(); assertEquals(0, adopts); assertEquals(1, disposes)
    }
    @Test fun partialCandidateCleanupWaitsForPhysicalReturn() {
        val f = Fixture(); var closes = 0
        f.scope.own(1, afterWorkerReturns = true) { closes++ }; f.scope.expire(); f.drain()
        assertEquals(0, closes); assertEquals(0, f.releases)
        f.returned(); assertEquals(1, closes); assertEquals(1, f.releases)
    }
    @Test fun interruptedCleanupPermanentlyRetainsAdmission() {
        val f = Fixture(); f.scope.own(1) { throw InterruptedException() }; f.scope.expire(); f.returned()
        assertEquals(0, f.releases)
    }
    @Test fun staleAcquisitionCannotPublishDelivery() {
        val f = Fixture(); var disposed = 0; f.scope.cancel(); f.scope.complete(1) { disposed++ }
        failure(ReadAcquisitionFailure.CANCELLED) { f.scope.take() }; f.returned(); assertEquals(1, disposed)
    }
    @Test fun hostileRetryThousandTimesHasFixedWorkers() = saturated(1000)
    @Test fun providerFailureKeepsOriginalError() {
        val f = Fixture(); val error = SecurityException(); f.scope.fail(error); f.returned()
        try { f.scope.take(); fail("expected error") } catch (actual: SecurityException) { assertSame(error, actual) }
    }
    @Test fun snapshotStaleBeforeDeliveryGetsDisposalOwner() {
        val f = Fixture(); var closes = 0; f.scope.complete(1) { closes++ }; f.scope.cancel(); f.returned()
        assertEquals(1, closes); assertEquals(ReadAcquisitionState.CANCELLED, f.scope.state)
    }
    @Test fun acquisitionSucceedsJustBeforeDeadline() {
        val f = Fixture(); f.time.set(9); f.scope.complete(1) { fail("consumer owns") }; f.scope.workerReturned()
        assertEquals(1, f.scope.take()); assertEquals(1, f.releases); f.scope.expire()
        assertEquals(ReadAcquisitionState.SUCCEEDED, f.scope.state)
    }
    @Test fun exactDeadlineCompletionLosesToTimeout() {
        val f = Fixture(); var closes = 0; f.time.set(10); f.scope.complete(1) { closes++ }; f.returned()
        assertEquals(ReadAcquisitionState.EXPIRED, f.scope.state); assertEquals(1, closes)
    }
    @Test fun cancellationExactlyRacingLateReturnHasOneOwner() = lateOperation(cancel = true, resource = true)
    @Test fun consumersShareSingleRunnerAdmission() = saturated(1)
    @Test fun failedAdoptionKeepsCleanupOwner() {
        val f = Fixture(); var closes = 0; val lease = f.scope.own(1) { closes++ }
        try { lease.adopt<Unit> { throw IllegalStateException() }; fail("expected") } catch (_: IllegalStateException) { }
        f.scope.cancel(); f.returned(); assertEquals(1, closes)
    }
    @Test fun normalCloseExceptionRetainsAccounting() {
        val f = Fixture(); val lease = f.scope.own(1) { throw java.io.IOException() }
        try { lease.close(); fail("expected") } catch (_: java.io.IOException) { }
        f.scope.cancel(); f.returned(); assertEquals(0, f.releases)
    }
    @Test fun thousandVariedCompletionCancellationInterleavings() {
        repeat(1000) { index ->
            val f = Fixture(); var closed = 0
            when (index % 4) {
                0 -> { f.scope.complete(1) { closed++ }; assertEquals(1, f.scope.take()) }
                1 -> { f.scope.complete(1) { closed++ }; f.scope.cancel() }
                2 -> { f.scope.expire(); f.scope.complete(1) { closed++ } }
                3 -> { f.scope.cancel(); f.scope.complete(1) { closed++ } }
            }
            f.returned(); assertEquals(if (index % 4 == 0) 0 else 1, closed); assertEquals(1, f.releases)
        }
    }
    private fun observedExceptionAt(time: Long, error: Throwable) {
        val f = Fixture()
        try {
            f.scope.blockingOperation<Int> { f.time.set(time); throw error }
            fail("Expected operation exception")
        } catch (observed: Throwable) {
            assertSame(error, observed)
            // Exactly the executor boundary: deadline elapsed, timer undispatched, operation throws.
            f.scope.fail(observed)
        }
        if (time < 10) {
            try { f.scope.take(); fail("Expected original failure") }
            catch (observed: Throwable) { assertSame(error, observed) }
            assertEquals(ReadAcquisitionState.FAILED, f.scope.state)
        } else {
            failure(ReadAcquisitionFailure.TIMEOUT) { f.scope.take() }
            assertEquals(ReadAcquisitionState.EXPIRED, f.scope.state)
        }
        assertEquals(0, f.releases)
        f.returned()
        assertEquals(1, f.releases)
    }

    @Test fun operationThrowsBeforeDeadlineKeepsOriginalFailure() = observedExceptionAt(9, IllegalStateException())
    @Test fun operationThrowsExactlyAtDeadlineGetsTimeout() = observedExceptionAt(10, IllegalStateException())
    @Test fun operationThrowsAfterDeadlineGetsTimeout() = observedExceptionAt(11, IllegalStateException())
    @Test fun elapsedDeadlineUndispatchedTimerThenOperationThrowsGetsTimeout() = observedExceptionAt(11, java.io.IOException())
    @Test fun permissionExceptionBeforeDeadlineWins() = observedExceptionAt(9, SecurityException())
    @Test fun permissionExceptionAfterDeadlineLosesToTimeout() = observedExceptionAt(11, SecurityException())
    @Test fun providerIoExceptionBeforeDeadlineWins() = observedExceptionAt(9, java.io.IOException())
    @Test fun providerIoExceptionAfterDeadlineLosesToTimeout() = observedExceptionAt(11, java.io.IOException())
    @Test fun newlyObservedOperationCancellationAfterDeadlineGetsTimeout() = observedExceptionAt(11, kotlinx.coroutines.CancellationException())

    @Test fun timerExpiryBeforeExceptionKeepsExistingTimeout() {
        val f = Fixture(); f.scope.expire(); f.scope.fail(SecurityException())
        failure(ReadAcquisitionFailure.TIMEOUT) { f.scope.take() }
        f.returned(); assertEquals(1, f.releases)
    }
    @Test fun originalFailureBeforeDeadlineSurvivesLaterTimer() {
        val f = Fixture(); val original = java.io.IOException()
        f.time.set(9); f.scope.fail(original); f.time.set(11); f.scope.expire()
        try { f.scope.take(); fail("Expected original failure") }
        catch (actual: java.io.IOException) { assertSame(original, actual) }
        f.returned(); assertEquals(ReadAcquisitionState.FAILED, f.scope.state)
    }
    @Test fun externalCancellationAlreadyTerminalRemainsCancellation() {
        val f = Fixture(); f.scope.cancel(); f.time.set(11)
        f.scope.fail(java.io.IOException()); f.scope.expire()
        failure(ReadAcquisitionFailure.CANCELLED) { f.scope.take() }
        f.returned(); assertEquals(ReadAcquisitionState.CANCELLED, f.scope.state)
    }
    @Test fun successAlreadyDeliveredRemainsSuccessAfterExpiryAndFailure() {
        val f = Fixture(); f.scope.complete(1) { fail("Already delivered") }
        assertEquals(1, f.scope.take()); f.time.set(11); f.scope.expire(); f.scope.fail(java.io.IOException())
        f.returned(); assertEquals(ReadAcquisitionState.SUCCEEDED, f.scope.state)
    }
    @Test fun typedFailureValueBeforeDeadlineKeepsValue() {
        val f = Fixture(); val value = StorageResult.Failure(StorageError.PermissionDenied)
        f.time.set(9); f.scope.complete(value, successful = false) { fail("Already delivered") }
        assertSame(value, f.scope.take()); f.time.set(11); f.scope.expire(); f.returned()
        assertEquals(ReadAcquisitionState.FAILED, f.scope.state)
    }
    @Test fun typedFailureValueAfterDeadlineGetsTimeout() {
        val f = Fixture(); var disposed = 0; f.time.set(11)
        f.scope.complete(StorageResult.Failure(StorageError.PermissionDenied), successful = false) { disposed++ }
        failure(ReadAcquisitionFailure.TIMEOUT) { f.scope.take() }
        f.returned(); assertEquals(1, disposed)
    }
    @Test fun lateExceptionDisposesLeaseExactlyOnceAndRetainsWorkerAdmission() {
        val f = Fixture(); var closed = 0
        val lease = f.scope.own(1, afterWorkerReturns = true) { closed++ }
        f.time.set(11); f.scope.fail(java.io.IOException()); f.drain()
        lease.close(); f.scope.expire(); f.scope.fail(SecurityException())
        assertEquals(0, closed); assertEquals(0, f.releases)
        f.returned(); lease.close(); f.drain()
        assertEquals(1, closed); assertEquals(1, f.releases)
    }
    @Test fun lateExceptionCleanupFailureRetainsAdmission() {
        val f = Fixture(); f.scope.own(1) { throw java.io.IOException("cleanup") }
        f.time.set(11); f.scope.fail(java.io.IOException("provider")); f.returned()
        failure(ReadAcquisitionFailure.TIMEOUT) { f.scope.take() }
        assertEquals(0, f.releases)
    }
    @Test fun lateExceptionBlockedCleanupRetainsAdmissionUntilCleanupReturns() {
        val f = Fixture(); val entered = CountDownLatch(1); val release = CountDownLatch(1)
        f.scope.own(1) { entered.countDown(); release.await(); Unit }
        f.time.set(11); f.scope.fail(java.io.IOException()); f.scope.workerReturned()
        val drainer = Thread { f.drain() }.apply { start() }
        try { assertTrue(entered.await(5, TimeUnit.SECONDS)); assertEquals(0, f.releases) }
        finally { release.countDown(); drainer.join(5000) }
        assertFalse(drainer.isAlive); assertEquals(1, f.releases)
    }
    @Test fun lateExceptionForbidsSubsequentSuccessfulCompletion() {
        val f = Fixture(); var disposed = 0; f.time.set(11); f.scope.fail(java.io.IOException())
        f.scope.complete(1) { disposed++ }
        failure(ReadAcquisitionFailure.TIMEOUT) { f.scope.take() }
        f.returned(); assertEquals(1, disposed)
    }
    @Test fun lateExceptionForbidsSnapshotAdoption() {
        val f = Fixture(); var disposed = 0; var adopted = 0
        val lease = f.scope.own(1) { disposed++ }; f.time.set(11); f.scope.fail(java.io.IOException())
        failure(ReadAcquisitionFailure.TIMEOUT) { lease.adopt { adopted++ } }
        f.returned(); assertEquals(0, adopted); assertEquals(1, disposed)
    }

}
