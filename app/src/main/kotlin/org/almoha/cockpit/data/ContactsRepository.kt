package org.almoha.cockpit.data

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactFav(
    val id: Long,
    val name: String,
    val number: String,
    val photoUri: String?,
)

class ContactsRepository(private val context: Context) {

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.READ_CONTACTS,
    ) == PackageManager.PERMISSION_GRANTED

    suspend fun loadFavorites(limit: Int = 2): List<ContactFav> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val resolver = context.contentResolver
        val wantedNames = listOf("Patrick", "Parents portable")
        val found = LinkedHashMap<String, ContactFav>()

        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ),
            null,
            null,
            "${ContactsContract.CommonDataKinds.Phone.STARRED} DESC, ${ContactsContract.CommonDataKinds.Phone.LAST_TIME_CONTACTED} DESC",
        )
        cursor?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1)?.trim().orEmpty()
                val number = c.getString(2)?.trim().orEmpty()
                if (number.isBlank()) continue
                val matchedKey = wantedNames.firstOrNull { it.equals(name, ignoreCase = true) } ?: continue
                if (matchedKey.lowercase() in found) continue
                val photo = c.getString(3)
                found[matchedKey.lowercase()] = ContactFav(id, matchedKey, number, photo)
                if (found.size == wantedNames.size) break
            }
        }

        wantedNames.mapNotNull { found[it.lowercase()] }.take(limit)
    }

    private fun firstNumber(
        resolver: android.content.ContentResolver,
        contactId: Long,
    ): String? {
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID}=?",
            arrayOf(contactId.toString()),
            null,
        )
        cursor?.use {
            if (it.moveToFirst()) return it.getString(0)?.trim()
        }
        return null
    }

    fun photo(uri: String?): Uri? = uri?.let { Uri.parse(it) }
}
