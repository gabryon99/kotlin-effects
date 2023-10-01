# Kotlin Effects
A Kotlin library implementing **algebraic effect handlers**. This library
is the result of my thesis did at the **Technical University of Munich** with the collaboration of **JetBrains Research**.

The library shows the feasibility of implementing algebraic effect handlers within
the Kotlin programming language. Here is a brief list containing library's main points:

* Effects are defined with classes/objects inhereting from the `Effect` interface.
* Effectful computations are invoked within a `handle e with h` expression, where `e` is an effectful function and `h` is an effect handler.
* The library **does not support** multishot continuations.
* The provided handler semantics is the **deep** one.
* Effects are performed using the `do/perform` notation (maybe not the best Kotlin idiomatic choice).

```kotlin
object Read: Effect<String>

@Test
fun `MPretnar - Simple Read Effect`() {
    handle {
        val firstName = perform(Read)
        val lastname = perform(Read)
        println("Full Name: $firstName $lastname")
    } with { effect ->
        when (effect) {
            is Read -> resume("Bob")
            else -> unhandled()
        }
    }
}
```

Effect handlers are implemented on top of Kotlin's coroutines system, using linear continuations.
