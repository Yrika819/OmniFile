package com.omnifile.preview

import com.omnifile.storage.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** Gates control the physical return independently of the cancelled logical owner. */
class PreviewOwnershipTest {
    @Test fun sessionInstallRaceKeepsBAndDisposesLateA() = runBlocking { Fixture().use { f ->
        val a = f.pendingOpen("A"); f.open("A"); f.open("B"); val b = f.current()
        a.complete(Unit)
        assertSame(b, f.current()); assertEquals(1, f.sessions.getValue("A").invalidations)
        f.vm.nextPage(); assertEquals(1, f.pageIndex()); f.vm.close(); assertEquals(1, b.invalidations)
    } }
    @Test fun oldPublicationAfterBackKeepsIdle() = runBlocking { Fixture().use { f ->
        val a = f.pendingOpen("A"); f.open("A"); f.vm.close(); a.complete(Unit)
        assertEquals(PreviewUiState.Idle, f.vm.state.value); assertEquals(1, f.sessions.getValue("A").invalidations)
    } }
    @Test fun oldDrainFinallyCannotClearNewDrainOrQueuedIntent() = runBlocking { Fixture().use { f ->
        f.open("A"); val a = f.current(); val releaseA = a.holdNext(); f.vm.nextPage()
        f.open("B"); val b = f.current(); val releaseB = b.holdNext(); f.vm.nextPage(); f.vm.nextPage()
        releaseA.complete(Unit); assertTrue(f.vm.state.value is PreviewUiState.Loading)
        releaseB.complete(Unit); assertSame(b, f.current()); assertEquals(2, f.pageIndex())
        assertEquals(listOf(1, 2), b.renders); assertEquals(1, a.invalidations)
    } }
    @Test fun retryAToBDisposesOldGeneration() = runBlocking { Fixture().use { f ->
        f.open("A"); val old = f.current(); f.vm.retry()
        assertNotSame(old, f.current()); assertEquals(1, old.invalidations); assertEquals(2, f.created.size)
    } }
    @Test fun replacementABCDisposesBothLateCandidates() = runBlocking { Fixture().use { f ->
        val a = f.pendingOpen("A"); val b = f.pendingOpen("B")
        f.open("A"); f.open("B"); f.open("C"); val c = f.current()
        b.complete(Unit); a.complete(Unit); assertSame(c, f.current())
        assertEquals(1, f.sessions.getValue("A").invalidations); assertEquals(1, f.sessions.getValue("B").invalidations)
    } }
    @Test fun backDuringRenderCannotPublishOldPage() = runBlocking { Fixture().use { f ->
        f.open("A"); val a = f.current(); val release = a.holdNext(); f.vm.nextPage(); f.vm.close()
        release.complete(Unit); assertEquals(PreviewUiState.Idle, f.vm.state.value); assertEquals(1, a.invalidations)
    } }
    @Test fun recreationAtCompletionKeepsNewOwner() = runBlocking {
        Fixture().use { old -> Fixture().use { recreated ->
            val a = old.pendingOpen("A"); old.open("A"); old.vm.close(); recreated.open("A")
            val current = recreated.current(); a.complete(Unit)
            assertEquals(PreviewUiState.Idle, old.vm.state.value); assertSame(current, recreated.current())
        } }
    }
    @Test fun oldWorkerDeathCannotCloseReplacement() = runBlocking { Fixture().use { f ->
        f.open("A"); val a = f.current(); val release = a.holdNext(); a.failure = PdfRendererFailure(PdfRendererFailureKind.WORKER_DIED)
        f.vm.nextPage(); f.open("B"); val b = f.current(); release.complete(Unit)
        assertSame(b, f.current()); assertEquals(0, b.invalidations); f.vm.nextPage(); assertEquals(1, f.pageIndex())
    } }
    @Test fun pageIntentDuringSessionReplacementBelongsToInstalledOwner() = runBlocking { Fixture().use { f ->
        f.open("A"); val release = f.pendingOpen("B"); f.open("B"); f.vm.nextPage()
        release.complete(Unit); assertEquals(0, f.pageIndex()); f.vm.nextPage(); assertEquals(1, f.pageIndex())
    } }
    @Test fun lateCleanupAfterBackCannotClearReopenedOwner() = runBlocking { Fixture().use { f ->
        f.open("A"); val a = f.current(); val release = a.holdNext(); f.vm.nextPage(); f.vm.close(); f.open("B")
        val b = f.current(); release.complete(Unit); assertSame(b, f.current()); f.vm.close(); assertEquals(1, b.invalidations)
    } }
    @Test fun currentWorkerDeathPublishesErrorAndRetryRemainsNavigable() = runBlocking { Fixture().use { f ->
        f.open("A"); f.current().failure = PdfRendererFailure(PdfRendererFailureKind.WORKER_DIED)
        f.vm.nextPage(); assertTrue(f.vm.state.value is PreviewUiState.Error); f.vm.retry()
        f.vm.nextPage(); assertEquals(1, f.pageIndex())
    } }
    @Test fun failedOldAcquisitionCannotReplaceCurrentSuccess() = runBlocking { Fixture().use { f ->
        val release = f.pendingOpen("A"); f.failOpen = "A"; f.open("A"); f.open("B")
        val b = f.current(); release.complete(Unit); assertSame(b, f.current())
    } }
    @Test fun latestPageIntentWinsAcrossDirectionChanges() = runBlocking { Fixture().use { f ->
        f.open("A"); val release = f.current().holdNext(); f.vm.nextPage(); f.vm.nextPage(); f.vm.previousPage()
        release.complete(Unit); assertEquals(1, f.pageIndex())
    } }
    @Test fun fiveHundredVariedGenerationAndDrainInterleavings() = runBlocking {
        repeat(500) { iteration -> Fixture().use { f ->
            if (iteration % 2 == 0) {
                val release = f.pendingOpen("A"); f.open("A")
                if (iteration % 4 == 0) f.vm.close()
                f.open("B"); val b = f.current(); release.complete(Unit)
                assertSame(b, f.current()); assertEquals(1, f.sessions.getValue("A").invalidations)
            } else {
                f.open("A"); val release = f.current().holdNext(); f.vm.nextPage()
                f.open("B"); val b = f.current(); val releaseB = b.holdNext(); f.vm.nextPage()
                if (iteration % 3 == 0) f.vm.nextPage()
                release.complete(Unit); releaseB.complete(Unit); assertSame(b, f.current())
                assertEquals(if (iteration % 3 == 0) 2 else 1, f.pageIndex())
            }
            val b = f.current(); f.vm.close(); assertEquals(1, b.invalidations)
            assertTrue(f.created.all { it.invalidations == 1 })
        } }
    }

