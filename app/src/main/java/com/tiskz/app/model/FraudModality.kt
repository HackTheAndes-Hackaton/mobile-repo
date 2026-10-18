package com.tiskz.app.model

import org.json.JSONArray
import org.json.JSONObject

data class FraudSource(val label: String, val url: String)

/** Referencias editoriales fijas: nunca enlaces redactados por el modelo o el llamante. */
val fraudSources = mapOf(
    "lazo_rivas_2022" to FraudSource("Lazo García y Rivas Champion (2022)", "https://doi.org/10.15381/lengsoc.v21i2.22535"),
    "sedano_2024" to FraudSource("Sedano Pinzón (2024)", "https://doi.org/10.56712/latam.v5i5.2691"),
    "bde_vishing_2020" to FraudSource("Banco de España · guía de vishing", "https://clientebancario.bde.es/pcb/es/blog/vishing.html"),
)

private val modalityLabels = mapOf(
    "posible_extorsion" to "Posible extorsión",
    "amenaza_por_verificar" to "Amenaza por verificar",
    "posible_fraude_credenciales" to "Posible fraude de claves o códigos (vishing)",
    "credenciales_por_verificar" to "Petición de claves o códigos por verificar",
    "posible_fraude_acceso_remoto" to "Posible fraude de acceso remoto",
    "acceso_remoto_por_verificar" to "Acceso remoto por verificar",
    "posible_fraude_pago" to "Posible fraude de pago bajo presión",
    "peticion_economica_por_verificar" to "Petición de dinero por verificar",
    "relato_o_denuncia" to "Relato o denuncia de una amenaza",
    "solicitud_cotidiana" to "Solicitud cotidiana",
)

data class FraudModality(val category: String, val hypothesis: Boolean, val quotes: List<String>, val sources: List<String>) {
    val label: String get() = modalityLabels[category].orEmpty()
    val explanation: String get() = when (category) {
        "posible_extorsion" -> "Hay una amenaza ligada a una exigencia o condición."
        "amenaza_por_verificar" -> "Hay una posible amenaza, pero falta contexto sobre la exigencia."
        "posible_fraude_credenciales", "credenciales_por_verificar" -> "Se pide compartir una clave o un código de acceso."
        "posible_fraude_acceso_remoto", "acceso_remoto_por_verificar" -> "Se pide acceso o control del dispositivo."
        "posible_fraude_pago" -> "Hay un pedido de dinero con otras señales de riesgo."
        "peticion_economica_por_verificar" -> "Hay un pedido de dinero; por sí solo no demuestra engaño."
        "relato_o_denuncia" -> "Se está contando una amenaza recibida o citada."
        "solicitud_cotidiana" -> "La petición observada es cotidiana."
        else -> "Falta contexto para determinar el motivo."
    }
    fun toJson() = JSONObject().put("categoria", category)
        .put("interpretacion", if (hypothesis) "hipotesis_preventiva" else "conducta_por_revisar")
        .put("evidencias", JSONArray(quotes.map { JSONObject().put("cita", it) }))
        .put("fuentes", JSONArray(sources))

    companion object {
        fun parse(array: JSONArray?, allowedQuotes: Set<String>? = null): List<FraudModality> {
            if (array == null || array.length() > 10) return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                val m = array.optJSONObject(i) ?: return@mapNotNull null
                val category = m.optString("categoria").takeIf { it in modalityLabels } ?: return@mapNotNull null
                val interpretation = m.optString("interpretacion")
                if (interpretation !in setOf("hipotesis_preventiva", "conducta_por_revisar")) return@mapNotNull null
                if ((interpretation == "hipotesis_preventiva") != category.startsWith("posible_")) return@mapNotNull null
                val evidence = m.optJSONArray("evidencias") ?: return@mapNotNull null
                if (evidence.length() !in 1..8) return@mapNotNull null
                val quotes = (0 until evidence.length()).mapNotNull { j ->
                    (evidence.optJSONObject(j)?.opt("cita") as? String)?.takeIf {
                        it.isNotBlank() && it.length <= 600 && (allowedQuotes == null || it in allowedQuotes)
                    }
                }
                if (quotes.size != evidence.length()) return@mapNotNull null
                val refs = m.optJSONArray("fuentes")
                val sources = if (refs == null || refs.length() > 3) emptyList() else
                    (0 until refs.length()).mapNotNull { refs.optString(it).takeIf { id -> id in fraudSources } }.distinct()
                FraudModality(category, interpretation == "hipotesis_preventiva", quotes.distinct(), sources)
            }.distinctBy { it.category }
        }
    }
}
