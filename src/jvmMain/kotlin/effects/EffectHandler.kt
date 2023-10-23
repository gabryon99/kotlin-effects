package effects

import kotlin.coroutines.Continuation

class UnhandledEffectException: RuntimeException()

interface EffectHandlerScope<R> : Continuation<R> {

    fun <T> unhandled(): T = throw UnhandledEffectException()

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

suspend fun <R> EffectHandlerScope<R>.resume(): R = resume(Unit)

interface EffectHandler<R> : EffectHandlerScope<R> {
    /**
     * Invoke the effect handler passing the effect as parameter.
     */
    fun <T> invokeHandler(effect: Effect<T>)
}
