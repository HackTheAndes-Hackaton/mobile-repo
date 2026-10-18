package com.tiskz.app.call

import org.junit.Assert.*
import org.junit.Test

class AutomaticConferenceTest {
    private class Rig {
        var now = 0L
        var authorized = true
        val effects = mutableListOf<String>()
        val target = ConferenceTarget("+12098195098", "19900012098195098")
        val controller = AutomaticConference("caller", "entel-sim", "request-1", target, object : ConferenceActions {
            override fun answer(id: String) { effects += "answer:$id" }
            override fun hold(id: String) { effects += "hold:$id" }
            override fun dial(account: String, requestId: String) { effects += "dial:$account:$requestId" }
            override fun merge(first: String, second: String) { effects += "merge:$first:$second" }
            override fun mergeConference(id: String) { effects += "merge-conference:$id" }
            override fun disconnect(id: String) { effects += "disconnect:$id" }
            override fun unhold(id: String) { effects += "unhold:$id" }
            override fun closeAlerts() { effects += "close-alerts" }
        }, { authorized }, { now }, {})
        fun caller(phase: LegPhase = LegPhase.RINGING, canHold: Boolean = true) =
            ConferenceLeg("caller", phase, account = "entel-sim", canHold = canHold)
        fun agent(phase: LegPhase = LegPhase.DIALING) = ConferenceLeg("agent", phase,
            account = "entel-sim", outgoing = true, number = target.number, agentRequest = "request-1")
        fun start() { controller.start(listOf(caller())) }
        fun dial() {
            start(); controller.alertsReady()
            controller.callsChanged(listOf(caller(LegPhase.ACTIVE)))
            controller.callsChanged(listOf(caller(LegPhase.HOLDING)))
        }
        fun merge() {
            dial()
            controller.callsChanged(listOf(caller(LegPhase.HOLDING).copy(conferenceable = setOf("agent")), agent(LegPhase.ACTIVE)))
        }
        fun connected() {
            merge()
            controller.callsChanged(listOf(caller(LegPhase.ACTIVE).copy(parent = "conference"),
                agent(LegPhase.ACTIVE).copy(parent = "conference"),
                ConferenceLeg("conference", LegPhase.ACTIVE, children = setOf("caller", "agent"))))
        }
    }

    @Test fun disabledOrUnconfirmedNeverAuthorizesACall() {
        assertFalse(ConferenceGate().allowed)
        val gate = ConferenceGate(true, true, true, true, true, "sim1", "sim1", "route", "route")
        assertTrue(gate.allowed)
        listOf(gate.copy(enabled = false), gate.copy(costsConfirmed = false), gate.copy(defaultDialer = false),
            gate.copy(callPermission = false), gate.copy(credentialsReady = false), gate.copy(incomingAccount = "sim2"),
            gate.copy(currentRoute = "different-carrier-or-number"), gate.copy(confirmedRoute = ""))
            .forEach { assertFalse(it.allowed) }
    }

    @Test fun waitsForAuthenticatedAlertsAndActualHoldBeforeDialingExactlyOnce() {
        val r = Rig(); r.start()
        assertTrue(r.effects.isEmpty())
        r.controller.alertsReady(); r.controller.alertsReady()
        assertEquals(listOf("answer:caller"), r.effects)
        r.controller.callsChanged(listOf(r.caller(LegPhase.ACTIVE)))
        assertFalse(r.effects.any { it.startsWith("dial:") })
        repeat(3) { r.controller.callsChanged(listOf(r.caller(LegPhase.HOLDING))) }
        assertEquals(1, r.effects.count { it.startsWith("dial:") })
        assertEquals(ConferencePhase.DIALING, r.controller.state.phase)
    }

    @Test fun noAuthorizationLeavesIncomingCallUntouched() {
        val r = Rig(); r.authorized = false; r.start(); r.controller.alertsReady()
        assertEquals(listOf("close-alerts"), r.effects)
        assertEquals(ConferencePhase.FAILED, r.controller.state.phase)
    }

