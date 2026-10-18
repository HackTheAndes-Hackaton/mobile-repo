# Pendientes de PhoneWard

Estado al 18 de octubre de 2026. Detalle técnico del servidor de voz: [agents — continuidad del equipo](https://github.com/HackTheAndes-Hackaton/agents/blob/main/docs/CONTINUIDAD_EQUIPO.md).

## Ya resuelto (no volver a abrir)

- `call.ready_to_answer` integrado: revisión actual en `observar`, sin alerta. No verifica identidad. Véase [CONTRATO_BACKEND.md](CONTRATO_BACKEND.md).
- Señales peruanas (Yape, BCP, SUNAT, familiaridad, relato frente a amenaza) en comprensión y detección del servidor.
- El número de Vapi usa la cascada Soniox → GPT-4.1 mini → Eleven Flash (voz Sarah).
- Pantalla de llamada compacta: un solo panel con porcentaje y aviso, que se despliega al tocarlo; la conversación ocupa el resto.
- HyperOS: mientras suena se abre una ventana invisible de PhoneWard para que el sistema no pase la llamada a su propio teléfono (por ejemplo, después de «Cerrar todo» en recientes). Solo se ve el aviso de llamada. **Falta confirmarlo con una llamada real.**
- Notificaciones flotantes activadas en el Xiaomi de la demo.
- `docs/PROBLEMA_PERU.md` está subido.

## 1. Pruebas de punta a punta que faltan

- [ ] **HyperOS:** «Cerrar todo» en recientes → recibir una llamada → debe verse el aviso de PhoneWard, no el del teléfono del sistema.
- [ ] **Llamada real completa con la cascada:** amenaza explícita → alerta → audio guardado → corte de la conferencia en el teléfono. Solo se probó con audio sintético.
- [ ] **Guardar una llamada real contra la base de producción** y ver la reputación del número en la app (requiere la clave del punto 2).
- [ ] **Flujo nuevo con llamada real:** aviso de entrada → «espacio seguro» → entrar → alerta si piden un código.

## 2. Configuración manual

- [ ] Pegar la API key de producción en la app: **Ajustes → Historial en el panel**.

## 3. Agente de voz (requiere acceso a Vapi)

- [ ] **Aplicar y medir** los ajustes preparados en `agents/config/vapi-cascade-assistant.json`: ejemplos de estilo en el prompt, `maxTokens` 80, `maxEndpointDelayMs` 500 y `optimizeStreamingLatency` 3. Objetivo: respuesta en menos de 1,5 s sin cortar a quien habla.
- [ ] **Naturalidad:** confirmar en llamadas reales que ya no repite el recado ni cierra con preguntas innecesarias.
- [ ] **`maxDurationSeconds`** (hoy 180 s): decidir si se amplía para la demo.
- [ ] **Número de Vapi peruano** (hoy +1 209…): cada llamada es larga distancia internacional y el audio llega peor.

## 4. Funciones planeadas que aún no existen

- [ ] Comparación de voces entre llamadas (motor de embeddings y revisión de referencias).
- [ ] Desvío desde Entel.
- [ ] Exportar reportes a PDF desde el panel.

## 5. Documentación

- [ ] Sección «Validación con la PNP» con los aprendizajes de la entrevista (falta el contenido de la entrevista).

## Simulación opcional

La simulación permite revisar la interfaz; no valida audio real, guardado de voz ni conferencia de la operadora:

```bash
adb shell am start -n com.tiskz.app/.MainActivity --es simulate safe
```

Con `--es simulate scam` se ve la estafa.
