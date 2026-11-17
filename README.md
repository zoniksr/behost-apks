# APK conservadas para compatibilidad

Estos seis binarios originales se conservan en GitHub para retirar las APK del Implementador BeHost y sus paquetes internos. No fueron recompilados, modificados ni firmados nuevamente. La migración de distribución no certifica su operación ni los convierte en versiones nuevas.

Las descargas de BeHost deben enlazar al archivo de esta revisión mediante un commit fijo y conservar la versión, el SHA-256, los bytes y el canal de cada producto. No se incluye código web propietario, datos de clientes ni claves privadas. `distribution.json` es un inventario de integridad; no es un catálogo firmado de actualizaciones automáticas.

| Aplicación | Versión | Paquete Android | Archivo |
|---|---|---|---|
| Comedor Be Cocina v1.2.25.apk | 1.2.25 | `mx.behost.comedor.cocina` | [Descargar](comedor-be/1.2.25/Comedor_Be_Cocina_v1.2.25.apk) |
| Comedor Be Ventas v1.2.25.apk | 1.2.25 | `mx.behost.comedor.ventas` | [Descargar](comedor-be/1.2.25/Comedor_Be_Ventas_v1.2.25.apk) |
| Evento Be Acceso v1.0.4.apk | 1.0.4 | `mx.behost.eventobeacceso` | [Descargar](evento-be/1.0.4/Evento_Be_Acceso_v1.0.4.apk) |
| Reloj Checador Be Trabajador v1.1.31.apk | 1.1.31 | `mx.behost.relojtrabajador` | [Descargar](reloj-checador-be/1.1.31/Reloj_Checador_Be_Trabajador_v1.1.31.apk) |
| Reloj Checador Be Trabajador v1.1.35.apk | 1.1.35 | `mx.behost.relojtrabajador` | [Descargar](reloj-checador-be/1.1.35/Reloj_Checador_Be_Trabajador_v1.1.35.apk) |
| Reloj Checador Be Trabajador v1.1.39 PRUEBAS.apk | 1.1.39-pruebas | `mx.behost.relojtrabajador.dev` | [Descargar](reloj-checador-be/1.1.39-pruebas/Reloj_Checador_Be_Trabajador_v1.1.39_PRUEBAS.apk) |

Reloj 1.1.31 y 1.1.35 conservan su identidad original. Reloj 1.1.39-pruebas usa una identidad `.dev` y permanece separado. La publicación de Reloj 1.1.50 existente utiliza `.lab`, requiere otro backend y no debe reemplazar silenciosamente estos canales.

Verificación realizada: SHA-256 contra los archivos originales, lectura del manifiesto Android, firma APK con `apksigner`, CRC y búsqueda de patrones de credenciales en entradas descomprimidas. No sustituye pruebas físicas ni demuestra ausencia absoluta de cualquier secreto posible.

Las cuatro APK nativas de Restaurante Be y su catálogo firmado se conservan en sus ubicaciones existentes; esta rama no cambia ese canal.
