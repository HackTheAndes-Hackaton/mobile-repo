package com.tiskz.app.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallGuardTest {

    @Test
    fun detectsPeruvianScamRequests() {
        assertEquals(GuardKind.OTP, CallGuard.check("Dígame el código que le llegó por SMS para anular la compra"))
        assertEquals(GuardKind.OTP, CallGuard.check("Confírmeme el código de 6 dígitos"))
        assertEquals(GuardKind.PASSWORD, CallGuard.check("Necesito su clave de internet"))
        assertEquals(GuardKind.CARD, CallGuard.check("¿Me da el número de su tarjeta?"))
        assertEquals(GuardKind.MONEY, CallGuard.check("Yapéame 200 soles ahorita"))
        assertEquals(GuardKind.MONEY, CallGuard.check("Haga una transferencia a esta cuenta"))
        assertEquals(GuardKind.REMOTE, CallGuard.check("Descargue la aplicación AnyDesk"))
        assertEquals(GuardKind.FAMILY, CallGuard.check("Tu hijo está detenido en la comisaría"))
        assertEquals(GuardKind.PRIZE, CallGuard.check("¡Felicidades, ganaste un sorteo!"))
    }

    @Test
    fun ignoresOrdinaryConversation() {
        assertNull(CallGuard.check("Hola, soy Carla de la farmacia. La receta ya está lista."))
        assertNull(CallGuard.check("Te llamo para confirmar la reunión de mañana a las 10."))
    }
}
