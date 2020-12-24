package ru.falseteam.rsub

import ru.falseteam.rsub.annotation.RSubFunction
import ru.falseteam.rsub.annotation.RSubInterface
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation

class RSub(
    inputInterfaces: List<Any>
) {
    private val iInput = mutableMapOf<String, InputInterface>()

    init {
        processInput(inputInterfaces)
    }

    inline fun <reified T> getInterface() = getInterface(T::class)

    fun getInterface(kClass: KClass<*>) {

    }

    private fun processInput(inputInterfaces: List<Any>) {
        inputInterfaces.forEach { instance ->
            val kClass = instance.javaClass.kotlin
            val name = kClass.simpleName!!
            if (!kClass.hasAnnotation<RSubInterface>()) throw RSubException.NoInterfaceAnnotation(name)
            if (iInput.containsKey(name)) throw RSubException.DuplicateInterfaceName(name)

            val functions = processInterface(kClass)
            iInput[name] = InputInterface(instance, functions)
        }
    }

    private fun processInterface(kClass: KClass<*>): Map<String, InputFunction> {
        return kClass.functions.asSequence()
            .filter { it.hasAnnotation<RSubFunction>() }
            .map { it.name to processFunction(it) }
            .toMap()
    }

    private fun processFunction(function: KFunction<*>): InputFunction {
        println(function.name)
        return InputFunction()
    }

    private data class InputInterface(
        val instance: Any,
        val functions: Map<String, InputFunction>,
    )

    private class InputFunction()
}
