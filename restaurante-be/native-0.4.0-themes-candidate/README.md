# Restaurante Be · Temas 0.4.0 · candidato QA

Cuatro APK Android nativas: Hostess, Mesero, Cocina y Valet. Incorporan cinco apariencias —Aurora, Océano, Jardín, Atardecer y Medianoche—, emojis junto a las etiquetas y transiciones breves. La preferencia de apariencia se guarda en cada aplicación. El movimiento reducido respeta la elección del usuario y la configuración del sistema. Los logos originales se conservan.

**APK de depuración firmadas para QA. No son una versión estable ni están certificadas para producción.** Actualizan la línea de candidatos con identificadores `mx.behost.restaurantenative.candidate.*`; las aplicaciones oficiales existentes permanecen intactas. No utilizar con datos reales.

Están desarrolladas con Java y Android Views. Requieren el adaptador nativo privado de Restaurante Be 6.7.6 y el coordinador autorizado. Caja y Administración continúan siendo web. Este directorio contiene únicamente APK, información de distribución y sus sumas de comprobación; no publica el backend ni las fuentes privadas del sistema.

La compilación, lint, manifiestos, firmas y SHA-256 se verifican en las evidencias de la entrega. Las pruebas instrumentadas se compilan; su ejecución en dispositivos, TalkBack, tamaño de letra al 200 %, geometría real y funcionamiento físico con varios equipos Wi-Fi siguen pendientes. Las comprobaciones estáticas no sustituyen esas pruebas.

Persisten los límites operativos del candidato anterior: coordinador en teléfono, relevo entre bases independientes, sincronización de eventos hacia la nube, notificaciones en segundo plano, lector QR nativo y las pruebas prolongadas sin WAN, terminal, impresión y hosting. Sin coordinador disponible se guardan borradores; una pantalla guardada no acredita confirmación de pedidos ni cobros.

Android mínimo API 24; objetivo API 35. Comprueba cada archivo con `SHA256SUMS.txt` y `manifest.json`. La rama de esta publicación es exclusivamente de candidato QA y no cambia la distribución estable.
