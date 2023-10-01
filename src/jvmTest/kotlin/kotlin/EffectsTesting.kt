package kotlin

import effects.*
import org.junit.jupiter.api.Test

object Next : Effect<String>

object Read : Effect<String>

object Fail : Effect<Unit>

class Print(val msg: String) : Effect<Unit>

sealed class State<R> : Effect<R> {
    internal data class Set(val key: String, val value: String): State<Unit>()
    internal data class Get(val key: String): State<String>()
}

class EffectsTesting {

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

        val answer = handle {
            42
        } with {
            unhandled()
        }

        assert(answer == 42)
    }

    @Test
    fun `Simple Effect`() {

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

    @Test
    fun `MPretnar - Reverse output`() {

        val reverseHandler: EffectHandlerFunction<Unit> = {
            when (it) {
                is Print -> {
                    resume()
                    println(it.msg)
                }
                else -> unhandled()
            }
        }

        handleWith(reverseHandler) {
            perform { Print("A") }
            perform { Print("B") }
            perform { Print("C") }
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
    fun `MPretnar - Collecting Output`() {

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

}