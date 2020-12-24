package ru.falseteam.rsub

sealed class RSubException(message: String) : Exception(message) {
    class DuplicateInterfaceName(name: String) : RSubException("Duplicate interface name: $name")
    class NoInterfaceAnnotation(name: String) :
        RSubException("Class $name passed as interface but doesnt have @RSubInterface annotation")
}
