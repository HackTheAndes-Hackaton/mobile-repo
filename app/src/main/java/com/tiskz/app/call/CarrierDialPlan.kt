package com.tiskz.app.call

/** Reglas LDI de las cuatro operadoras de Perú; no se infieren del número del abonado. */
enum class PeruCarrier(val label: String, val prefix: String, val mccmnc: String, val androidCarrierId: Int) {
    ENTEL("Entel", "1990", "71617", 1930),
    CLARO("Claro", "1912", "71610", 1647),
    MOVISTAR("Movistar", "1911", "71606", 1929),
    BITEL("Bitel", "1968", "71615", 2359);

    companion object {
        fun detect(simOperator: String, carrierId: Int): PeruCarrier? = entries.firstOrNull {
            it.mccmnc == simOperator && (carrierId == -1 || carrierId == it.androidCarrierId)
        }
    }
}

data class ConferenceTarget(val number: String, val dialNumber: String) {
    fun matches(candidate: String): Boolean {
        val digits = candidate.filter(Char::isDigit)
        return digits == number.drop(1) || digits == "00" + number.drop(1) || digits == dialNumber.filter(Char::isDigit)
    }
}

data class CarrierDialPlan(val carrier: PeruCarrier, val carrierId: Int, val target: ConferenceTarget) {
    /** Una confirmación de precio vale solo para esta operadora y este destino. */
    val confirmationKey: String get() = "${carrier.mccmnc}:$carrierId:${carrier.prefix}:${target.number}"
    companion object {
        const val DEFAULT_AGENT = "+12098195098"
        fun destination(raw: String): String? = raw.trim().replace(Regex("[ ()-]"), "")
            .takeIf { Regex("\\+[1-9][0-9]{7,14}").matches(it) }

        fun detect(simOperator: String, carrierId: Int, networkCountry: String, roaming: Boolean, rawNumber: String): CarrierDialPlan? {
            if (roaming || !networkCountry.equals("pe", ignoreCase = true)) return null
            val carrier = PeruCarrier.detect(simOperator, carrierId) ?: return null
            val number = destination(rawNumber) ?: return null
            val dial = if (number.startsWith("+51")) number else carrier.prefix + "00" + number.drop(1)
            return CarrierDialPlan(carrier, carrierId, ConferenceTarget(number, dial))
        }
    }
}
