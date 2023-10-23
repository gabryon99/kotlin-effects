package effects

import kotlin.coroutines.Continuation

interface EffectfulScope<R> : Continuation<R> {
    /**
     * Perform an effect within the current scope of an effectful function.
     */
    suspend fun <T> perform(effect: Effect<T>): T
}

/**
 * Alternative function to perform an effect within a lambda.
 */
suspend inline fun <T> EffectfulScope<*>.perform(effect: () -> Effect<T>): T = perform(effect())