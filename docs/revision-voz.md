# Revisión de voz y comprensión

Se utilizó `review-agent` en una revisión independiente de solo lectura del cambio de backend y del diff completo de `mobile-repo` contra `main`. El revisor no editó archivos ni creó commits.

Se corrigieron cuatro hallazgos P2:

1. **Negaciones recortadas:** una subcadena como «necesito pagos» podía extraerse de «No necesito pagos». El servidor exige oraciones completas, conserva su contexto literal y rechaza recortes. La narrativa libre del modelo se descarta.
2. **Cierre remoto:** el listener ahora responde a `onClosing` y termina la sesión sin esperar el ping ni depender de `call.ended`. Una prueba real con OkHttp y MockWebServer cubre el cierre.
3. **Orden de turnos:** el backend retransmite `sequence`; el parser y el estado móvil la conservan y ordenan la transcripción aunque los finales lleguen invertidos. Hay regresiones en ambos repositorios.

4. **Separadores de la prueba:** la verificación literal nativa usa el mismo salto de línea que el backend entre turnos, aceptando citas válidas que abarcan varios fragmentos.

También se amplió la pausa nativa a 1,2 segundos, se eliminó el corte de 180 ms en frases largas y se sincronizó la prueba con el final del WAV. La regresión cubre que una pausa breve no separe el final de una frase larga.

Revisión final del código y de los APK por arquitectura: **No findings.**

Validación local: 23 pruebas Node y 15 pruebas Android unitarias aprobadas; APK principal y de instrumentación compilados, lint sin errores. El runtime minificado se cargó y verificó antes del despliegue.

La verificación del audio nativo y sus resultados reales se guardan en [verificacion-voz.json](verificacion-voz.json). La revisión de código no sustituye esas pruebas. No se automatizó la navegación de la interfaz ni se conectó un teléfono físico. La transcripción puede equivocarse; la comprensión conserva citas literales y no atribuye identidades.

Durante la prueba, las métricas de amplitud mostraron que una inyección en ráfaga de un WAV de 6,7 segundos solo capturaba unos 2,1 segundos de voz. El inyector del micrófono virtual se cambió a `MODE_REAL_TIME` con paquetes de 20 ms acompasados; la amenaza volvió a llegar completa. Este ajuste afecta al entorno de prueba. Los intentos con captura incompleta o proceso interrumpido no se contaron como aprobados.
