package com.omnifile.preview

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Availability is resource-free; the holder owns a response until an atomic consumer transfer. */
internal class OwningResponse<T>(
    private val sessionId: String? = null,
    private val requestId: Long? = null,
    private val dispose: (T) -> Unit,
) {
    enum class State { WAITING, HOLDING, TRANSFERRED, DISPOSED }
    private val lock = Any()
    private val available = CompletableDeferred<Unit>()
    private var ownership = State.WAITING
    private var responseSeen = false
    val hasResponse: Boolean get() = synchronized(lock) { responseSeen }
    private var response: T? = null
    private var failure: Throwable? = null
    val state: State get() = synchronized(lock) { ownership }

    /** Callback initially owns incoming. Rejection keeps ownership here until disposal finishes. */
    fun offer(incoming: T, sessionId: String? = this.sessionId, requestId: Long? = this.requestId): Boolean {
        val accepted = synchronized(lock) {
            if (sessionId == this.sessionId && requestId == this.requestId) responseSeen = true
            if (ownership != State.WAITING || sessionId != this.sessionId || requestId != this.requestId) false
            else { response = incoming; ownership = State.HOLDING; true }
        }
        if (accepted) available.complete(Unit) else dispose(incoming)
        return accepted
    }

    fun fail(error: Throwable, responseObserved: Boolean = false) {
        val owned = synchronized(lock) {
            if (responseObserved) responseSeen = true
            if (ownership == State.TRANSFERRED || ownership == State.DISPOSED) return
            failure = error
            ownership = State.DISPOSED
            response.also { response = null }
        }
        try { owned?.let(dispose) } finally { available.complete(Unit) }
    }

    fun dispose() = fail(CancellationException("Response owner disposed"))

    suspend fun awaitAndTransfer(): T {
        try {
            available.await()
            currentCoroutineContext().ensureActive()
            return synchronized(lock) {
                failure?.let { throw it }
                check(ownership == State.HOLDING)
                @Suppress("UNCHECKED_CAST")
                val owned = response as T
                response = null
                ownership = State.TRANSFERRED
                owned
            }
        } finally {
            // This is harmless after transfer. The consumer now owns its final close.
            dispose()
        }
    }
}
