package com.tiskz.app.call

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import com.tiskz.app.model.CallPhase

/**
 * Mientras suena, HyperOS (Xiaomi) revisa si la app de teléfono tiene una ventana propia; si PhoneWard no tiene
 * ninguna (por ejemplo, tras «Cerrar todo» en recientes), pasa la llamada al teléfono del sistema.
 * Esta ventana es invisible y no recibe toques: el titular sigue viendo solo el aviso de llamada.
 * Se cierra sola cuando deja de sonar.
 */
class RingingAnchorActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private val check = object : Runnable {
        override fun run() {
            val ringing = CallManager.activeCall.value?.phase == CallPhase.RINGING
            if (!ringing || System.currentTimeMillis() - startedAt > MAX_MS) finish() else handler.postDelayed(this, POLL_MS)
        }
    }
    private var startedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        )
        // Opacidad 0: Android deja pasar los toques a la app de abajo sin bloquearlos.
        window.attributes = window.attributes.apply { alpha = 0f }
        startedAt = System.currentTimeMillis()
        handler.post(check)
    }

    override fun onDestroy() {
        handler.removeCallbacks(check)
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val POLL_MS = 300L
        private const val MAX_MS = 60_000L

        fun show(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(context, RingingAnchorActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION),
                )
            }
        }
    }
}
