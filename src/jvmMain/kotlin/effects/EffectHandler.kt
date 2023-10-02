package effects

import kotlin.coroutines.Continuation

class UnhandledEffectException: Exception()

interface EffectHandler<R : Any?> : Continuation<R> {

    fun <T> unhandled(): T = throw UnhandledEffectException()

    fun <T> invokeHandler(effect: Effect<T>)


    /**
     * Resume the execution of an effectful function
     * where it was suspended before performing an effect.
     *
     * Notice that `resume()` can be called only once within the `with` block,
     * since Kotlin does not support multi-shot continuations. However, this restriction
     * doesn't apply when the `resume()` function is used after managing different effects.
     *
     * ```kotlin
     * handle<Unit> {
     *      perform(Yield(42)) // <- Pass execution to the `with` block.
     *      // Continue from here after invoking `resume(...)`
     * } with {
     *      resume()
     * }
     */
    suspend fun <T> resume(input: T): R
}

suspend fun <R> EffectHandler<R>.resume(): R = resume(Unit)

