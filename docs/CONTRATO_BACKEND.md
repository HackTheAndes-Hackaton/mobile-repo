# Contrato app ↔ backend de agentes

## Canal de micrófono WebRTC (Maritime)

La pantalla **Canal de voz** captura el micrófono a 48 kHz tras autorización y permiso de Android. El audio
viaja por WebRTC al proveedor de transcripción; los eventos de texto se reenvían por WebSocket a
Maritime, que ejecuta comprensión y detección. El código de acceso nunca viaja al proveedor ni se
incluye en la aplicación. El backend usa los créditos/modelos configurados en Maritime.

Base actual: `https://api.maritime.sh/a/ebf0eb81-42c0-4437-a044-1af84af1b755`.
Todas las rutas siguientes conservan el prefijo `/a/<id>`.

1. `POST /v1/sessions`, cabecera `X-Voice-Token: <código privado>`, cuerpo
   `{"consentimiento_analisis":true}`. Devuelve `session_id`, `producer_token`, `observer_token` y caducidad.
2. Abrir `wss://<host>/a/<id>/ws/calls?session=<session_id>&ticket=<producer_token>` y enviar
   `{"type":"session.start"}`. Los tokens de sala son temporales y no deben registrarse en logs.
3. Crear pista de audio y DataChannel `oai-events`, generar la oferta SDP y enviar
   `POST /v1/sessions/<session_id>/offer`, con `X-Voice-Token: <producer_token>` y `{"sdp":"..."}`.
   Aplicar el `sdp` de respuesta al PeerConnection.
4. Al abrirse el DataChannel se habilita la pista. El detector local de pausas envía
   `input_audio_buffer.commit` después del silencio; evita cortar una frase mientras hay voz continua.
5. Reenviar los deltas/finales del proveedor como `transcript.update` por el WebSocket productor:
   `{"type":"transcript.update","id":"item-1","speaker":"user","text":"...","final":true,"sequence":1}`.
   Solo los segmentos finales activan los agentes; los parciales reemplazan el mismo `id` en pantalla.
6. Detener cierra pista, PeerConnection y WebSocket y solicita
   `POST /v1/sessions/<session_id>/end`, con el token productor. También se cierra al ir al fondo.

Se usa `X-Voice-Token` porque el proxy de Maritime reserva `Authorization`.
La app no persiste ni el código ni audio/texto de este canal. Cada sesión dura como máximo 15 minutos.

### Resultados de los dos agentes

```json
{"type":"alert.updated","alerta":true,"probability":0.88,"threshold":0.5,"revision":2,"provisional":true}
{"type":"understanding.update","revision":2,"analysis":{"rol":"comprension","modo":"extractivo_sin_identidades_v1","resumen":"Fragmentos seleccionados para revisar: peticiones. Las citas conservan el texto recibido; no se atribuyen identidades ni hablantes.","evidencias":[{"tipo":"solicitud","cita":"Transfiere ahora.","observacion":"Fragmento seleccionado sobre una petición; no identifica a su autor ni destinatario."}],"datos_observados":[],"incertidumbres":[],"limitaciones":[]}}
```

La comprensión acepta solo tipos permitidos y citas de oraciones completas. El servidor descarta resumen, participantes y observaciones libres del modelo; genera las descripciones y limitaciones con texto controlado. Una mención «Soy Ana» puede aparecer literalmente como evidencia, pero nunca identifica a una persona. No se reparan palabras dudosas por conjetura.

`transcript.update.sequence` conserva el orden de los turnos aunque los finales lleguen invertidos; la app lo usa para ordenar y reemplazar parciales. Un cierre remoto WebSocket termina la captura inmediatamente sin esperar el ping ni requerir `call.ended`.

`alerta` es un booleano estricto. Antes de recibirlo el resultado es pendiente, nunca `false` por defecto.
La pantalla muestra el booleano y la comprensión por separado. Las revisiones antiguas se descartan.
`risk.update` puede acompañar la alerta con `score_kind: "probabilidad_de_alerta_no_calibrada"`:
ese valor no es una probabilidad calibrada de estafa y el nuevo canal no lo presenta como tal.
Un evento `error` conserva la última alerta y añade un aviso; no significa ausencia de riesgo.
No se confirma identidad o culpabilidad ni se realizan denuncias automáticas.

## Prototipo de llamadas de operadora

El flujo siguiente pertenece al prototipo anterior. Su modo demo usa eventos locales; para audio
de operadora real requiere un puente externo. El nuevo canal de micrófono no usa `caller` ni
necesita configurar manualmente una URL WebSocket.

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
