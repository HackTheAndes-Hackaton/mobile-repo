package com.tiskz.app.call

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiskz.app.PhoneWardApp
import com.tiskz.app.ui.call.LiveCallActions
import com.tiskz.app.ui.call.LiveCallScreen
import com.tiskz.app.ui.theme.PhoneWardTheme

/** Pantalla de llamada de PhoneWard. Se muestra sobre la pantalla de bloqueo. */
class InCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
        )
        val settings = (application as PhoneWardApp).container.settings
        if (savedInstanceState == null) handleNoticeAction(intent)

        setContent {
            PhoneWardTheme {
                val call by CallManager.activeCall.collectAsStateWithLifecycle()
                val analysis by CallManager.analysis.collectAsStateWithLifecycle()
                val conference by CallManager.conference.collectAsStateWithLifecycle()
                val guard by CallManager.guard.collectAsStateWithLifecycle()
                val currentSettings by settings.settings.collectAsStateWithLifecycle()

                LaunchedEffect(call == null) {
                    if (call == null) finish()
                }

                call?.let {
                    LiveCallScreen(
                        call = it,
                        analysis = analysis,
                        answerThreshold = currentSettings.answerThreshold,
                        conference = conference,
                        guard = guard,
                        actions = LiveCallActions(
                            onAnswer = CallManager::answer,
                            onJoin = CallManager::join,
                            onReject = CallManager::reject,
                            onHangUp = CallManager::hangUp,
                            onBlock = CallManager::blockAndHangUp,
                            onNotScam = CallManager::markNotScam,
                            onToggleMute = CallManager::toggleMute,
                            onToggleSpeaker = CallManager::toggleSpeaker,
                            onDismissGuard = CallManager::dismissGuard,
                            onClose = {
                                CallManager.dismissEnded()
                                finish()
                            },
                        ),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNoticeAction(intent)
    }

    override fun onResume() {
        super.onResume()
        CallNotifications.screenVisible = true
    }

    override fun onPause() {
        CallNotifications.screenVisible = false
        super.onPause()
    }

    /** "Responder" y "Entrar" del aviso abren esta pantalla y ejecutan la acción una sola vez. */
    private fun handleNoticeAction(intent: Intent) {
        when (intent.getStringExtra(CallNotifications.EXTRA_ACTION)) {
            CallNotifications.ACTION_ANSWER -> CallManager.answer()
            CallNotifications.ACTION_JOIN -> CallManager.join()
        }
        intent.removeExtra(CallNotifications.EXTRA_ACTION)
    }
}
