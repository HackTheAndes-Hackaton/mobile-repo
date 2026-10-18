package com.tiskz.app.call

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.core.content.ContextCompat

/** Llamada saliente desde el marcador, Contactos o Recientes. Telecom la entrega a Tiskz como cualquier llamada. */
object Dialing {

    fun canCall(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    fun placeCall(context: Context, number: String) {
        if (number.isBlank()) return
        try {
            context.getSystemService(TelecomManager::class.java)
                .placeCall(Uri.fromParts("tel", number, null), Bundle())
        } catch (e: SecurityException) {
            Toast.makeText(context, "Falta el permiso para llamar", Toast.LENGTH_SHORT).show()
        }
    }
}
