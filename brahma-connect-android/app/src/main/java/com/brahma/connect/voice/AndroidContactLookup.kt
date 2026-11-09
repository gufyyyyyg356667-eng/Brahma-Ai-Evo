package com.brahma.connect.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Looks up contacts by name using the READ_CONTACTS permission.
 * Nothing is cached or sent anywhere; each lookup reads the phone's contacts directly.
 */
class AndroidContactLookup(private val context: Context) : ContactLookup {

    override fun find(name: String): ContactMatch {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return ContactMatch.PermissionDenied

        val numberToName = linkedMapOf<String, String>()
        val cursor = try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null,
            )
        } catch (security: SecurityException) {
            return ContactMatch.PermissionDenied
        } ?: return ContactMatch.NotFound

        cursor.use { c ->
            while (c.moveToNext()) {
                val displayName = c.getString(0)
                val number = c.getString(1)
                if (!displayName.isNullOrBlank() && !number.isNullOrBlank()) {
                    numberToName.putIfAbsent(number, displayName)
                }
            }
        }
        if (numberToName.isEmpty()) return ContactMatch.NotFound

        // Prefer exact name matches when several contacts contain the spoken word.
        val exact = numberToName.filterValues { it.equals(name, ignoreCase = true) }
        val candidates = if (exact.isNotEmpty()) exact else numberToName

        val distinctNumbers = candidates.keys.map { it.filter { ch -> ch.isDigit() || ch == '+' } }.distinct()
        return if (distinctNumbers.size == 1) {
            val (number, displayName) = candidates.entries.first()
            ContactMatch.Found(displayName, number)
        } else {
            ContactMatch.Ambiguous(candidates.values.distinct())
        }
    }
}
