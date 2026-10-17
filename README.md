# Tiskz

Tiskz es la app de teléfono que protege contra estafas telefónicas. Los contactos suenan normal; las
llamadas desconocidas las atiende primero un agente de IA, y Tiskz analiza **toda la llamada**, también
cuando el usuario ya está hablando.

## Cómo funciona

| Quién llama | Qué pasa |
|---|---|
| **Contacto guardado** | Suena normal. Sin filtro ni espera. |
| **Número desconocido u oculto** | Tiskz la rechaza en silencio y el operador la desvía (desvío "si está ocupado") al número de Tiskz. Un agente de IA contesta con un saludo neutro ("Aló, ¿de parte de quién?"). |
| **Estafa ya confirmada** | Va directo al agente. El usuario nunca se entera. |

- **Un solo agente.** No hay un modo honeypot aparte: el agente atiende igual a todos los desconocidos, sin
  distinguir de antemano entre una persona legítima y un estafador. Si resulta ser estafa, el mismo agente
  sigue la conversación, retiene al estafador y registra evidencia.
- **El agente no se delata, pero no suplanta al usuario.** No anuncia que es un filtro anti-estafas, nunca
  dice ser el dueño del teléfono y, si le preguntan directamente si es una máquina, lo reconoce.
- **Análisis continuo.** Si la llamada parece legítima, Tiskz llama al usuario ("Llamada verificada") y los
  conecta, pero **sigue escuchando**. Un estafador que se portó bien con el agente y cambia de rol con el
  usuario dispara una alerta con el motivo, por ejemplo: *"Dijo ser de Claro, ahora habla en nombre del BCP"*.
  El usuario puede colgar o pasarle la llamada al agente.
- **Base comunitaria.** Cada estafa alimenta una base de números y evidencia que, en el modelo B2G, valida la
  autoridad (en Perú, conectada a lo que OSIPTEL ya hace con RENTESEG).

Flujo completo, modelo B2G y referentes internacionales: [`docs/ANALISIS_Y_FLUJO.md`](docs/ANALISIS_Y_FLUJO.md).

**Estado:** la app (pantallas, contactos, modo demo) y el canal de voz WebRTC están hechos. El rechazo
silencioso, el desvío, el puente de telefonía, el agente de voz y la detección de incongruencias están pendientes.

---

App Android con un canal de micrófono WebRTC que envía audio para transcripción y muestra en vivo
el resultado de dos agentes: comprensión (citas literales y categorías para revisar, sin atribuir identidades) y detección (alerta booleana provisional).
El servicio está desplegado en Maritime. La interfaz de llamadas de operadora y sus escenarios de
demo siguen disponibles como prototipo separado.

Contrato de eventos: [`docs/CONTRATO_BACKEND.md`](docs/CONTRATO_BACKEND.md).
Diseño del proyecto: [`docs/ANALISIS_Y_FLUJO.md`](docs/ANALISIS_Y_FLUJO.md).

## Canal de voz real

1. En **Inicio → Abrir canal de voz**, comprobar la URL HTTPS del servicio.
2. Introducir el código privado de acceso facilitado por quien administra el backend.
3. Confirmar la autorización de análisis y pulsar **Iniciar micrófono**. Android pedirá acceso al micrófono.
4. Hablar y hacer una pausa: la transcripción, la alerta y el resumen se actualizan en la pantalla.
5. Pulsar **Detener** para cerrar. Salir de la pantalla o enviar la app al fondo también detiene el micrófono.

URL incluida por defecto:
`https://api.maritime.sh/a/ebf0eb81-42c0-4437-a044-1af84af1b755`.

La captura solicita 48 kHz. La comprensión acepta oraciones completas y genera descripciones controladas; no convierte nombres mencionados o palabras dudosas en identidades ni hablantes. La pantalla muestra también incertidumbres y limitaciones.
Los fragmentos se cierran tras 1,2 segundos de pausa, dando margen a la entrega del audio antes del análisis. La alerta llega después de esa pausa y del procesamiento de los modelos.

El código se mantiene solo en memoria durante la pantalla; no se incluye en el APK ni se guarda en
ajustes. El audio se transmite al proveedor de transcripción por WebRTC y el texto final se envía
al backend para analizarlo. Esta pantalla no guarda audio ni transcripciones en el historial local.
La alerta `false` significa que el detector no activó una alerta con el contexto recibido; no certifica seguridad.
Los errores se muestran explícitamente y no se convierten en `false`.

