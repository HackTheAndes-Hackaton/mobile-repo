package com.tiskz.app.util

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

object PhoneNumbers {

    /** País de la SIM (ej. "PE", "CL"). Define cómo se formatean los números sin prefijo internacional. */
    private var countryIso: String = Locale.getDefault().country

    fun init(context: Context) {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val iso = telephony?.simCountryIso?.takeIf { it.isNotBlank() }
            ?: telephony?.networkCountryIso?.takeIf { it.isNotBlank() }
        if (iso != null) countryIso = iso.uppercase(Locale.ROOT)
    }

    fun digits(number: String): String = number.filter { it.isDigit() }

    /** Últimos 9 dígitos: iguala "+51 987 654 321" con "987654321". */
    fun matchKey(number: String): String = digits(number).takeLast(9)

    fun display(number: String): String {
        if (number.isBlank()) return "Número oculto"
        return PhoneNumberUtils.formatNumber(number, countryIso) ?: number
    }
}
