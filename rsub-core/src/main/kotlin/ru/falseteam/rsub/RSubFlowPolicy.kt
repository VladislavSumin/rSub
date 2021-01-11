package ru.falseteam.rsub

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RSubFlowPolicy(val policy: Policy) {
    enum class Policy {
        THROW_EXCEPTION,
        SUPPRESS_EXCEPTION_AND_RECONNECT,
    }
}

