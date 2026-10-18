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
La pantalla conserva la alerta y la comprensión por separado, y presenta la orientación descrita abajo. Las revisiones antiguas se descartan.
`risk.update` puede acompañar la alerta con `score_kind: "probabilidad_de_alerta_no_calibrada"`:
ese valor no es una probabilidad calibrada de estafa y el nuevo canal no lo presenta como tal.
Un evento `error` conserva la última alerta y añade un aviso; no significa ausencia de riesgo.
No se confirma identidad o culpabilidad ni se realizan denuncias automáticas.

### Orientación por revisión (APK 0.4.4)

```json
{"type":"assessment.update","session_id":"id-de-sesion","revision":2,"assessment":{"version":"orientacion_v1","estado":"verificar","orientacion":"Antes de pagar, verifica por un canal que ya conoces.","alerta":false,"evidencia_textual":[{"tipo":"peticion_pago","cita":"Necesito dinero."}],"voz_sintetica":{"estado":"no_evaluada"},"identidades_verificadas":[],"acciones_automaticas":false}}
```

Los estados admitidos son `observar`, `verificar`, `alertar` e `inconcluso`. La APK muestra el texto como texto, con nombre de estado además del color y anuncio accesible; no ejecuta instrucciones del campo `orientacion`. Requiere versión conocida, revisión entera no negativa, estado conocido y orientación no vacía. Las citas tienen hasta 8 elementos de hasta 600 caracteres. Un evento inválido se ignora, sin crear un resultado seguro.

El servidor combina los dos agentes de la misma revisión. El cliente rechaza una orientación anterior a sus últimas revisiones de alerta o comprensión; cuando llega una de estas más reciente, oculta la orientación vieja y muestra «Actualizando orientación». En telefonía también exige que el `session_id` coincida con la llamada activa y borra el análisis al empezar otra llamada.

La presentación no cambia `alerta`, `risk_percent`, `readyToAnswer`, consentimiento ni controles de la llamada. El corte sigue usando `call.hangup_requested`, no este evento. Sin `assessment.update`, la orientación aparece pendiente; una alerta previa del detector puede mostrarse mientras se espera la explicación. Los clientes anteriores siguen recibiendo los eventos de riesgo y comprensión.

## Flujo objetivo: llamadas desconocidas (propuesta)

