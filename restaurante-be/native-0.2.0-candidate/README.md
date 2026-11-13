# Restaurante Be: cuatro APK nativas 0.2.0, candidato QA

Hostess, Mesero, Cocina y Valet. Caja y Administración permanecen web.

Estas APK se compilaron desde fuentes Java/Android Views, sin WebView operativo. Requieren el adaptador nativo candidato del sistema privado Restaurante Be 6.7.6 y un coordinador configurado por el administrador. No incluyen backend, credenciales, bases de datos ni llaves privadas.

**Son APK de depuración, firmadas para laboratorio, con identificadores independientes. No son una actualización oficial ni una versión estable.** No usar con datos reales. Una firma válida sólo verifica el binario: no acredita operación física, LAN de 24 horas ni producción.

Implementado: sesión real vinculada a dispositivo, firma P-256, TLS con huella y hostname, borradores SQLite cifrados mediante Keystore, UUID permanente, estados confirmados/rechazados/inciertos y pantallas por rol. El servidor conserva la autoridad sobre usuarios, permisos y negocio.

Pendiente: coordinador alojado en teléfono, relevo entre bases independientes, sincronización de eventos hacia nube, recepción en segundo plano, lector QR nativo, pruebas físicas Android 7 y moderno, 24 horas sin WAN, PAC/terminal/impresora y hosting BeHost. Al faltar el coordinador se conservan borradores; no se simula confirmación.

Android mínimo 7/API24; target35. Para futuras compilaciones target37 debe aplicarse el permiso de red local de Android17. Este paquete no acredita esas pruebas futuras.

Compruebe SHA-256 contra SHA256SUMS.txt y la identidad/firma en manifest.json. No sustituya las APK anteriores 2.0.18-qa1 ni Peer0.1.0. La publicación está en una rama QA separada y no modifica la distribución estable.

