# Probar conversación con marin

En **Voz en vivo**, activa «El asistente escucha y responde con voz de IA». Usa el mismo canal HTTPS y código privado del equipo de las pruebas anteriores:

`https://api.maritime.sh/a/ebf0eb81-42c0-4437-a044-1af84af1b755`

1. Autoriza el envío del audio, su análisis y las respuestas de voz de IA.
2. Pulsa **Conversar**, concede micrófono y espera el saludo de Tiskz. Usa el volumen de llamadas/multimedia del dispositivo; prueba auriculares si hay eco.
3. Habla en español. La pantalla separa los subtítulos del interlocutor y del asistente. Los dos agentes analizan únicamente al interlocutor y muestran alerta y comprensión provisionales.
4. Pulsa **Detener** para cerrar. Salir de la pantalla o enviar la app al fondo también detiene la sesión. No se guarda audio ni texto en disco.

Para usar el modo anterior de solo transcripción y análisis, desactiva el interruptor y vuelve a aceptar el consentimiento correspondiente.

## Alcance

Es una conversación por internet dentro de la app. No captura ni inyecta audio de llamadas de operadora y no activa un desvío del número SIM. Contestar ese número requiere un número receptor y un puente de telefonía. El beneficio Zavu aún necesita verificar Voice Agents/número y compatibilidad de audio externo; no se ha contratado telefonía ni se ha confirmado voz marin a través de Zavu.

Se usa `gpt-live-1` con `marin` a través del servidor Maritime existente, sin una clave de OpenAI en el APK. Los créditos de alojamiento, IA y telefonía no deben confundirse. La conversión de los 100 créditos del evento a minutos Live no está confirmada.

## Contrato

Crear sala con `mode: "assistant"` y `consentimiento_voz_ia:true`, además del consentimiento de análisis y el encabezado `X-Voice-Token`. El backend conserva la API key y configura voz, instrucciones y delegación. La oferta usa el ticket temporal de productor.

En `oai-events`, Android espera `session.started`, retransmite captions/delegaciones/cierre mediante `live.event` y permite únicamente `session.thinking.append` desde el backend. El flujo de audio es continuo; no manda commits de la API Realtime anterior. Los subtítulos son parciales y se acumulan literalmente por interlocutor, sin inventar límites finales de turno. La comprensión selecciona ids de fragmentos literales del servidor y no atribuye identidades.

Al terminar se retira el emisor, se envía `session.close` y se espera hasta dos segundos por confirmación antes de liberar todos los recursos. La limpieza HTTP de sala es adicional. El proxy no confirmó correctamente `/hangup` en las pruebas; solo `session.closed` confirma el cierre y sus segundos de uso. El test nativo exige esa confirmación y la liberación del micrófono.

## Validación reproducible

`NativeLiveIntegrationTest` requiere un emulador aislado del audio del anfitrión y un inyector de WAV ficticio por gRPC. El código se suministra temporalmente en `files/voice-test-code`, nunca por argumentos ni en el APK. La prueba exige audio recibido con energía positiva, ambos agentes, evidencia literal, ausencia de identidades inventadas, cero commits antiguos, cierre confirmado y micrófono liberado.

Casos sintéticos: amenaza con exigencia de transferencia (TRUE) y reunión con negación explícita de pagos/datos (FALSE). Los resultados de ejecución están en `docs/verificacion-marin.json`.
