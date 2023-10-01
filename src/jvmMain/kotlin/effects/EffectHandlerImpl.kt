package effects

import effects.exceptions.AlreadyResumedException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.*

class EffectHandlerImpl<R>(
    private val effectfulScope: EffectfulScope<R>,
    private val effectfulFunContinuation: Continuation<Any?>,
    private val effectHandlerFunction: EffectHandlerFunction<R>,
) : EffectHandler<R> {

    companion object {
        private val _currentID = AtomicInteger(0)
        fun generateNewID(): Int = _currentID.getAndIncrement()
    }

    enum class EffectHandlerStatus {
        INITIAL,
        HANDLING,
        SUSPENDED,
        RESUMED,
        DONE;
    }

    private val effectHandlerId = generateNewID()
    override val context: CoroutineContext
        get() = CoroutineName("EffectHandler$${effectHandlerId}")

    private var handlerContinuation: Continuation<R>? = null

    private var effectHandlerResult: Result<R> = Result.failure(NoSuchElementException())

    private var status = EffectHandlerStatus.INITIAL

    override fun <T> invokeHandler(effect: Effect<T>) {
        // The effect handler is in execution
        status = EffectHandlerStatus.HANDLING
        val effLambda: suspend () -> R = { effectHandlerFunction.invoke(this, effect) }
        val effectHandlerCoroutine = effLambda.createCoroutine(this)
        effectHandlerCoroutine.resume(Unit)
    }

    override suspend fun <T> resume(input: T): R {

        if (status == EffectHandlerStatus.RESUMED) {
            throw AlreadyResumedException("Continuations are linear, therefore you can resume them at least once.")
        }

        status = EffectHandlerStatus.RESUMED

        // Resume the effectful function computation
        // where it was stopped.
        effectfulScope.resetForwardIndex()
        effectfulFunContinuation.resume(input)

        // What is the current state of the effectful function associated with the handler?
        // If the effectful function has been done computing, it means that a result is ready
        // to be fetched. Otherwise, if an effect has been performed, we have to suspend
        // the current effect handler and wait for the whole effect runtime.

        val returnedValue = when (effectfulScope.effectfulFunctionStatus) {
            EffectfulScope.EffectfulFunctionStatus.PERFORMED_EFFECT -> {
                suspendCoroutine {
                    status = EffectHandlerStatus.SUSPENDED
                    handlerContinuation = it
                }
            }
            EffectfulScope.EffectfulFunctionStatus.COMPUTED -> effectfulScope.unwrapResult().getOrThrow()
            else -> TODO("Unreachable")
        }

        // If the previous state was suspended then it means
        // the runtime invoked the continuation of the current effect handler. We switch back
        // to the resumed state.
        if (status == EffectHandlerStatus.SUSPENDED) {
            status = EffectHandlerStatus.RESUMED
        }

        return returnedValue
    }

    /**
     * Continue the execution of an effect handler where it was suspended.
     */
    fun continueEffectHandlerExecution(value: R) {
        assert(status == EffectHandlerStatus.SUSPENDED)
        assert(handlerContinuation != null)
        handlerContinuation!!.resume(value)
    }

    override fun resumeWith(result: Result<R>) {

        effectHandlerResult = result.onSuccess {
            // The effect handler computation ended without resumption.
            // We abort the execution of the effectful function.

            when (status) {
                EffectHandlerStatus.RESUMED -> {
                    effectfulScope.unregisterEffectHandler(this, result)
                }
                EffectHandlerStatus.HANDLING -> {
                    effectfulScope.abortComputation(result)
                }
                else -> TODO("Unreachable")
            }

            status = EffectHandlerStatus.DONE
        }.onFailure {

            if (it is UnhandledEffectException) {
                // We should invoke the top effect handler...
                effectfulScope.unregisterEffectHandler(this)
                effectfulScope.forwardToParent()
            }

        }
    }

    internal fun unwrapResult(): Result<R> {
        assert(status == EffectHandlerStatus.DONE)
        return effectHandlerResult
    }
}