Este canal captura **el micrófono con autorización**, no el audio interno de llamadas de operadora.
El modo demo de llamadas no afecta a esta pantalla: el canal de voz siempre utiliza el servicio real.

## Requisitos

- Android Studio (trae el JDK 21 que usa Gradle).
- Teléfono o emulador con **Android 10 (API 29) o superior**.

## Correr

1. Abrir la carpeta en Android Studio y esperar el *Gradle sync*.
2. Run ▶ sobre un teléfono o emulador.

Por línea de comandos (Windows, Git Bash):

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug        # APK por arquitectura y universal en app/build/outputs/apk/debug/
./gradlew installDebug         # instala en el dispositivo conectado
```

## Configurar backend y agentes

En `local.properties` (no se sube a git):

```properties
tiskz.wsUrl=wss://agentes.midominio.com/ws/calls
tiskz.backendUrl=https://api.midominio.com
tiskz.demoMode=false
```

También se pueden cambiar en **Ajustes** dentro de la app, sin recompilar.
La URL HTTP del backend apunta por defecto a Maritime y se usa en el canal de voz. La URL WebSocket
de llamadas de operadora conserva `10.0.2.2:8000` para el prototipo anterior; no es necesaria para el canal de voz.

## Modo demo

Viene **activado**: las llamadas se analizan con guiones locales (`analysis/DemoScripts.kt`), sin backend.

- **Inicio → Probar Tiskz**: botones "Estafa" y "Llamada segura" que simulan una llamada entrante.
- **Llamada real en el emulador** (con Tiskz como app de teléfono):
  ```bash
  adb emu gsm call 987654321     # termina en impar → escenario estafa (modo Automático)
  adb emu gsm call 912345678     # termina en par   → escenario seguro
  ```

## Estructura

```
app/src/main/java/com/tiskz/app/
├── analysis/   Eventos de los agentes, sesión WebSocket y sesión de demo
├── call/       InCallService, filtro de llamadas, CallManager y pantalla de llamada
├── data/       Ajustes, historial y números bloqueados (SharedPreferences)
├── model/      Llamada, análisis, veredicto
├── ui/         Pantallas en Jetpack Compose (tema blanco y azul)
├── voice/      Sesión WebRTC, API de salas, pausas de voz y puente de transcripción
└── util/       Rol de app de teléfono, formato de números y fechas
```

## Limitaciones conocidas (MVP)

- El canal de micrófono requiere conexión a Internet, autorización y acceso al servicio. No captura
  llamadas de operadora; integrarlas requiere un proveedor o puente de telefonía separado.
- Una llamada a la vez: una segunda llamada entrante se rechaza.
- El historial y los bloqueos son locales al teléfono; aún no se sincronizan con el backend.
- Sin cuenta de usuario todavía.

## Validación

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Las pruebas unitarias cubren autenticación, rutas, cancelación, orden de eventos, reemplazo de
parciales, errores y pausas de voz. La prueba instrumentada opcional
`NativeVoiceIntegrationTest` requiere un emulador con micrófono virtual, WAV sintético y el código
en `files/voice-test-code` dentro del almacenamiento privado de la app. Se omite si falta ese archivo
y lo elimina al terminar. No utiliza el micrófono del equipo anfitrión ni incorpora secretos al APK.
El argumento de instrumentación `-e voiceScenario riesgo` o `-e voiceScenario legitimo` permite
ejecutar cada caso en un arranque separado del emulador cuando su controlador de audio lo requiere.
El inyector espera `ready:<escenario>` en `files/voice-test-phase`, entrega el WAV PCM16 mono a
48 kHz seguido de silencio y crea `files/voice-test-audio-complete` en ese mismo almacenamiento
privado al terminar. La prueba espera ese marcador y la última frase antes de detener la captura;
una alerta temprana por sí sola no termina el audio. No reutilices este mecanismo con audio real.
En el emulador de esta verificación se usa `MODE_REAL_TIME`, paquetes de 20 ms acompasados con
reloj monotónico, un segundo de silencio inicial y cuatro al final. La inyección en ráfaga produjo
capturas incompletas antes de WebRTC; el resultado de esos intentos no se contó como válido.

WebRTC Android: [SDK y versiones oficiales](https://github.com/webrtc-sdk/android).

Para teléfonos ARM64 y el emulador usado en las pruebas: `app-arm64-v8a-debug.apk`. Para otras arquitecturas se generan APK específicos y `app-universal-debug.apk`. `installDebug` selecciona la arquitectura del dispositivo conectado.
