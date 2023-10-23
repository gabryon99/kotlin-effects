import effects.*
import org.junit.jupiter.api.Test
import kotlin.RuntimeException

object Next : Effect<String>

object Read : Effect<String>

object Fail : Effect<Unit>

data class Print(val msg: String) : Effect<Unit>

sealed class State<R> : Effect<R> {
    internal data class Set(val key: String, val value: String): State<Unit>()
    internal data class Get(val key: String): State<String>()
}

object Empty: Effect<Unit>

class EffectsTesting {

    @Test
    fun `Performing a lot`() {
        // 100 - 27ms/27ms/27ms =avg 27ms
        // 1_000 - 72ms/62ms/72ms =avg 68,6ms
        // 10_000 - 205ms/205ms/196ms =avg 202ms
        // 100_000 - 1962ms/1938ms/1951ms =avg 1950ms
        handle {
            for (i in (0..1_000_000)) {
                perform(Empty)
            }
        } with {
            resume(Unit)
        }
    }

    @Test
    fun `Random Test`() {
        val x = handle {
            perform(Empty) // EH0
            perform(Empty) // EH1
            perform(Empty) // EH2
            0
        } with { effect ->
            when (effect) {
                is Empty -> {
                    resume() + 1
                }
                else -> unhandled()
            }
        }
        assert(x == 3)
    }

    @Test
    fun `Nested Effect Handlers`() {
        handle {
            handle {
                handle {
                    println(perform(Read))
                } with {
                    unhandled()
                }
            } with {
                unhandled()
            }
        } with {
            when (it) {
                is Read -> {
                    resume("🇮🇹")
                }
                else -> unhandled()
            }
        }
    }

    @Test
    fun `No effects`() {
        // 27ms,27ms,18ms
        val answer = handle {
            42
        } with {
            unhandled()
        }

        assert(answer == 42)
    }

    @Test
    fun `Simple Effect`() {
        // 27ms,27ms,18ms
        val result = handle {
            val prefix = perform { Read }
            "$prefix: hello world!"
        } with {
            when (it) {
                is Read -> {
                    resume("[info]")
                }
                else -> unhandled()
            }
        }
        assert(result == "[info]: hello world!")
    }

    @Test
    fun `Abort effectful function`() {
        // 36ms,27ms,
        val result = handle {
            perform(Fail)
            0
        } with {
            when (it) {
                is Fail -> 42
                else -> unhandled()
            }
        }
        assert(result == 42)
    }

    @Test
    fun `MPretnar - Simple Read Effect`() {
        handle {
            val firstName = perform(Read)
            val lastname = perform(Read)
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

    @Test
    fun `MPretnar - Reverse output`() {

        handle {
            perform { Print("A") }
            perform { Print("B") }
            perform { Print("C") }
        } with {
            when (it) {
                is Print -> {
                    resume()
                    println(it.msg)
                }
                else -> unhandled()
            }
        }

    }

    @Test
    fun `The Next effect`() {

        val result = handle {
            val a = perform(Next).toInt()
            println(a)
            a
        } with {
            when (it) {
                is Next -> {
                    resume("42")
                }
                else -> unhandled()
            }
        }

        assert(result == 42)
    }

    @Test
    fun `MPretnar - Collecting Output (Original)`() {

        val pair = handle {
            handle {
                perform { Print("A") }
                perform { Print("B") }
                perform { Print("C") }
            } with {
                when (it) {
                    is Print -> {
                        resume()
                        println(it)
                        unhandled()
                    }
                    else -> unhandled()
                }
            }
            Pair(Unit, "")
        } with {
            when (it) {
                is Print -> {
                    println(it)
                    val (x, acc) = resume(Unit)
                    Pair(x, "${it.msg}$acc")
                }
                else -> unhandled()
            }
        }
        println(pair)
    }

    @Test
    fun `MPretnar - Collecting Output (Tweaked)`() {

        val result = handle {

            handleWithDefault<Pair<Unit, String>> {
                perform { Print("A") }
                perform { Print("B") }
                perform { Print("C") }
                Pair(Unit, "")
            }

        } with { effect ->
            when (effect) {
                is Print -> {
                    val (_, msg) = resume()
                    Pair(Unit, "${effect.msg}${msg}")
                }
                else -> unhandled()
            }
        }

        assert(result.second == "ABC")
    }

    @Test
    fun `State Effect - Print emails`() {
        val emailDatabase = mutableMapOf(
            "bob" to "bob@tum.de",
            "alex" to "alex.12@tum.de",
        )
        handle {
            // kotlin.Read existing emails
            println("Bob's email: ${perform(State.Get("bob"))}")
            println("Alex's email: ${perform(State.Get("alex"))}")
            // Create a new email address
            perform(State.Set("sofia", "sofy.best@tum.de"))
            println("Sofia's email: ${perform(State.Get("sofia"))}")
        } with { effect ->
            when (effect) {
                is State.Get -> resume(emailDatabase[effect.key])
                is State.Set -> {
                    emailDatabase[effect.key] = effect.value
                    resume(Unit)
                }
                else -> unhandled()
            }
        }
    }

    @Test
    fun `Throwing an exception`() {
        handle {
            try {
                println(perform(Empty))
            } catch (e: IllegalStateException) {
                println("Do nothing!")
            } finally {
                println("This will not be printed!")
            }
        } with {
            resume(throw RuntimeException())
        }
    }

}