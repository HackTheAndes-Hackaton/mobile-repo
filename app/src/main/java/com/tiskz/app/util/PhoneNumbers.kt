package com.tiskz.app.util

object PhoneNumbers {

    fun digits(number: String): String = number.filter { it.isDigit() }

    /** Últimos 9 dígitos: iguala "+56 9 1234 5678" con "912345678". */
    fun matchKey(number: String): String = digits(number).takeLast(9)

    /** "+56912345678" → "+56 9 1234 5678". Otros formatos se muestran tal cual. */
    fun display(number: String): String {
        if (number.isBlank()) return "Número oculto"
        val d = digits(number)
        return when {
            d.length == 11 && d.startsWith("569") -> "+56 9 ${d.substring(3, 7)} ${d.substring(7)}"
            d.length == 9 && d.startsWith("9") -> "+56 9 ${d.substring(1, 5)} ${d.substring(5)}"
            else -> number
        }
    }
}
