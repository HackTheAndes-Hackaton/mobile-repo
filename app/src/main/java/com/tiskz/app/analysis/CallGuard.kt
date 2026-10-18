package com.tiskz.app.analysis

import java.text.Normalizer

/** Peligro concreto detectado en lo que dice quien llama, con el consejo que se muestra al titular. */
enum class GuardKind(val title: String, val advice: String) {
    OTP("No compartas códigos", "Ningún banco ni empresa te pide por teléfono el código que te llegó por SMS."),
    PASSWORD("No digas tu clave", "Tu clave, PIN o CVV son solo tuyos. Nadie legítimo los pide por teléfono."),
    CARD("No dictes tu tarjeta", "No des el número, la fecha ni los datos de tu tarjeta en una llamada."),
    MONEY("No envíes dinero", "Si te piden Yapear, Plinear o transferir, cuelga y verifica por otro medio."),
    REMOTE("No instales nada", "Te piden instalar una app o dar acceso al celular: así toman control de tus cuentas."),
    FAMILY("Verifica con tu familiar", "Cuelga y llama tú directamente a tu familiar antes de hacer cualquier cosa."),
    PRIZE("Los premios no se cobran", "Un premio real no te pide pagar ni dar datos por teléfono."),
}

data class GuardAlert(val kind: GuardKind, val quote: String, val lineId: String)

/**
 * Detector local de pedidos peligrosos (patrones frecuentes en Perú). Funciona sin backend:
 * protege también después de que el titular entra a la llamada, que es cuando ocurre el robo.
 */
object CallGuard {

    private val rules: List<Pair<GuardKind, Regex>> = listOf(
        GuardKind.OTP to Regex("""\b(codigo|token|clave dinamica)\b.*\b(llego|llega|sms|mensaje|verificacion|validacion|digitos|dicte|dictar|dime|digame|pasame|paseme|confirme)\b|\b(digame|dime|dicte|pasame|paseme|confirme)\b.*\bcodigo\b"""),
        GuardKind.PASSWORD to Regex("""\b(clave|contrasena|pin|cvv|cvc)\b|\b(tres|3) numeros de atras\b"""),
        GuardKind.CARD to Regex("""\bnumero de (tu|su) tarjeta\b|\bfecha de (vencimiento|caducidad)\b|\b(16|dieciseis) digitos\b"""),
        GuardKind.MONEY to Regex("""\b(yape\w*|plin\w*|transfier\w*|transferencia|deposit\w*|cuenta interbancaria|cci)\b|\b(envie|enviame|mande|mandame|preste|prestame)\b.*\b(plata|dinero|soles|monto)\b"""),
        GuardKind.REMOTE to Regex("""\b(instale|instala|descargue|descarga|baje|baja)\b.*\b(app|aplicacion|programa)\b|\b(anydesk|teamviewer|quicksupport)\b"""),
        GuardKind.FAMILY to Regex("""\b(detenido|detenida|comisaria|accidente|atropell\w*|secuestr\w*|hospital|no le digas a nadie|no le cuentes a nadie)\b"""),
        GuardKind.PRIZE to Regex("""\b(ganaste|ha ganado|gano un|premio|sorteo|bono)\b"""),
    )

    /** Primer peligro de la frase, o null. */
    fun check(text: String): GuardKind? {
        val normalized = normalize(text)
        return rules.firstOrNull { (_, regex) -> regex.containsMatchIn(normalized) }?.first
    }

    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
}
