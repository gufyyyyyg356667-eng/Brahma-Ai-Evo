package com.brahma.connect.voice

sealed class ContactMatch {
    data class Found(val name: String, val number: String) : ContactMatch()
    data class Ambiguous(val names: List<String>) : ContactMatch()
    object NotFound : ContactMatch()
    object PermissionDenied : ContactMatch()
}

/** Resolves a spoken contact name to a phone number. */
interface ContactLookup {
    fun find(name: String): ContactMatch
}
