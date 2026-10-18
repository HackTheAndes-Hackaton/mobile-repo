package com.tiskz.app.voice

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiskz.app.MainActivity
import com.tiskz.app.analysis.EventParser
import com.tiskz.app.analysis.reduce
import com.tiskz.app.model.ActiveCall
import com.tiskz.app.model.AssessmentState
import com.tiskz.app.model.CallAnalysis
import com.tiskz.app.model.CallDirection
import com.tiskz.app.model.CallPhase
import com.tiskz.app.ui.call.LiveCallActions
import com.tiskz.app.ui.call.LiveCallScreen
import com.tiskz.app.ui.components.AssessmentCard
import com.tiskz.app.ui.theme.PhoneWardTheme
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Datos sintéticos, sin micrófono, red telefónica ni credenciales. */
@RunWith(AndroidJUnit4::class)
class AssessmentDisplayTest {
    @Test fun fourStatesRenderOnVoiceCardAndLiveCallWithoutTriggeringActions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        val analysis = mutableStateOf(CallAnalysis())
        val fullCall = mutableStateOf(false)
        var actionsInvoked = 0
        val action = { actionsInvoked++; Unit }
        val actions = LiveCallActions(action, action, action, action, action, action, action, action, action)
        val call = ActiveCall("+51900000000", CallDirection.INCOMING, CallPhase.ACTIVE, simulated = true)
        try {
            instrumentation.runOnMainSync {
                activity.setContent { PhoneWardTheme {
                    if (fullCall.value) LiveCallScreen(call, analysis.value, 40, actions)
                    else AssessmentCard(analysis.value)
                } }
            }
            for (screen in listOf(false, true)) {
                for (state in AssessmentState.entries) {
                    val guidance = "Orientación de prueba: ${state.label}."
                    val raw = JSONObject().put("type", "assessment.update").put("revision", 1)
                        .put("assessment", JSONObject().put("version", "orientacion_v1").put("estado", state.wireValue)
                            .put("orientacion", guidance).put("evidencia_textual", org.json.JSONArray())).toString()
                    instrumentation.runOnMainSync {
                        analysis.value = CallAnalysis().reduce(EventParser.parse(raw)!!)
                        fullCall.value = screen
                    }
                    val deadline = System.currentTimeMillis() + 5000
                    var shown = false
                    while (System.currentTimeMillis() < deadline && !shown) {
                        instrumentation.waitForIdleSync()
                        val texts = visibleTexts(instrumentation.uiAutomation.rootInActiveWindow)
                        shown = texts.any { it.contains(guidance) } && texts.any { it == state.label }
                        if (!shown) Thread.sleep(50)
                    }
                    val bitmap = instrumentation.uiAutomation.takeScreenshot()
                    File(context.getExternalFilesDir(null), "assessment-${if (screen) "call" else "voice"}-${state.wireValue}.png")
                        .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                    assertTrue("Debe mostrarse ${state.label} en ${if (screen) "llamada" else "voz"}; ventana=${instrumentation.uiAutomation.rootInActiveWindow?.packageName}", shown)
                }
            }
            assertEquals("Presentar orientación nunca debe accionar la llamada", 0, actionsInvoked)
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

    private fun visibleTexts(node: AccessibilityNodeInfo?): List<String> {
        if (node == null) return emptyList()
        val own = if (node.isVisibleToUser) listOfNotNull(node.text?.toString(), node.contentDescription?.toString()) else emptyList()
        return own + (0 until node.childCount).flatMap { visibleTexts(node.getChild(it)) }
    }
}
