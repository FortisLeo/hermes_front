package com.rishitgoklani.hermes_front_mobile.domain.bridge

/** Registry: capabilities are looked up by name; adding one never edits existing classes (open/closed). */
class CapabilityRegistry(capabilities: List<Capability> = emptyList()) {
    private val byName = LinkedHashMap<String, Capability>()
    init { capabilities.forEach(::register) }

    fun register(capability: Capability) {
        require(capability.name !in byName) { "Duplicate capability: ${capability.name}" }
        byName[capability.name] = capability
    }

    fun list(): List<Capability> = byName.values.toList()
    fun get(name: String): Capability? = byName[name]
}
