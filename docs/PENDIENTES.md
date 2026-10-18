# Pendientes de Tiskz

Estado al 18 de octubre de 2026, después de:

- `web-repo` `f90c434`: reputación comunitaria del número y evidencia para la PNP.
- `mobile-repo` `60d91a5`: alerta dentro de la llamada y reputación comunitaria del número.

## 1. Repo de agentes (no implementado)

| Pendiente | Por qué importa |
|---|---|
| **El agente debe enviar `call.ready_to_answer`** cuando verifica quién llama | Sin esto, en una llamada real **nunca aparece "Ya puedes entrar"**. Es el hueco más grande del flujo principal. |
| **Señales peruanas en el prompt del analizador** (familiaridad forzada, saludo eufórico, OTP, Yape, BCP o SUNAT) | Hoy solo están como reglas en el panel y en la app; el porcentaje de riesgo en vivo no las usa. |
| **`maxDuration` de Vapi** | Si es corto, Vapi cuelga al agente aunque la app ya no tenga límite. |
| **Número de Vapi peruano** (hoy es +1 209…) | Cada llamada es larga distancia internacional y une llamadas por la operadora, lo que es frágil. |

### Contrato sugerido para "espacio seguro"

El agente pide 4 datos: **nombre**, **entidad**, **motivo** y **si va a pedir algo** (dinero, códigos o datos).
Si las respuestas son coherentes y no hay señales de riesgo, envía por el canal de alertas:

```json
{ "type": "call.ready_to_answer" }
```

La app también acepta `{ "type": "agent.status", "status": "safe" }`. Ambos están en `docs/CONTRATO_BACKEND.md`.
Para el resumen del pop-up ("Tu agente analizó la llamada: …"), conviene enviar antes un `understanding.update` con `resumen`.

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

- [ ] `PhoneAlertSessionTest` falla desde antes de estos cambios (NullPointerException). Hay que revisarlo.
- [ ] `bun.lock` sin subir en `web-repo`. Decidir si va al repo.
- [ ] `docs/PROBLEMA_PERU.md` (estado del arte con cifras de Perú) está en local y nunca se subió.
- [ ] Sección "Validación con la PNP" en la documentación, con los aprendizajes de la entrevista.

## Recomendación para la demo

Lo crítico es el punto 1, el `ready_to_answer`. Mientras no esté, usar la **llamada simulada**, que muestra el flujo completo:

```bash
adb shell am start -n com.tiskz.app/.MainActivity --es simulate safe
```

Con `--es simulate scam` se ve la estafa.
