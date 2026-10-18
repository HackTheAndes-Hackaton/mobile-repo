package com.tiskz.app.call

import org.junit.Assert.*
import org.junit.Test

class CarrierDialPlanTest {
    @Test fun carrierComesFromSimAndProducesItsOwnInternationalPrefix() {
        listOf("71617" to "1990", "71610" to "1912", "71606" to "1911", "71615" to "1968").forEach { (sim, prefix) ->
            val plan = CarrierDialPlan.detect(sim, -1, "pe", false, "+1 209 819 5098")!!
            assertEquals(prefix + "0012098195098", plan.target.dialNumber)
            assertTrue(plan.target.matches("+12098195098"))
            assertTrue(plan.target.matches("0012098195098"))
            assertTrue(plan.target.matches(plan.target.dialNumber))
            assertFalse(plan.target.matches("+12098195099"))
        }
    }

    @Test fun unknownSimMvnoMismatchAndRoamingNeverFallBackToAChargedPrefix() {
        assertNull(CarrierDialPlan.detect("71699", -1, "pe", false, CarrierDialPlan.DEFAULT_AGENT))
        assertNull(CarrierDialPlan.detect("71617", 9999, "pe", false, CarrierDialPlan.DEFAULT_AGENT))
        assertNull(CarrierDialPlan.detect("71617", 1930, "us", true, CarrierDialPlan.DEFAULT_AGENT))
        assertNull(CarrierDialPlan.detect("71617", 1930, "", false, CarrierDialPlan.DEFAULT_AGENT))
        assertNull(CarrierDialPlan.detect("310260", -1, "pe", false, CarrierDialPlan.DEFAULT_AGENT))
    }

    @Test fun rejectsMmiDtmfPauseShortcodesAndMalformedNumbers() {
        listOf("123", "**21*+12098195098#", "+12098195098,123", "+12098195098;123", "+0123456789",
            "+12098\n195098", "+12098195098&foo", "+１２３４５６７８９０").forEach { value ->
            assertNull(value, CarrierDialPlan.destination(value))
        }
    }

    @Test fun domesticAgentDoesNotUseAnInternationalPrefix() {
        assertEquals("+51912345678", CarrierDialPlan.detect("71617", 1930, "pe", false, "+51912345678")!!.target.dialNumber)
    }

    @Test fun confirmationIsBoundToCarrierAndDestination() {
        val entel = CarrierDialPlan.detect("71617", 1930, "pe", false, CarrierDialPlan.DEFAULT_AGENT)!!
        val claro = CarrierDialPlan.detect("71610", 1647, "pe", false, CarrierDialPlan.DEFAULT_AGENT)!!
        val changed = CarrierDialPlan.detect("71617", 1930, "pe", false, "+12098195099")!!
        assertNotEquals(entel.confirmationKey, claro.confirmationKey)
        assertNotEquals(entel.confirmationKey, changed.confirmationKey)
    }
}
