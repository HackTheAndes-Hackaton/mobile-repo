# Pendientes de Tiskz

Estado al 18 de octubre de 2026, después de:

- `web-repo` `f90c434`: reputación comunitaria del número y evidencia para la PNP.
- `mobile-repo` `60d91a5`: alerta dentro de la llamada y reputación comunitaria del número.

## 1. Repo de agentes (estado actualizado)

| Pendiente | Por qué importa |
|---|---|
| **`call.ready_to_answer` implementado** | Integrado en main: revisión actual en `observar`, sin alerta. No verifica identidad. Se observó el evento en una llamada real; falta comprobar todos los avisos visuales. |
| **Señales peruanas en el prompt del analizador** (familiaridad forzada, saludo eufórico, OTP, Yape, BCP o SUNAT) | Hoy solo están como reglas en el panel y en la app; el porcentaje de riesgo en vivo no las usa. |
| **`maxDuration` de Vapi** | Si es corto, Vapi cuelga al agente aunque la app ya no tenga límite. |
| **Número de Vapi peruano** (hoy es +1 209…) | Cada llamada es larga distancia internacional y une llamadas por la operadora, lo que es frágil. |

### Contrato vigente de revisión inicial

El evento `call.ready_to_answer` se emite tras completar comprensión y detección de la misma revisión, con estado `observar` y sin alerta. No confirma identidad ni garantiza seguridad. Un nuevo turno invalida la revisión previa. Véase [CONTRATO_BACKEND.md](CONTRATO_BACKEND.md).

Estado de captura, corte y comparación de voz: [CONTINUIDAD_EQUIPO.md](CONTINUIDAD_EQUIPO.md).

## 2. Configuración manual

- [ ] Crear la **API key en el panel de producción**: https://web-repo-xi.vercel.app → Integraciones → Nueva clave.
- [ ] Pegarla en la app: **Ajustes → Historial en el panel**. Sin ella no se guardan las conversaciones ni funciona la reputación.
- [ ] Activar **"Notificaciones flotantes"** de Tiskz en el Xiaomi de la demo, y en cualquier otro teléfono que se use.

## 3. Pruebas de punta a punta que faltan

- [ ] **Guardar una llamada real contra la BD de producción.** Solo se probó en local; en producción los endpoints responden, pero sin clave se quedan en 401.
- [ ] **La reputación en el teléfono.** Funciona en local; falta la clave para verla en la app.
- [ ] **Una llamada real completa con el flujo nuevo**: pop-up de entrada → aviso discreto → "espacio seguro" → entrar → alerta si piden el código. Las pruebas fueron con la llamada simulada.
- [ ] **El pop-up de "Te están llamando" con una llamada real.** En la simulación no salta porque Tiskz está abierto.

## 4. Pendientes menores

- [x] Las pruebas Android pasan: 65 pruebas JVM, compilación y lint en la versión 0.4.6.
- [ ] `bun.lock` sin subir en `web-repo`. Decidir si va al repo.
- [ ] `docs/PROBLEMA_PERU.md` (estado del arte con cifras de Perú) está en local y nunca se subió.
- [ ] Sección "Validación con la PNP" en la documentación, con los aprendizajes de la entrevista.

## Simulación opcional

La simulación permite revisar la interfaz; no valida audio real, guardado de voz ni conferencia de la operadora:

```bash
adb shell am start -n com.tiskz.app/.MainActivity --es simulate safe
```

Con `--es simulate scam` se ve la estafa.
