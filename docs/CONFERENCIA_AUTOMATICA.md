# Conferencia automática con marin

Tiskz integra en la aplicación principal el flujo del prototipo local `tiskz-call-probe`:
recibir una llamada de la SIM, contestar, ponerla en espera, llamar al agente y solicitar
una conferencia. No captura ni inyecta el audio interno de la SIM. El audio llega a Vapi
como el de otro participante telefónico.

El interruptor y la confirmación de costos están **apagados en una instalación nueva**.
No se activa un desvío, se compra un número ni se llama durante la configuración.

## Preparación una sola vez

1. Instalar la APK principal de Tiskz. En Inicio pulsar **Configurar protección automática**.
2. Elegir Tiskz como aplicación Teléfono y conceder `CALL_PHONE` y `READ_PHONE_STATE`.
3. Seleccionar la SIM receptora; si hay una sola se preselecciona, sin autorizar llamadas.
4. Revisar la operadora detectada, el número del agente y la marcación calculada.
5. Confirmar con esa operadora que **esa SIM, ese destino y la conferencia** no añaden cargos.
   Solo después marcar la casilla correspondiente. Los créditos de IA no pagan a la operadora.
6. Introducir una vez el código privado del equipo. Se guarda cifrado con AES-GCM y Android
   KeyStore, asociado al canal HTTPS; nunca se incluye en el APK ni se guarda en texto plano.
7. Activar la protección automática, autorizando las llamadas y el análisis. No se realiza
   una llamada para probar o habilitar el interruptor.

El canal y el número actual de Vapi están preconfigurados. No es necesario conocer el prefijo
LDI ni mantener conectado el Mac. Cambiar el número no configura el agente ni sus webhooks:
el número elegido debe atenderlo el asistente marin conectado al mismo backend de alertas.

La configuración persiste para las siguientes llamadas. Cambiar la SIM o el destino elimina
la confirmación de costos y desactiva el modo; cambiar el canal HTTPS lo desactiva y exige
configurar acceso para ese canal. En cada llamada se vuelve a comprobar la SIM, la operadora,
el destino confirmado, el rol y los permisos. Una portabilidad o un cambio de perfil no reutiliza
la autorización anterior. No se activa con SIM desconocida, roaming o país de red distinto
de Perú, ni si falta información de la red.

## Detección y marcación

Se utiliza `TelephonyManager.createForPhoneAccountHandle` para la **SIM seleccionada**, no
la SIM predeterminada ni el prefijo del número del usuario. MCC/MNC y, si está disponible,
el identificador de operadora de Android deben concordar. No se adivina la operadora de un MVNO.

| Operadora | MCC/MNC | Prefijo LDI | Destino de prueba +12098195098 |
| --- | --- | --- | --- |
| Entel | 71617 | 1990 | 19900012098195098 |
| Claro | 71610 | 1912 | 19120012098195098 |
| Movistar | 71606 | 1911 | 19110012098195098 |
| Bitel | 71615 | 1968 | 19680012098195098 |

El número se introduce en formato internacional `+…`. Para destinos peruanos `+51` no se añade
un prefijo LDI. Se rechazan secuencias MMI, pausas, DTMF y números cortos. No hay fallback a
otra operadora, otro prefijo ni otra SIM si la llamada falla.

