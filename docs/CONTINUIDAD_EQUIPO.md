# Estado para continuar — 18 de octubre de 2026

El PR mobile #12 y el PR agents #9 están fusionados en `main`. APK 0.4.6, versionCode 10.

- Implementados: cierre de ambas partes de la conferencia por `risk_detected` o `agent_ended`, opción separada de conservación de voz y estados de guardado.
- Instalado en el Samsung de prueba; modo real, conferencia y conservación activados. Los costos ya fueron confirmados para esa SIM. La opción de conservación viene apagada en instalaciones nuevas.
- Verificados: 65 pruebas JVM, `assembleDebug`, `lintDebug`. La desconexión por `agent_ended` se observó en llamadas reales. No se confirmó todavía el recorrido completo alerta de riesgo → WAV guardado → corte durante una conferencia real.
- `call.ready_to_answer` ya está integrado. «Ya puedes entrar» informa el resultado de la revisión actual; no verifica identidad.
- Scribe compara transcripciones en paralelo. No compara identidades vocales ni controla el corte.
- La comparación de voces entre llamadas sigue pendiente de un motor de embeddings y revisión de referencias. No mostrarla como una función ya operativa.

El último intento se interrumpió para esperar al otro chat; su resultado final no quedó verificado. No hay cambios de código locales pendientes al entregar este estado.

Estado completo y criterios de la próxima prueba: [agents — continuidad del equipo](https://github.com/HackTheAndes-Hackaton/agents/blob/main/docs/CONTINUIDAD_EQUIPO.md). Contrato móvil: [CONTRATO_BACKEND.md](CONTRATO_BACKEND.md).

Para continuar: `git pull --ff-only origin main`. Para compilar y verificar: `./gradlew testDebugUnitTest assembleDebug lintDebug` con el JDK de Android Studio configurado. APK: `app/build/outputs/apk/debug/app-universal-debug.apk`. Actualizar con la misma firma para conservar datos. Las claves se configuran por el canal privado del equipo; no van en Git.
