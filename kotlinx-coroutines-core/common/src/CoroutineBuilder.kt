/*
 * Copyright 2016-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.coroutines

import kotlin.jvm.*

/**
 * A builder object for coroutine of type [C] with body block working in scope [S] and returning [T].
 * It provides an efficient DSL to attach [catch] and [finally] blocks to the coroutine before it is [build].
 * 
 * Each call of [catch] and [finally] wraps the body of the coroutine in the corresponding exception handling
 * logic and returns a new instance of `CoroutineBuilder`. An instance of coroutine builder itself
 * is immutable and encapsulates all the parameters of the corresponding builder function call.
 * A call to [build] calls the coroutine builder. 
 */
public abstract class CoroutineBuilder<C, S, T>(
    /**
     * @suppress It is not supposed to be extended outside of this module.
     */
    @JvmField
    @InternalCoroutinesApi
    protected val currentBlock: suspend S.() -> T
) {
    internal abstract fun updateBlock(block: suspend S.() -> T): CoroutineBuilder<C, S, T>

    /**
     * Runs currentBlock and awaits for all its children to complete.
     * As a side effect, it also makes current state machine complete.
     * We know, that we are not going to use current job in any of the catch/finally section,
     * because all suspending invocations are protected [withNonCancellableContext].
     */
    private suspend inline fun S.awaitCurrentBlockCompletion(): Any? {
        val proposedUpdate = try {
            currentBlock()
        } catch (e: Exception) {
            CompletedExceptionally(e)
        }
        return awaitFinalStateCompletion(proposedUpdate)
    }

    @PublishedApi
    internal fun catchIf(
        predicate: (Throwable) -> Boolean,
        handler: suspend CoroutineScope.(cause: Throwable) -> T
    ): CoroutineBuilder<C, S, T> =
        updateBlock {
            val state = awaitCurrentBlockCompletion()
            when {
                state !is CompletedExceptionally -> @Suppress("UNCHECKED_CAST") (state as T)
                predicate(state.cause) -> withNonCancellableContext(state.cause, handler)
                else -> throw state.cause
            }
        }

    /**
     * Wraps the coroutine code block of this builder with the `try/catch` block for exceptions of type [E],
     * executing the specified [handler] code when exception is caught with the exception as parameter.
     *
     * The resulting code is semantically equivalent to the following, albeit the
     * actual implementation is significantly more efficient and uses internal mechanisms to
     * catch exceptions of all the children coroutines in the code:
     *
     * ```
     * try {
     *     // coroutineScope to catch exceptions of all children coroutines, too
     *     coroutineScope {
     *         originalCodeBlock()
     *     }
     * } catch(cause: E) {
     *     // withContext to execute handler even when this coroutine is cancelled
     *     withContext(NonCancellable) {
     *         handler(cause)
     *     }
     * }
     * ```
     */
    public inline fun <reified E : Throwable> catch(
        noinline handler: suspend CoroutineScope.(cause: E) -> T
    ): CoroutineBuilder<C, S, T> =
        catchIf(
            predicate = { it is E },
            handler = @Suppress("UNCHECKED_CAST") (handler as suspend CoroutineScope.(cause: Throwable) -> T)
        )

    /**
     * Wraps the coroutine code block of this builder with the `try/finally` block,
     * executing the specified [handler] code with the failure exception as parameter.
     *
     * The resulting code is semantically equivalent to the following, albeit the
     * actual implementation is significantly more efficient and uses internal mechanisms to
     * catch exceptions of all the children coroutines in the code:
     *
     * ```
     * var cause: Throwable? = null
     * try {
     *     // coroutineScope to catch exceptions of all children coroutines, too
     *     coroutineScope {
     *         originalCodeBlock()
     *     }
     * } catch(e: Throwable) {
     *     cause = e // remember what exception was the cause
     * } finally {
     *     // withContext to execute handler even when this coroutine is cancelled
     *     withContext(NonCancellable) {
     *         handler(cause)
     *     }
     *     cause?.let { throw it } // rethrow original exception (if it was caught)
     * }
     * ```
     */
    public fun finally(
        handler: suspend CoroutineScope.(cause: Throwable?) -> Unit
    ): CoroutineBuilder<C, S, T> =
        updateBlock {
            val state = awaitCurrentBlockCompletion()
            val cause = (state as? CompletedExceptionally)?.cause
            withNonCancellableContext(cause, handler)
            cause?.let { throw it }
            @Suppress("UNCHECKED_CAST")
            state as T
        }

    /**
     * Calls the coroutine builder and return the resulting instance of coroutine.
     */
    public abstract fun build(): C
}

