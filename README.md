# Tiskz

App Android que reemplaza la app de teléfono (como Truecaller). Cuando entra una llamada, un agente
de IA atiende primero, y Tiskz muestra en vivo **el porcentaje de riesgo de estafa**, la transcripción
y la coincidencia de huella de voz. Si la llamada es segura, el usuario contesta; si es estafa, la bloquea.

> La app es solo la interfaz. La voz, la transcripción y el análisis los hacen agentes externos;
> el formato de los mensajes está en [`docs/CONTRATO_BACKEND.md`](docs/CONTRATO_BACKEND.md).
>
> Análisis completo, estado del arte y flujo del proyecto: [`docs/ANALISIS_Y_FLUJO.md`](docs/ANALISIS_Y_FLUJO.md).

## Requisitos

- Android Studio (trae el JDK 21 que usa Gradle).
- Teléfono o emulador con **Android 10 (API 29) o superior**.

## Correr

1. Abrir la carpeta en Android Studio y esperar el *Gradle sync*.
2. Run ▶ sobre un teléfono o emulador.

Por línea de comandos (Windows, Git Bash):

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew assembleDebug        # APK en app/build/outputs/apk/debug/
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
Por defecto apunta a `10.0.2.2:8000`, que es el localhost de la PC visto desde el emulador.

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
└── util/       Rol de app de teléfono, formato de números y fechas
```

## Limitaciones conocidas (MVP)

- **El audio no sale del teléfono.** Android no deja que una app lea el audio de una llamada del
  operador. El análisis depende de que la llamada pase por el backend (desvío de llamadas).
- Una llamada a la vez: una segunda llamada entrante se rechaza.
- El historial y los bloqueos son locales al teléfono; aún no se sincronizan con el backend.
- Sin cuenta de usuario todavía.
