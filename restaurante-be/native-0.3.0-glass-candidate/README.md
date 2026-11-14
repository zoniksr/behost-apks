# Restaurante Be · Be Glass 0.3.0 · candidato QA

Cuatro APK nativas: Hostess, Mesero, Cocina y Valet. Nueva presentación inspirada en Liquid Glass: navegación por tareas, jerarquía clara, botones grandes y logos originales de Restaurante Be y BeHost. Caja y Administración continúan siendo web.

Compiladas desde Java y Android Views, sin WebView operativo. Requieren el adaptador nativo privado de Restaurante Be 6.7.6 y el coordinador autorizado. No contienen backend, credenciales, bases de datos ni llaves privadas.

**APK de depuración firmadas para QA; no son versión estable ni producción.** Conservan los identificadores de candidato 0.2.0 y actualizan sólo esa línea de pruebas. Las APK oficiales anteriores permanecen intactas. No utilizar con datos reales.

Se verificaron compilación de las cuatro aplicaciones y sus pruebas instrumentadas, lint, firmas, manifiestos y SHA-256. Las pruebas instrumentadas se compilaron, pero no se ejecutaron: no hubo dispositivos conectados. La geometría en teléfono, TalkBack, escala de fuente 200 % y el flujo Wi-Fi físico siguen pendientes. No se presentan imágenes simuladas como capturas Android reales.

Sin cambios en identidad de dispositivo, almacenamiento cifrado, TLS, firma de solicitudes ni lógica de sincronización. Etiquetas de tiempos del Mesero alineadas con la web: Primer tiempo, Segundo tiempo, Plato fuerte y Postre.

Persisten los pendientes del candidato anterior: coordinador en teléfono, relevo entre bases independientes, sincronización de eventos hacia nube, notificaciones en segundo plano, lector QR nativo, 24 horas sin WAN, terminal, impresión y hosting. Sin coordinador disponible se conservan borradores, sin simular confirmación.

Android mínimo API 24, objetivo API 35. Verifique los archivos contra SHA256SUMS.txt y manifest.json. Esta rama QA no modifica la distribución estable ni publica las fuentes privadas del sistema.

