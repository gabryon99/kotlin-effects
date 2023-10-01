package effects

class UnhandledEffectException : Exception()

/**
 * Special marker used to alert an unhandled effect.
 */
fun <R> unhandled(): R = throw UnhandledEffectException()