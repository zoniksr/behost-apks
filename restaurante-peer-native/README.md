# Restaurante Be · Android nativo + nodo temporal sin servidor dedicado

**Estado: prototipo QA de laboratorio, 13 noviembre 2026. NO es apto para restaurantes en producción.**

## Qué funciona en esta compilación
- Cuatro APK Android **genuinamente nativas**, con Android Views y sin WebView: Hostess, Mesero, Cocina, Valet.
- Cada APK puede alojar temporalmente el **nodo coordinador TLS** mientras permanece abierta, con la pantalla encendida.
- Comunicación por Wi-Fi del establecimiento sin Internet, introduciendo IPv4 LAN, código efímero y huella SHA-256 del certificado del nodo.
- TLS 1.2+; clave privada generada y guardada en Android Keystore, no incluida en el código ni en GitHub.
- Host centraliza **datos FICTICIOS** de mesas, visitas, comandas y autos, aplicando SQLite transaccional, UUID idempotente, revisiones de mesa y controles simples por rol.
- Sin nodo: cada APK guarda **borradores cifrados AES-GCM**; se reconcilian manualmente y nunca se muestran como entregados a Cocina.
- Se instalan junto a las versiones existentes, con identificadores QA diferentes.

## Qué NO funciona todavía
- **No** se conecta al PHP/MySQL real de Restaurante Be, ni mueve datos productivos.
- **No** incluye venta/facturación real, inventario, pago, terminal, propina, sincronización nube ni impresión.
- **No** hay descubrimiento automático de nodos ni traspaso seguro de liderazgo. No iniciar 2 nodos sobre datos reales.
- **No** hay aprobación/autenticación persistente de identidades y permisos por usuario/empleado. El PIN del nodo es sólo para demostración; los roles transportados pueden falsificarse.
- **No** hay servicio en primer plano: al cerrar/suspender el anfitrión, desaparece el nodo.
- **No** se han realizado pruebas físicas en teléfonos o pruebas de integración con Caja/Administración.
- Caja y Administración siguen siendo web, y **un navegador no puede actuar como servidor TCP sin un auxiliar nativo**. Sus capacidades de caché deben integrarse con el backend antes de permitir cambios offline.

## Puesta en marcha QA
1. Instala las cuatro APK de la publicación de pruebas. No desinstales las APK oficiales.
2. Conecta los teléfonos a la **misma Wi-Fi privada del personal**. No uses Wi-Fi de invitados con aislamiento entre dispositivos.
3. En cualquier APK toca **Este dispositivo será el nodo** y conserva la pantalla abierta.
4. En los demás equipos captura **IP del nodo**, su **huella TLS SHA-256 de 64 caracteres** comprobándola con la pantalla del nodo, y el código de 20 caracteres.
5. Toca **Vincular con nodo**. Un pedido se confirma sólo tras recibir acuse del nodo.
6. Simula desconectar Internet, pero mantén encendido el Wi-Fi. Los teléfonos siguen comunicándose por LAN.
7. Simula apagar el nodo: los cambios en los clientes se guardan como borradores. Al restaurarlo, usa **Reconciliar borradores** con el mismo UUID.
8. Si el nodo anfitrión se pierde definitivamente, detén las escrituras; **no** promociones automáticamente otro equipo hasta contar con un protocolo de traspaso/conciliación firmado.

## Seguridad
Todo es material QA ficticio. No capturar clientes reales, placas reales, credenciales de producción, tarjetas, CFDI ni datos de contacto. Los mensajes usan TLS con certificado pinneado; se cifran los snapshots y los borradores con Android Keystore. La app no solicita habilitar tráfico HTTP sin cifrar.

En Android 17, al migrar el target SDK 37 se requiere el permiso runtime `ACCESS_LOCAL_NETWORK`; este laboratorio se compila con target 35. En versiones antiguas de Android pueden faltar TLS 1.3, por lo que se negocia 1.2 cuando sea necesario.

## Para la versión realmente operativa
Es necesario integrar el servidor web existente con el **registro único de eventos**, autenticación por rol, sincronización del inventario, control de visitas/propinas, backups, validación de schema, cuotas, auditoría y migraciones. El nodo de Caja/Admin necesitaría una aplicación auxiliar nativa de escritorio, no un service worker.

## Compilación
Gradle AGP 8.8.2, Java 17, minSdk 24, targetSdk 35, build debug (firma de prueba). 
`gradle :app:assembleDebug`. La acción de GitHub comprueba que hay cuatro APK, sus identificadores de paquete, integridad de firmas y que el código no importa WebView.
