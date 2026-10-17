package com.tiskz.app.analysis

import com.tiskz.app.model.CallAnalysis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class UserDecision(val wire: String) {
    ANSWER("answer"),
    REJECT("reject"),
    HANG_UP("hang_up"),
    BLOCK("block"),
    NOT_SCAM("not_scam"),
}

/** Conexión con los agentes durante una llamada. Una sesión por llamada entrante. */
abstract class AnalysisSession {
    private val _state = MutableStateFlow(CallAnalysis())
    val state: StateFlow<CallAnalysis> = _state.asStateFlow()

    protected fun dispatch(event: AnalysisEvent) {
        _state.update { it.reduce(event) }
    }

    protected fun resetAnalysis() { _state.value = CallAnalysis() }

    fun markNotScam() {
        dispatch(AnalysisEvent.MarkedSafeByUser)
        send(UserDecision.NOT_SCAM)
    }

    abstract fun send(decision: UserDecision)

    abstract fun close()
}
