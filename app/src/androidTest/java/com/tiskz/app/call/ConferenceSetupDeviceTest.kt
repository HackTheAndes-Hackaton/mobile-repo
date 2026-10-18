package com.tiskz.app.call

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiskz.app.data.ConferenceCredentials
import com.tiskz.app.data.SettingsRepository
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Solo almacenamiento aislado y KeyStore; nunca accede a Telecom ni origina llamadas. */
@RunWith(AndroidJUnit4::class)
class ConferenceSetupDeviceTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "conference-test-${UUID.randomUUID()}-"
    private val files = mutableSetOf<String>()
    private val isolated = object : ContextWrapper(base) {
        override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
            val file = prefix + name
            files.add(file)
            return base.getSharedPreferences(file, mode)
        }
    }
    @After fun cleanup() { files.forEach { base.deleteSharedPreferences(it) } }

    @Test fun freshInstallIsOffAndCannotBeEnabledWithoutCostConfirmationAndSim() {
        val repo = SettingsRepository(isolated)
        assertFalse(repo.current.automaticConferenceEnabled)
        assertFalse(repo.current.conferenceCostsConfirmed)
        repo.update { it.copy(automaticConferenceEnabled = true) }
        assertFalse(repo.current.automaticConferenceEnabled)
        repo.update { it.copy(conferenceAccount = "sim1", automaticConferenceEnabled = true, conferenceCostsConfirmed = true) }
        assertFalse(repo.current.conferenceCostsConfirmed)
        assertFalse(repo.current.automaticConferenceEnabled)
    }

    @Test fun carrierDestinationAndBackendChangesRequireRearming() {
        val repo = SettingsRepository(isolated)
        repo.update { it.copy(conferenceAccount = "sim1") }
        repo.update { it.copy(conferenceCostsConfirmed = true, conferenceConfirmedRoute = "entel-target") }
        repo.update { it.copy(automaticConferenceEnabled = true) }
        assertTrue(repo.current.automaticConferenceEnabled)
        repo.update { it.copy(conferenceDestination = "+12098195099") }
        assertFalse(repo.current.automaticConferenceEnabled)
        assertFalse(repo.current.conferenceCostsConfirmed)
        assertTrue(repo.current.conferenceConfirmedRoute.isEmpty())
        repo.update { it.copy(conferenceCostsConfirmed = true, conferenceConfirmedRoute = "new-target") }
        repo.update { it.copy(automaticConferenceEnabled = true) }
        repo.update { it.copy(backendUrl = "https://example.test/other") }
        assertFalse(repo.current.automaticConferenceEnabled)
        repo.update { it.copy(conferenceAccount = "sim2") }
        assertFalse(repo.current.conferenceCostsConfirmed)
    }

    @Test fun setupSurvivesReopeningWithoutStoringPlaintextAndIsBoundToBackend() {
        val url = "https://example.test/agent"
        val secret = "test-only-private-access-123"
        val credentials = ConferenceCredentials(isolated)
        assertNull(credentials.read(url))
        credentials.save(url, secret)
        assertEquals(secret, ConferenceCredentials(isolated).read(url))
        assertNull(credentials.read("https://other.test/agent"))
        val prefs = isolated.getSharedPreferences("tiskz_conference_access", Context.MODE_PRIVATE)
        assertFalse(prefs.all.values.any { it.toString().contains(secret) })
        prefs.edit().putString("value", "invalid-ciphertext").commit()
        assertNull(credentials.read(url))
        credentials.clear()
        assertFalse(credentials.configured.value)
        assertNull(credentials.read(url))
    }
}
