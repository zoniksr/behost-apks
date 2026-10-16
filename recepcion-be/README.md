# Recepción Be para Android

**Recepción Be** es una aplicación de asistencia telefónica para profesionales y negocios. Permite configurar una recepción conversacional, atención por voz en español de México y funciones de agenda desde un teléfono Android compatible.

## Estado de pruebas

**Versión preparada:** v1.9.62 (versionCode 94).

La distribución pública se realiza mediante **GitHub Releases** de este repositorio. Esta versión debe identificarse como **prelanzamiento / pruebas**, ya que la recepción a través del número celular del profesional depende del servicio de desvío de su operadora y todavía requiere validación real de extremo a extremo.

## Funciones

- Asistente Sofía con selección de voz femenina en español de México según disponibilidad del motor de voz instalado en Android.
- Reconocimiento de voz en español y tratamiento de ruido ambiental.
- Conversación para dudas generales, horarios, servicios y solicitudes de cita.
- Integración de telefonía mediante una cuenta SIP configurada por el usuario.
- Pantalla que distingue conexión SIP, solicitud de desvío y última llamada entregada.

## Antes de usar

La aplicación requiere configuración y permisos adecuados. El **desvío de llamadas lo presta la operadora móvil**: ninguna pantalla de la app puede confirmar su activación únicamente por abrir el marcador o mostrar un código. Debe verificarse con una llamada real.

La calidad de la voz sintetizada y del reconocimiento depende del teléfono, motores instalados, red y condiciones acústicas. Las versiones de prueba no garantizan la atención de todas las llamadas.

## Descarga

Consulta [Releases](https://github.com/zoniksr/behost-apks/releases) y descarga el archivo APK de **Recepción Be** cuando aparezca publicado con la versión correspondiente.

**Seguridad:** nunca publiques contraseñas SIP, claves de firma, archivos keystore ni configuraciones privadas del profesional. El código fuente no forma parte de esta distribución binaria.
