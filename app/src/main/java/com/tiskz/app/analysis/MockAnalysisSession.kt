package com.tiskz.app.analysis

import com.tiskz.app.data.DemoScenario
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Sesión de demo: reproduce un guion de eventos como si vinieran de los agentes. */
class MockAnalysisSession(scenario: DemoScenario) : AnalysisSession() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            for (step in DemoScripts.forScenario(scenario)) {
                delay(step.delayMs)
                dispatch(step.event)
            }
        }
    }

    override fun send(decision: UserDecision) = Unit

    override fun close() {
        scope.cancel()
    }
}