    private class Fixture : AutoCloseable {
        val gates = mutableMapOf<String, CompletableDeferred<Unit>>()
        val sessions = mutableMapOf<String, Session>()
        val created = mutableListOf<Session>()
        var failOpen: String? = null
        private val lifecycle = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val vm = PreviewViewModel(object : PreviewEngine() {
            override suspend fun resolveAndLoad(item: PreviewItem, resolveSource: suspend () -> StorageResult<PreviewSource>, startingPage: Int): ResolvedPreview {
                gates.remove(item.displayName)?.let { withContext(NonCancellable) { it.await() } }
                if (failOpen == item.displayName) throw java.io.IOException("fake acquisition failure")
                val session = Session().also { sessions[item.displayName] = it; created += it }
                return ResolvedPreview("Test", PreviewPayload.PdfPage(page(), startingPage, 4, session))
            }
        }, Dispatchers.Unconfined, lifecycle)
        fun pendingOpen(name: String) = CompletableDeferred<Unit>().also { gates[name] = it }
        fun open(name: String) { vm.open(PreviewItem(Ref(name), name, "Test", "application/pdf", 1)) { StorageResult.Failure(StorageError.Unsupported) } }
        fun current() = ((vm.state.value as PreviewUiState.Ready).payload as PreviewPayload.PdfPage).session as Session
        fun pageIndex() = ((vm.state.value as PreviewUiState.Ready).payload as PreviewPayload.PdfPage).pageIndex
        override fun close() { vm.close(); lifecycle.cancel() }
    }
    private class Session : PdfDocumentSession {
        override val pageCount = 4
        var invalidations = 0
        val renders = mutableListOf<Int>()
        var gate: CompletableDeferred<Unit>? = null
        var failure: PdfRendererFailure? = null
        fun holdNext() = CompletableDeferred<Unit>().also { gate = it }
        override suspend fun renderPage(pageIndex: Int): PdfRenderedPage {
            renders += pageIndex
            gate?.also { gate = null }?.let { withContext(NonCancellable) { it.await() } }
            failure?.let { throw it }
            return page()
        }
        override fun invalidate() { invalidations++ }
        override suspend fun close() { invalidate() }
    }
    private data class Ref(override val identityKey: String) : EntryRef { override val providerId = ProviderId("ownership-test") }
    companion object { private fun page() = PdfRenderedPage(2, 2, ByteArray(16)) }
}
