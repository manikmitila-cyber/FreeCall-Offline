package com.meshmobile.contacts

class ContactManager {
    private val contacts = mutableListOf<Contact>()

    fun add(contact: Contact) {
        if (contacts.none { it.userId == contact.userId }) contacts += contact
    }

    fun findByUsername(username: String): Contact? =
        contacts.firstOrNull { it.username.equals(username, ignoreCase = true) }

    fun findByNumber(number: String): Contact? =
        contacts.firstOrNull { it.phoneNumber == number }

    fun all(): List<Contact> = contacts.toList()
}
