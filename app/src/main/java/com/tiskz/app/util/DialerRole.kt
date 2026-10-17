package com.tiskz.app.util

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build

/** Rol de "app de teléfono": con él, Android muestra nuestras pantallas en cada llamada. */
object DialerRole {

    fun isHeld(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true

    fun requestIntent(context: Context): Intent? =
        context.getSystemService(RoleManager::class.java)
            ?.takeIf { it.isRoleAvailable(RoleManager.ROLE_DIALER) }
            ?.createRequestRoleIntent(RoleManager.ROLE_DIALER)

    val runtimePermissions: Array<String>
        get() = buildList {
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
}
