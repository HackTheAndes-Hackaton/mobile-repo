# Contrato app ↔ backend de agentes

La app Tiskz **no procesa audio**. Android no permite que una app lea el audio de una llamada
del operador, así que el audio llega a los agentes por el backend (desvío de llamadas al número
del servidor). La app solo **muestra** lo que los agentes publican y **envía** las decisiones del usuario.

## Conexión

```
GET  {WS_URL}?caller=<número en E.164, URL-encoded>      (WebSocket)
```

- `WS_URL` se configura en `local.properties` (`tiskz.wsUrl=...`) o en Ajustes dentro de la app.
- Se abre **una conexión por llamada entrante**, en cuanto la llamada empieza a sonar.
- El backend usa `caller` para encontrar la sesión del agente que está atendiendo a ese número.

## App → backend

Al conectar:

```json
{ "type": "session.start", "caller_number": "+56987654321", "device_id": "a1b2c3..." }
```

Cuando el usuario toca un botón:

```json
{ "type": "user.decision", "action": "answer" }
```

| `action`   | Significado                                              |
|------------|----------------------------------------------------------|
| `answer`   | El usuario contesta. El agente debe salir de la llamada. |
| `reject`   | El usuario rechaza la llamada.                           |
| `hang_up`  | El usuario cuelga una llamada ya contestada.             |
| `block`    | El usuario bloquea el número (candidato a honeypot).     |
| `not_scam` | El usuario corrige: no es estafa (dato para reentrenar). |

## Backend → app

Todos los mensajes son JSON con un campo `type`. Los tipos desconocidos se ignoran, así que se
pueden agregar campos o tipos nuevos sin romper la app.

### `agent.status`: qué está haciendo el agente

```json
{ "type": "agent.status", "status": "screening" }
```

`status`: `connecting` · `screening` (el agente habla con quien llama) · `analyzing` · `safe` · `suspicious` · `scam`

### `transcript.update`: una frase de la conversación

```json
{ "type": "transcript.update", "id": "c-2", "speaker": "caller", "text": "Necesito la clave que le llegó por SMS", "flagged": true }
```

- `speaker`: `caller` (quien llama) · `agent` (Tiskz) · `user` (el dueño del teléfono)
- `id` (opcional): si se repite, la app **reemplaza** la frase. Sirve para mandar texto parcial y luego el final.
- `flagged`: marca la frase en rojo como sospechosa.

### `risk.update`: el porcentaje del medidor

```json
{
  "type": "risk.update",
  "risk_percent": 93,
  "reasons": ["Pide código SMS (OTP)", "Urgencia artificial"],
  "scam_type": "Phishing bancario",
  "impersonating": "Banco Andino"
}
```

- `risk_percent`: 0–100. **La app espera un número, no una categoría.**
- `reasons`: frases cortas, se muestran como etiquetas. Se reemplazan completas en cada evento.
- `scam_type` e `impersonating`: opcionales; si vienen `null` se mantiene el valor anterior.

Colores en la app: `< 30` azul · `30–59` ámbar · `60–84` naranja · `≥ 85` rojo.
Con `≥ 70` o `status: scam` la app muestra "Posible estafa" y cambia los botones a "Bloquear y colgar".

### `voiceprint.match`: coincidencia de huella de voz

```json
{ "type": "voiceprint.match", "similarity": 0.87, "label": "Coincide con una voz reportada como estafa", "reports": 3 }
```

### `call.ready_to_answer`: el agente terminó de verificar

```json
{ "type": "call.ready_to_answer" }
```

La app destaca "Contestar" si además el riesgo está bajo el umbral configurado (40 % por defecto).

### `call.ended` y `error`

```json
{ "type": "call.ended", "risk_percent": 93 }
{ "type": "error", "message": "No se pudo transcribir el audio" }
```

## Secuencia típica

```
App                                   Backend / agentes
 |--- WS connect ?caller=+569... ------->|
 |--- session.start -------------------->|
 |<-- agent.status: screening -----------|
 |<-- transcript.update (agent) ---------|  "Hola, soy Tiskz…"
 |<-- transcript.update (caller) --------|
 |<-- risk.update 32% -------------------|
 |<-- voiceprint.match 0.87 -------------|
 |<-- risk.update 93% -------------------|
 |<-- agent.status: scam ----------------|
 |--- user.decision: block ------------->|  → el backend puede pasar al honeypot
```

Ejemplo completo de esta secuencia (los mismos eventos que usa el modo demo):
`app/src/main/java/com/tiskz/app/analysis/DemoScripts.kt`.