Fuentes de marcación: [Entel](https://www.entel.pe/personas/servicios/larga-distancia-internacional/llamada-por-llamada),
[Claro](https://www.claro.com.pe/llamada-internacional-1912/),
[Movistar](https://www.movistar.com.pe/movil/prepago/prepago-paquetes),
[Bitel](https://bitel.com.pe/faq-bitel-fono/zonas-ldi.html).
Identificadores de SIM: [base de operadoras AOSP](https://github.com/aosp-mirror/platform_packages_providers_telephonyprovider/blob/main/assets/latest_carrier_id/carrier_list.textpb).
Estas referencias acreditan la marcación, no la gratuidad de un plan ni la disponibilidad de conferencia.

## Qué hace al entrar una llamada

1. Comprueba autorización para la cuenta de teléfono entrante. Conserva las reglas existentes de bloqueo.
2. Conecta el monitor autenticado de Maritime **antes de contestar**. Si falla, deja sonar para
   que el titular conteste manualmente; no llama a Vapi.
3. Solicita contestar y espera `ACTIVE` de Android. Solicita espera y espera `HOLDING`.
4. Marca una sola vez el destino calculado, con `EXTRA_PHONE_ACCOUNT_HANDLE` de la misma SIM.
5. Reconoce la llamada creada por un identificador propio, cuenta, destino y dirección saliente.
6. Espera `ACTIVE` de Vapi y que `getConferenceableCalls` incluya a la otra llamada.
7. Solicita `Call.conference`. Solo confirma la conferencia con un padre `ACTIVE` que contenga
   ambas llamadas. Los eventos repetidos no repiten la marcación ni la solicitud de unión.
8. Muestra las transcripciones, resumen de comprensión y alerta real en la pantalla de llamada.
   **Ver progreso de conexión** distingue hitos solicitados de los confirmados y muestra el
   código/descripcion de desconexión cuando Android lo facilita.

El monitor pertenece a la sesión de telefonía, no a la pantalla: cambiar de Activity no lo
desconecta como ocurre con el monitor manual de Voz en vivo. Se cierra al finalizar/cancelar
la automatización o al perder el servicio de llamadas. No se solicita el micrófono para este modo.
El texto de la conferencia no se guarda en el historial local; solo se registra metadata de llamada.
Esto no determina la conservación que hagan los proveedores.

## Primera prueba física

- Usar una sola conferencia y ninguna otra llamada directa al número compartido de Vapi a la vez.
- Tras la confirmación de costos, pedir a una persona informada que llame al número habitual
  de la SIM seleccionada. La app intenta contestar y unir automáticamente.
- Comprobar los hitos en Tiskz y **el audio desde ambos extremos**. La confirmación de Android
  por sí sola no demuestra que marin sea audible para quien llamó.
- El saludo de Vapi puede ocurrir mientras la llamada original sigue en espera; después de
  unir, pedir a marin que vuelva a presentarse y aceptar continuar con el análisis.
- Representar un caso normal y uno de amenaza ficticia; observar la alerta y las citas.
- **Colgar** termina las patas propias, incluida la conferencia si existe. No basta cerrar la app.

Hay límites de 12 s para conectar alertas, 8 s para contestar/espera, 25 s para marcar, 10 s
para obtener capacidad de unión y 10 s para confirmarla. Una vez unida, marin no tiene límite
de tiempo: escucha y analiza hasta que se cuelgue, también después de que entre el titular.

Mientras marin atiende, el titular **no está en la llamada**: Tiskz silencia el micrófono, baja
el volumen de la llamada y solo muestra **Cancelar** y **Entrar**. *Entrar* se habilita cuando el
backend envía `call.ready_to_answer` o `agent.status: safe` (espacio seguro), o si marin no logró
unirse. Si hay alerta de estafa se muestra **Bloquear** en su lugar. Al entrar se reactiva el
micrófono y se restaura el volumen. Si la red rechaza una acción o se pierde el monitor, no hay remarcación;
se intenta cerrar solo el agente y recuperar la llamada original. **Retomar llamada** permite
pedir salir de espera. La recuperación depende de la operadora.

Las voces de ambos humanos llegan mezcladas por la conferencia: se etiquetan como participantes,
sin atribuir identidades. Una alerta `false` no certifica seguridad. Un error o falta de análisis
se presenta como desconocido, nunca como `false`.

## Alcance de las verificaciones

Las pruebas unitarias usan efectos de telefonía falsos; no consumen minutos. Las instrumentadas
usan preferencias aisladas y KeyStore en un emulador, sin llamadas. Comprueban valores iniciales,
invalidación de la autorización y persistencia cifrada.

El informe aportado del prototipo independiente describe contestación automática en un Samsung
Galaxy A55, pero **no una conferencia completa con Vapi ni audio remoto de marin confirmado**.
Ese resultado no se presenta como prueba física de esta nueva APK principal. La conferencia,
el audio, la continuidad del monitor bajo restricciones del equipo y los costos deben verificarse
en el teléfono y la línea reales. Instalar una APK no omite el rol, los permisos ni la red móvil.

Referencias Android: [InCallService](https://developer.android.com/reference/android/telecom/InCallService),
[Call](https://developer.android.com/reference/android/telecom/Call),
[TelephonyManager](https://developer.android.com/reference/android/telephony/TelephonyManager).