> Pendiente de implementar en el backend. Describe el flujo de [`ANALISIS_Y_FLUJO.md`](ANALISIS_Y_FLUJO.md#4-flujo-principal):
> los contactos suenan normal; los desconocidos se rechazan en silencio, el operador los desvía al
> número de Tiskz y **un único agente** los atiende. No hay un modo honeypot aparte: si es estafa,
> el mismo agente sigue la conversación. **El análisis es continuo** y cubre también la parte de la
> llamada en que el usuario ya está conectado.

Una llamada desconocida tiene dos tramos, y el WebSocket de la llamada cubre ambos:

| Tramo | `call.phase` | Quién habla con quien llama |
|---|---|---|
| 1 | `agent` | El agente de IA |
| 2 | `connected` | El usuario; Tiskz escucha en silencio |
| 1 otra vez | `agent_retaken` | El agente retoma (el usuario pasó la llamada o colgó por alerta) |
| — | `ended` | Nadie |

### Backend → app

**`call.phase`**: cambio de tramo.

```json
{ "type": "call.phase", "phase": "connected" }
```

**`call.verified`**: el agente terminó el tramo 1 y la llamada parece legítima. Llega **antes** de que el
backend llame al usuario desde el número de Tiskz, para que la app muestre "Llamada verificada".

```json
{
  "type": "call.verified",
  "call_id": "c-8f2a",
  "caller_number": "+51987654321",
  "said_name": "Luis",
  "said_company": "Claro",
  "reason": "Ofrecer un cambio de plan",
  "risk_percent": 12
}
```

`said_name` y `said_company` son **lo que dijo quien llama**, no una identidad verificada. La app lo muestra así.

**`insight.update`**: señal detectada en cualquiera de los dos tramos. Es lo que alimenta las alertas
durante la llamada.

```json
{
  "type": "insight.update",
  "id": "i-3",
  "kind": "identity_mismatch",
  "message": "Dijo ser de Claro, ahora habla en nombre del BCP.",
  "quotes": [
    { "phase": "agent", "text": "Soy Luis, asesor de Claro." },
    { "phase": "connected", "text": "Le hablo del área de seguridad del BCP." }
  ],
  "risk_percent": 82
}
```

| `kind` | Significado |
|---|---|
| `identity_mismatch` | Dijo ser de una entidad y luego habla en nombre de otra. |
| `tone_shift` | Cambio de tono entre tramos: verificable con el agente, urgente con el usuario. |
| `sensitive_request` | Pide claves, códigos SMS, transferencias o instalar aplicaciones. |
| `data_mismatch` | Nombre, empresa o motivo distintos a los que dio al agente. |
| `voice_match` | La voz coincide con estafas confirmadas (detalle en `voiceprint.match`). |
| `number_reputation` | El número tiene reportes en la base comunitaria. |

`quotes` son citas literales con el tramo en que se dijeron. Un `id` repetido reemplaza el insight anterior.

En el tramo 2, `transcript.update` usa `speaker: "user"` para el dueño del teléfono y `speaker: "caller"`
para quien llama. `risk.update`, `voiceprint.match` y `error` siguen igual que en el prototipo.

### App → backend

`user.decision` agrega acciones para el tramo 2:

| `action` | Cuándo | Efecto |
|---|---|---|
| `answer` | Llamada verificada o en el agente | Une al usuario con quien llama (`call.phase: connected`). |
| `handoff_to_agent` | Tramo 2, tras una alerta | Saca al usuario; el agente retoma (`agent_retaken`). |
| `legit` | Tramo 2, tras una alerta | El usuario indica que la llamada es legítima; se usa para corregir el análisis. |
| `hang_up` | Cualquiera | Cuelga. |
| `block` | Cualquiera | Reporta el número; si hay un tramo activo, el agente retiene la llamada. |
| `not_scam` | Tras el tramo 1 | Corrección: no es estafa. |

### Secuencia (estafador que cambia de rol)

```
App                                        Backend / agente
 |<-- call.phase: agent ------------------------|  el agente atiende al desconocido
 |<-- transcript.update (agent / caller) -------|  "Soy Luis, asesor de Claro"
 |<-- risk.update 12% --------------------------|
 |<-- call.verified ----------------------------|  la app muestra "Llamada verificada"
 |--- user.decision: answer ------------------->|
 |<-- call.phase: connected --------------------|  Tiskz escucha en silencio
 |<-- transcript.update (caller) ---------------|  "…del área de seguridad del BCP"
 |<-- insight.update identity_mismatch 82% -----|  alerta en pantalla
 |--- user.decision: handoff_to_agent --------->|
 |<-- call.phase: agent_retaken ----------------|  el mismo agente sigue con el estafador
 |<-- call.phase: ended ------------------------|
```

## Prototipo de llamadas de operadora

El flujo siguiente pertenece al prototipo anterior. Su modo demo usa eventos locales; para audio
de operadora real requiere un puente externo. El nuevo canal de micrófono no usa `caller` ni
necesita configurar manualmente una URL WebSocket. El flujo objetivo de la sección anterior lo extiende.

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
| `block`    | El usuario bloquea el número; el mismo agente retiene la llamada si sigue activa. |
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
 |--- user.decision: block ------------->|  → el mismo agente sigue la conversación y registra evidencia
```

Ejemplo completo de esta secuencia (los mismos eventos que usa el modo demo):
`app/src/main/java/com/tiskz/app/analysis/DemoScripts.kt`.

### Cierre del agente y fragmento telefónico (APK 0.4.6)

La orden autenticada `call.hangup_requested` admite `reason:agent_ended` además de `risk_detected`. El motivo de cierre del agente no modifica el riesgo y admite revisión 0 (antes del primer turno). Se vincula a la misma sesión/llamada, requiere request_id válido y solo se consume una vez. La conferencia se cierra con ambas patas. Un `call.ended` aislado no ordena desconectar.

El monitor envía `consentimiento_conservacion:true` únicamente con la opción de evidencia activada. El servidor conserva hasta 10 s del canal customer ante riesgo principal, no por la comparación experimental. `audio.capture_result` indica `saving`, `saved` con `fragment_id`, o `failed`. El guardado puede completarse después de colgar y quedar confirmado en el panel, aunque el monitor del celular ya se haya cerrado. No hay reconocimiento de identidad por voz: el WAV queda pendiente de revisión.


### Bloqueo preventivo por coincidencia vocal (APK 0.4.7)

La conferencia automática anuncia `voice_block_protocol: 1` en `POST /v1/phone/monitor`, además de los permisos vigentes de análisis, huella de voz y corte automático. Una versión anterior o un monitor manual no anuncia esta capacidad: recibe el aviso vocal, pero no la nueva orden de corte por coincidencia vocal.

Con una similitud fuerte (≥ 0,80 y al menos un caso), el servidor publica primero `voice.match` y después:

```json
{
  "type": "call.hangup_requested",
  "session_id": "11111111-2222-4333-8444-555555555555",
  "call_id": "11111111-2222-4333-8444-555555555555",
  "request_id": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
  "revision": 0,
  "reason": "voice_match",
  "block_number": true,
  "identity_verified": false
}
```

La revisión puede ser 0 si todavía no hay texto. Se conservan la validación de sesión/llamada, UUID y consumo único. La APK añade a su lista local únicamente el número visible de la llamada entrante original obtenido de Telecom; ignora teléfonos en el evento y nunca bloquea el destino del agente. Si el número está oculto o no es válido, solicita el corte sin añadir un bloqueo. Cierra ambas patas de la conferencia.

El aviso de coincidencia tiene prioridad visual sobre «Ya puedes entrar», incluso después de que el usuario se una. La similitud vocal aparece separada del porcentaje de riesgo del contenido; no modifica ese porcentaje ni confirma una identidad o un delito. Una coincidencia fuerte reutiliza la referencia existente, sin registrar otra denuncia ni otra huella como confirmación. `voice.collecting` con `status: matched_existing` cancela una recopilación en curso.

Para actualizar desde la 0.4.6 sin borrar datos, compilar la 0.4.7 con la misma clave de firma que la APK instalada. Una clave debug de otra laptop no permite actualizarla: Android devuelve `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. No desinstalar para resolverlo si se deben conservar los datos.

La APK 0.4.8 conserva además el motivo de la orden autenticada antes de desconectar Telecom. Si fue `voice_match`, muestra «Cortada por coincidencia de voz» al terminar, en Recientes, en el detalle y en un aviso posterior al corte. El detalle separa la similitud vocal del riesgo del contenido y especifica si se añadió el número a la lista local. Recibir solo `voice.match` no permite afirmar que hubo corte o bloqueo. Los registros anteriores, sin motivo guardado, se leen sin atribuirles una causa retroactivamente.


### Tipos preventivos y fuentes (APK 0.4.9)

`assessment.update.assessment.modalidades[]` identifica conductas o hipótesis con `categoria`, `interpretacion`, `evidencias` (citas ancladas a `evidencia_textual`) y `fuentes` (IDs editoriales). La APK admite el catálogo de categorías del servidor, evita citas ajenas al evento y enlaza únicamente referencias fijas: `lazo_rivas_2022`, `sedano_2024`, `bde_vishing_2020`. Un estudio de caso y una revisión descriptiva orientan las categorías; la guía oficial las complementa. Ninguna fuente certifica la clasificación de una llamada.

La pantalla de llamada y su detalle muestran el tipo provisional, sus citas y las fuentes. `risk.update.scam_type` contiene la primera hipótesis disponible de esa revisión o null; un evento nuevo con null retira una clasificación anterior. Una alerta no espera a que llegue el tipo, y sin comprensión suficiente se muestra «Tipo todavía sin determinar». La clasificación no cambia el porcentaje, los permisos ni la política de corte.

`voice.match.scam_type` es un antecedente de reportes previos. Se muestra como tal, separado del análisis del contenido actual; no identifica al hablante ni atribuye esa modalidad a la llamada nueva. Si el campo falta, no se inventa. El historial conserva modalidades y referencias de la revisión vigente, sin rellenar retroactivamente registros anteriores.
