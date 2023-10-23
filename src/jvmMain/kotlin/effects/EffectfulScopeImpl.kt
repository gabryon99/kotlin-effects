package effects

import java.util.Stack
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.*

private const val DEFAULT_FORWARD_INDEX = -1

class EffectfulScopeImpl<R> private constructor(
    private val effectfulFunction: EffectfulFunction<R>,
    private val effectHandlerFunction: EffectHandlerFunction<R>
) : EffectfulScope<R> {

    data class Builder<R>(
        var effectfulFunction: EffectfulFunction<R>? = null,
        var effectHandlerFunction: EffectHandlerFunction<R>? = null
    ) {
        fun effectfulFunction(effectfulFunction: EffectfulFunction<R>) = apply {
            this.effectfulFunction = effectfulFunction
        }

        fun effectHandlerFunction(effectHandlerFunction: EffectHandlerFunction<R>) = apply {
            this.effectHandlerFunction = effectHandlerFunction
        }

        fun build() = EffectfulScopeImpl(effectfulFunction!!, effectHandlerFunction!!)
    }

    enum class EffectfulFunctionStatus {
        INITIAL,
        PERFORMED_EFFECT,
        FORWARDED,
        ABORTED,
        COMPUTED;
    }

    companion object {
        val EFFECTFUL_SCOPES: MutableList<EffectfulScopeImpl<*>> = mutableListOf()

        private val _currentID = AtomicInteger(0)
        fun generateNewID(): Int = _currentID.getAndIncrement()
    }

    /* Used for debugging purposes. */
    private val effectfulFunctionID = generateNewID()
    override val context: CoroutineContext
        get() = CoroutineName("EffectfulFunction$${effectfulFunctionID}")

    internal var effectfulFunctionStatus = EffectfulFunctionStatus.INITIAL

    private var effectToBeHandled: Effect<*>? = null

    private var computedResult: Result<R> = Result.failure(NoSuchElementException())

    /***
     * This continuation represents the "rest" of the effectful function
     * to continue.
     */
    private var effectfulFunctionContinuation: Continuation<Any?>? = null

    /**
     * Effect handlers call-stack. Each time a new effect handler is invoked
     * within the current scope, it will be added to the stack.
     */
    private var effectHandlerStack: Stack<EffectHandler<R>> = Stack()

    private var forwardIndex: Int = DEFAULT_FORWARD_INDEX

    override suspend fun <T> perform(effect: Effect<T>): T {
        effectfulFunctionStatus = EffectfulFunctionStatus.PERFORMED_EFFECT
        effectToBeHandled = effect
        return suspendCoroutine {
            // Suspend the effectful function and invoke the handler.
            effectfulFunctionContinuation = it as Continuation<Any?>
        }
    }

    private fun invokeEffectHandler(effectHandlerFunction: EffectHandlerFunction<R>) {

        assert(effectfulFunctionStatus == EffectfulFunctionStatus.PERFORMED_EFFECT ||
                effectfulFunctionStatus == EffectfulFunctionStatus.FORWARDED)
        assert(effectToBeHandled != null)
        assert(effectfulFunctionContinuation != null)

        val effect = effectToBeHandled!!
        // Create a new effect handler within a coroutine
        val newEffectHandler = EffectHandlerImpl(
            this,
            effectfulFunctionContinuation!!,
            effectHandlerFunction,
        )

        // Add a new effect handler on top of the stack
        registerEffectHandler(newEffectHandler)
        // Invoke handler
        newEffectHandler.invokeHandler(effect)
    }

    private fun registerEffectHandler(effectHandler: EffectHandler<R>) {
        effectHandlerStack.push(effectHandler)
    }

    internal fun unregisterEffectHandler(effectHandler: EffectHandler<R>, result: Result<R>? = null) {
        effectHandlerStack.remove(effectHandler)
        result?.let {
            computedResult = it
        }
    }

    internal fun unwrapResult(): Result<R> {
        assert(effectfulFunctionStatus == EffectfulFunctionStatus.COMPUTED || effectfulFunctionStatus == EffectfulFunctionStatus.ABORTED)
        return computedResult
    }

    internal fun abortComputation(result: Result<R>) {
        computedResult = result
        effectfulFunctionStatus = EffectfulFunctionStatus.ABORTED
    }

    internal fun resetForwardIndex() {
        forwardIndex = DEFAULT_FORWARD_INDEX
    }

    internal fun forwardToParent() {
        effectfulFunctionStatus = EffectfulFunctionStatus.FORWARDED
        forwardIndex -= 1
    }

    override fun resumeWith(result: Result<R>) {
        computedResult = result
        effectfulFunctionStatus = EffectfulFunctionStatus.COMPUTED
    }

    fun runEffectfulScope(): R {

        if (this === EFFECTFUL_SCOPES.firstOrNull()) {
            throw IllegalStateException("An handler was already register within this scope.")
        }

        EFFECTFUL_SCOPES.add(this)

        while (true) {
            when {
                effectfulFunctionStatus == EffectfulFunctionStatus.INITIAL -> {
                    val effectfulFunctionCoroutine = effectfulFunction.createCoroutine(this, this)
                    effectfulFunctionCoroutine.resume(Unit)
                }

                effectfulFunctionStatus == EffectfulFunctionStatus.PERFORMED_EFFECT -> {
                    invokeEffectHandler(effectHandlerFunction)
                }

                effectfulFunctionStatus == EffectfulFunctionStatus.FORWARDED -> {
                    val scopeIdx = EFFECTFUL_SCOPES.size + forwardIndex
                    if (scopeIdx < 0) {
                        throw RuntimeException("Top scope for effect handlers has been reached.")
                    }
                    invokeEffectHandler(EFFECTFUL_SCOPES[scopeIdx].effectHandlerFunction as EffectHandlerFunction<R>)
                }

                effectfulFunctionStatus == EffectfulFunctionStatus.ABORTED -> {
                    EFFECTFUL_SCOPES.remove(this)
                    return unwrapResult().getOrThrow()
                }

                effectfulFunctionStatus == EffectfulFunctionStatus.COMPUTED && effectHandlerStack.empty() -> {
                    EFFECTFUL_SCOPES.remove(this)
                    return unwrapResult().getOrThrow()
                }

                effectfulFunctionStatus == EffectfulFunctionStatus.COMPUTED && !effectHandlerStack.empty() -> {
                    // If the effect handler stack is not empty,
                    // it means there are some suspended effect handlers that are to be resumed.
                    var result = unwrapResult().getOrThrow() // <--
                    while (!effectHandlerStack.empty()) {
                        with(effectHandlerStack.pop() as EffectHandlerImpl) {
                            continueEffectHandlerExecution(result)
                            result = unwrapResult().getOrThrow()
                        }
                    }
                    return result
                }
            }
        }
    }
}
