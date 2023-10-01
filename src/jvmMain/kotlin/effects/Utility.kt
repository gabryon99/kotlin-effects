package effects

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

data class CoroutineName(val name: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<CoroutineName>

    override fun toString(): String = name
}

val <T> Continuation<T>.name get() = this.context[CoroutineName.Key] ?: "Unnamed"