package com.tiskz.app.call

import android.telecom.Call
import android.telecom.CallScreeningService
import com.tiskz.app.PhoneWardApp

/** Corre antes de que suene el teléfono: corta en silencio los números bloqueados. */
class PhoneWardCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart.orEmpty()
        val incoming = callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        val blocked = incoming && (application as PhoneWardApp).container.blocklist.isBlocked(number)

        val response = CallResponse.Builder().apply {
            if (blocked) {
                setDisallowCall(true)
                setRejectCall(true)
                setSkipNotification(true)
            }
        }.build()
        respondToCall(callDetails, response)
    }
}