    @Test fun authenticationTimeoutDoesNotAnswerOrDial() {
        val r = Rig(); r.start(); r.now = 12_001; r.controller.tick(); r.controller.alertsReady()
        assertEquals(listOf("close-alerts"), r.effects)
    }

    @Test fun revokingAuthorizationAfterAnswerDoesNotDial() {
        val r = Rig(); r.start(); r.controller.alertsReady()
        r.controller.callsChanged(listOf(r.caller(LegPhase.ACTIVE)))
        r.authorized = false
        r.controller.callsChanged(listOf(r.caller(LegPhase.HOLDING)))
        assertFalse(r.effects.any { it.startsWith("dial:") })
        assertTrue(r.effects.contains("unhold:caller"))
    }

    @Test fun noHoldCapabilityKeepsTheOriginalCall() {
        val r = Rig(); r.start(); r.controller.alertsReady()
        r.controller.callsChanged(listOf(r.caller(LegPhase.ACTIVE, canHold = false)))
        assertEquals(ConferencePhase.FAILED, r.controller.state.phase)
        assertFalse(r.effects.any { it.startsWith("dial:") || it.startsWith("disconnect:") })
    }

    @Test fun mergesOnlyTaggedAgentOnTheSameSimAndAfterConferenceCapability() {
        val r = Rig(); r.dial()
        val caller = r.caller(LegPhase.HOLDING).copy(conferenceable = setOf("agent"))
        r.controller.callsChanged(listOf(caller, r.agent(LegPhase.ACTIVE).copy(account = "other-sim")))
        r.controller.callsChanged(listOf(caller, r.agent(LegPhase.ACTIVE).copy(agentRequest = null)))
        r.controller.callsChanged(listOf(r.caller(LegPhase.HOLDING), r.agent(LegPhase.ACTIVE)))
        assertFalse(r.effects.any { it.startsWith("merge:") })
        repeat(3) { r.controller.callsChanged(listOf(caller, r.agent(LegPhase.ACTIVE))) }
        assertEquals(1, r.effects.count { it.startsWith("merge:") })
        assertEquals(ConferencePhase.MERGING, r.controller.state.phase)
    }

    @Test fun conferenceMustContainBothLegsBeforeReportingConnected() {
        val r = Rig(); r.merge()
        r.controller.callsChanged(listOf(r.caller(LegPhase.HOLDING), r.agent(LegPhase.ACTIVE),
            ConferenceLeg("unrelated", LegPhase.ACTIVE, children = setOf("caller", "someone-else"))))
        assertEquals(ConferencePhase.MERGING, r.controller.state.phase)
        r.controller.callsChanged(listOf(r.caller(LegPhase.ACTIVE), r.agent(LegPhase.ACTIVE),
            ConferenceLeg("ours", LegPhase.ACTIVE, children = setOf("caller", "agent"))))
        assertEquals(ConferencePhase.CONNECTED, r.controller.state.phase)
        assertEquals("ours", r.controller.displayCallId)
    }

    @Test fun noAnswerTimeoutClosesOnlyAgentAndResumesCallerWithoutRetry() {
        val r = Rig(); r.dial()
        r.controller.callsChanged(listOf(r.caller(LegPhase.HOLDING), r.agent()))
        r.now = 25_001; repeat(3) { r.controller.tick() }
        assertEquals(ConferencePhase.FAILED, r.controller.state.phase)
        assertTrue(r.effects.contains("disconnect:agent"))
        assertTrue(r.effects.contains("unhold:caller"))
        assertFalse(r.effects.contains("disconnect:caller"))
        assertEquals(1, r.effects.count { it.startsWith("dial:") })
    }

    @Test fun originalCallerHangupCleansUpAgent() {
        val r = Rig(); r.dial()
        r.controller.callsChanged(listOf(r.caller(LegPhase.DISCONNECTED), r.agent()))
        assertEquals(ConferencePhase.ENDED, r.controller.state.phase)
        assertTrue(r.effects.contains("disconnect:agent"))
    }

