package com.tiskz.app.data

import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import com.tiskz.app.util.PhoneNumbers
import java.text.Normalizer

data class ContactPhone(val number: String, val label: String)

/** Un contacto del teléfono con todos sus números. */
data class PhoneContact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val starred: Boolean,
    val phones: List<ContactPhone>,
) {
    // Calculado una vez al cargar: buscar por cada tecla no vuelve a quitar tildes ni dígitos.
    private val searchName = normalize(name)
    /** Solo los dígitos de cada número, para buscar y sugerir en el marcador. */
    val phoneDigits: List<String> = phones.map { PhoneNumbers.digits(it.number) }

    /** Inicial para agrupar la lista alfabética ("#" si no empieza con letra). */
    val initial: String = searchName.firstOrNull()?.takeIf { it.isLetter() }?.uppercase() ?: "#"

    /** [query] ya normalizada con [normalizeQuery]; [digits] son sus dígitos. */
    fun matches(query: String, digits: String): Boolean {
        if (query.isEmpty()) return true
        if (searchName.contains(query)) return true
        return digits.length >= 3 && phoneDigits.any { it.contains(digits) }
    }

    /** Abre la ficha en la app de contactos del sistema para ver o editar. */
    fun viewIntent(): Intent = Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.getLookupUri(id, lookupKey))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * Contactos guardados en el teléfono. PhoneWard reemplaza a la app de teléfono, así que también
 * tiene que poder buscarlos y llamarlos; crearlos y editarlos se delega a la app de contactos.
 */
object ContactsBook {

    /** Bloqueante: llamar fuera del hilo principal. */
    fun load(context: Context): List<PhoneContact> {
        if (!ContactLookup.hasPermission(context)) return emptyList()
        val projection = arrayOf(
            Phone.CONTACT_ID, Phone.LOOKUP_KEY, Phone.DISPLAY_NAME_PRIMARY, Phone.STARRED,
            Phone.NUMBER, Phone.TYPE, Phone.LABEL,
        )
        val byId = linkedMapOf<Long, PhoneContact>()
        try {
            context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC")
                ?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val number = c.getString(4)?.trim().orEmpty()
                        if (number.isEmpty()) continue
                        val label = Phone.getTypeLabel(context.resources, c.getInt(5), c.getString(6)).toString()
                        val phone = ContactPhone(number, label)
                        val existing = byId[id]
                        if (existing == null) {
                            byId[id] = PhoneContact(
                                id = id,
                                lookupKey = c.getString(1).orEmpty(),
                                name = c.getString(2)?.takeIf { it.isNotBlank() } ?: number,
                                starred = c.getInt(3) == 1,
                                phones = listOf(phone),
                            )
                        } else if (existing.phones.none { PhoneNumbers.matchKey(it.number) == PhoneNumbers.matchKey(number) }) {
                            // Un mismo número suele estar repetido por varias cuentas (Google, SIM, WhatsApp).
                            byId[id] = existing.copy(phones = existing.phones + phone)
                        }
                    }
                }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return byId.values.toList()
    }

    /** Formulario de nuevo contacto de la app del sistema, con el número ya escrito si se indica. */
    fun insertIntent(number: String? = null): Intent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
        .apply { if (!number.isNullOrBlank()) putExtra(ContactsContract.Intents.Insert.PHONE, number) }
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

fun normalizeQuery(text: String): String = normalize(text.trim())

private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .lowercase()
