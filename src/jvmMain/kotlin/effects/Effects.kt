package effects

typealias EffectfulFunction<R> = suspend (EffectfulScopeImpl<R>).() -> R
typealias EffectHandlerFunction<R> = suspend (EffectHandler<R>).(Effect<*>) -> R

interface Effect<R : Any?>

fun <R> handle(effectfulFunction: EffectfulFunction<R>): EffectfulScopeImpl.Builder<R> =
    EffectfulScopeImpl.Builder(effectfulFunction)

infix fun <R> EffectfulScopeImpl.Builder<R>.with(effectHandlerFunction: EffectHandlerFunction<R>): R =
    effectHandlerFunction(effectHandlerFunction).build().runEffectfulScope()

fun <R> handleWithDefault(effectfulFunction: EffectfulFunction<R>): R =
    handleWith({ unhandled() }, effectfulFunction)

fun <R> handleWith(effectHandlerFunction: EffectHandlerFunction<R>, effectfulFunction: EffectfulFunction<R>): R =
    EffectfulScopeImpl.Builder(effectfulFunction, effectHandlerFunction).build().runEffectfulScope()