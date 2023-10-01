package effects

typealias EffectfulFunction<R> = suspend (EffectfulScope<R>).() -> R
typealias EffectHandlerFunction<R> = suspend (EffectHandler<R>).(Effect<*>) -> R

interface Effect<R : Any?>

fun <R> handle(effectfulFunction: EffectfulFunction<R>): EffectfulScope.Builder<R> =
    EffectfulScope.Builder(effectfulFunction)

infix fun <R> EffectfulScope.Builder<R>.with(effectHandlerFunction: EffectHandlerFunction<R>): R =
    effectHandlerFunction(effectHandlerFunction).build().runEffectfulScope()

fun <R> handleWithDefault(effectfulFunction: EffectfulFunction<R>): R =
    handleWith({ unhandled() }, effectfulFunction)

fun <R> handleWith(effectHandlerFunction: EffectHandlerFunction<R>, effectfulFunction: EffectfulFunction<R>): R =
    EffectfulScope.Builder(effectfulFunction, effectHandlerFunction).build().runEffectfulScope()