    @Test fun cancellationAlsoDisconnectsLateCreatedAgent() {
        val r = Rig(); r.dial(); r.controller.cancel()
        repeat(3) { r.controller.callsChanged(listOf(r.caller(LegPhase.ACTIVE), r.agent())) }
        assertEquals(1, r.effects.count { it == "disconnect:agent" })
        assertFalse(r.effects.contains("disconnect:caller"))
    }

    @Test fun alertsLostDuringCallDoNotRedialAndRestoreCaller() {
        val r = Rig(); r.merge(); r.controller.alertsLost()
        assertEquals(ConferencePhase.FAILED, r.controller.state.phase)
        assertTrue(r.effects.contains("unhold:caller"))
        assertTrue(r.effects.contains("disconnect:agent"))
        assertFalse(r.effects.contains("disconnect:caller"))
    }

    @Test fun originalLegCanDisappearTemporarilyWhileParentIsBeingCreated() {
        val r = Rig(); r.merge()
        r.controller.callsChanged(listOf(r.agent(LegPhase.ACTIVE)))
        assertEquals(ConferencePhase.MERGING, r.controller.state.phase)
        r.controller.callsChanged(listOf(r.agent(LegPhase.ACTIVE),
            ConferenceLeg("conference", LegPhase.ACTIVE, children = setOf("caller", "agent"))))
        assertEquals(ConferencePhase.CONNECTED, r.controller.state.phase)
    }

    @Test fun hangupDisconnectsParentAndBothLegs() {
        val r = Rig(); r.connected(); r.controller.hangUp()
        assertEquals(ConferencePhase.ENDED, r.controller.state.phase)
        assertTrue(r.effects.containsAll(listOf("disconnect:conference", "disconnect:caller", "disconnect:agent", "close-alerts")))
    }

    @Test fun parentNotYetActiveDoesNotClaimConferenceConnected() {
        val r = Rig(); r.merge()
        r.controller.callsChanged(listOf(r.caller(LegPhase.HOLDING), r.agent(LegPhase.ACTIVE),
            ConferenceLeg("conference", LegPhase.DIALING, children = setOf("caller", "agent"))))
        assertEquals(ConferencePhase.MERGING, r.controller.state.phase)
    }

    @Test fun volteMergesWithMergeConferenceWhenNoConferenceableCallsAreListed() {
        val r = Rig(); r.dial()
        r.controller.callsChanged(listOf(r.caller(LegPhase.HOLDING), r.agent(LegPhase.ACTIVE).copy(canMerge = true)))
        assertEquals(listOf("merge-conference:agent"), r.effects.filter { it.startsWith("merge") })
        assertEquals(ConferencePhase.MERGING, r.controller.state.phase)
    }

    @Test fun volteConferenceReplacingBothLegsCountsAsConnectedAndLivesUntilItEnds() {
        val r = Rig(); r.dial()
        r.controller.callsChanged(listOf(r.caller(LegPhase.HOLDING), r.agent(LegPhase.ACTIVE).copy(canMerge = true)))
        r.controller.callsChanged(listOf(ConferenceLeg("ims", LegPhase.ACTIVE, isConference = true)))
        assertEquals(ConferencePhase.CONNECTED, r.controller.state.phase)
        assertEquals("ims", r.controller.displayCallId)
        r.now = 600_000; r.controller.tick()
        assertEquals(ConferencePhase.CONNECTED, r.controller.state.phase)
        r.controller.callsChanged(emptyList())
        assertEquals(ConferencePhase.ENDED, r.controller.state.phase)
    }

    @Test fun agentLeavingConferenceStopsMonitoringWithoutHangingUpCaller() {
        val r = Rig(); r.connected()
        r.controller.callsChanged(listOf(r.caller(LegPhase.ACTIVE).copy(parent = "conference"),
            r.agent(LegPhase.DISCONNECTED),
            ConferenceLeg("conference", LegPhase.ACTIVE, children = setOf("caller"))))
        assertEquals(ConferencePhase.FAILED, r.controller.state.phase)
        assertFalse(r.effects.contains("disconnect:caller"))
        assertTrue(r.effects.contains("close-alerts"))
    }
}
