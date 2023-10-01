# Kotlin Effects
A Kotlin library implementing algebraic effect handlers. This library
is the result of my thesis did at TUM with the collaboration of JetBrains Research.

The library shows the feasibility of implementing algebraic effect handlers within
the Kotlin programming language.

```kotlin
object Read: Effect<String>

@Test
fun `MPretnar - Simple Read Effect`() {
    handle {
        val firstName = perform(Read)
        println("kotlin.Read firstname: $firstName")
        val lastname = perform(Read)
        println("kotlin.Read lastname: $lastname")
        println("Full Name: $firstName $lastname")
    } with { effect ->
        when (effect) {
            is Read -> {
                resume("Bob")
            }
            else -> unhandled()
        }
    }
}
```
