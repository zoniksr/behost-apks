# Catálogo central de APK Restaurante Be

URL permanente: https://raw.githubusercontent.com/zoniksr/behost-apks/main/catalogs/restaurante-be.json

BeHost y Restaurante Be consultan el mismo catálogo firmado. Sus botones y QR
conservan rutas propias permanentes y redirigen a APK por commit inmutable. Una
publicación futura no requiere cambiar sus páginas ni recompilar el backend.
El catálogo contiene metadatos; ningún instalador web incluye o transmite APK.

## Canales y alcance

- `native_qa`: cuatro apps Android nativas candidatas; inicialmente 0.4.0, código4.
- `legacy_qa`: cuatro apps de pruebas anteriores; inicialmente 2.0.18-qa1, código22.
- `stable`: deshabilitado. No hay identidades de producción autorizadas.

Las familias tienen paquetes y certificados distintos: no se compara su número
de versión entre familias. Nunca se usa `releases/latest`, que puede corresponder
a otro producto. Una firma válida identifica al publicador; no prueba que las
apps hayan pasado pruebas físicas, de carga o de operación en producción.

## Publicar cuatro APK nuevas con un comando

Requisitos: Python3.11+, git autenticado con permiso de push y nombre/correo de
autor configurados, JDK17+, Android SDK Build Tools (`apksigner`, `aapt2`,
`zipalign`), OpenSSL CLI o PHP con OpenSSL. Clonar este repositorio y mantener el
checkout limpio. Guardar la clave RSA3072 fuera del checkout, protegida por ACL
del operador y con respaldo privado. Nunca introducirla como secreto GitHub,
archivo del repositorio, APK, salida de consola o paquete de instalación.

```powershell
python tools/catalog_publish.py --repo C:/repos/behost-apks --key C:/private/catalog-signing.pem --channel native_qa --backend-version 6.7.6 --sdk C:/Android/Sdk --java C:/Java/jdk17 --php C:/PHP/php.exe --apk hostess=C:/release/hostess.apk --apk mesero=C:/release/mesero.apk --apk cocina=C:/release/cocina.apk --apk valet=C:/release/valet.apk --report C:/release/publication.json
```

El comando obtiene versiones, identidad y firma del SDK; sube las cuatro APK a
una rama de artefactos conservando los demás archivos, las descarga por commit
y vuelve a verificarlas. Firma el catálogo con la clave local y añade una única
propuesta a `candidates/restaurante-be/`. No modifica checkout, índice ni HEAD
locales. GitHub ejecuta el verificador de `main`, y sólo entonces actualiza el
catálogo. La salida `proposal-pushed-await-workflow` requiere comprobar que el
workflow termina correctamente; no significa promoción terminada.

Si el lote ya está vigente, el comando no crea otra versión de catálogo. Si un
push falla por concurrencia, ejecutarlo de nuevo: no hace force push ni elimina
artefactos. Un lote rechazado puede permanecer en su rama, pero no se anuncia.
Los commits inmutables impiden que cambiar una rama sustituya una descarga ya
firmada. Conservar ramas de artefactos evita que GitHub deje sus commits sin
referencias. Mantener una copia privada de los fuentes/firmas Android originales.

## Firmar una propuesta para APK ya publicadas o retirar un canal

`tools/apk_catalog.py prepare --input propuesta.json --key /ruta/privada.pem
--output candidato.json --report verificacion.json --sdk /Android/Sdk` verifica
URLs existentes y firma. `--php` y `--java` son opcionales cuando están en PATH.
El JSON de entrada tiene `channels`; cada canal activo contiene
`min_backend_version` y `urls` con los cuatro roles. Para retirar un canal usar
`{"channels":{"native_qa":{"status":"disabled","min_backend_version":"6.7.6"}}}`.
Los demás canales conservan su valor. `--bootstrap` sólo sirve cuando el catálogo
no existe. `bootstrap-input.json` documenta las ocho URLs iniciales verificadas.

Añadir el envelope resultante como
`candidates/restaurante-be/SECUENCIA10DIGITOS-PRIMEROS16SHA256PAYLOAD.json` con git
normal; un único candidato por push. El workflow también admite ejecución manual
con la ruta de un candidato ya presente en `main`. Nunca escribir el catálogo
vigente directamente. Una revocación firmada no recupera versiones antiguas como
fallback. Cambiar una clave o autorizar producción requiere revisión de política
y actualización explícita de los consumidores; no se hace con un campo remoto.

## Modelo de confianza y recuperación

RSA3072/SHA256 firma los bytes UTF8 canónicos del payload. La clave pública y su
SHA están fijados en el verificador y los consumidores. El job de verificación
tiene sólo `contents:read` y ejecuta SDK contra las APK descargadas. Su artifact
inmutable de Actions contiene el envelope exacto y el reporte. El job de
promoción del mismo run tiene `contents:write`, no ejecuta SDK, revalida firma y
vínculo del reporte con esos bytes, y compara secuencia y versiones con `main`.
Las Actions están fijadas por SHA; no se ejecutan scripts de una rama candidata.

Catálogo, historial firmado y `restaurante-be-state.json` se escriben en un único
commit con padre igual al HEAD leído. La actualización es no-forzada: una carrera
rechaza el commit y relee el estado. Se conserva el máximo por identidad incluso
al deshabilitar un canal. Mismo código sólo admite exactamente la misma APK.
El estado público es un índice de control del workflow; su autoridad depende de
proteger `main` y los scripts. Los consumidores mantienen además su propio
registro persistente y verifican la firma, así que una edición sin la clave no
crea un catálogo válido. No borrar/resetear esos registros durante recuperación.

GitHub no necesita una clave privada ni un token personal guardado como secreto.
La promoción ocurre dentro del mismo workflow; no depende de que un push hecho
por `GITHUB_TOKEN` desencadene otro workflow. El token del job no se imprime ni
se guarda. Para restricciones de rama, autorizar este flujo sin habilitar force
push; los permisos/protecciones concretos del repositorio deben probarse.

No hay vencimiento calendario que obligue a volver a firmar una versión que
sigue vigente. Clientes revalidan cada300s y admiten copia verificada hasta24h,
señalando cuándo GitHub no se pudo confirmar. Después ocultan descargas; la
operación del restaurante no depende de ese catálogo. Esto limita una repetición
de una respuesta vieja, sin reemplazar TLS ni el registro local de secuencias.

La verificación limita APK a100MiB, SDK21–100, versión Android hasta2100000000,
URLs del repositorio por SHA40 y certificados/paquetes permitidos. Los APK
legacy deben ser no-debuggable; los nativos QA pueden ser debuggable. Los límites
coinciden con el consumidor PHP. El publicador emite fechas UTC `...Z` y rechaza
fechas futuras mayores de5min; ambos equipos necesitan reloj correcto.

## Evidencias

Los archivos de esta sección se incluyen en la entrega local de fuentes y
evidencias; no forman parte del repositorio público. Los reportes de ejecución
remota se conservan como artifacts del workflow de GitHub Actions.

`tests/test_catalog.py` comprueba firmas reales, política, repetición, retiro,
estado persistente, concurrencia CAS y preservación del checkout con git real.
Los fixtures de promoción son locales y no sustituyen observar un run real de
GitHub Actions. `evidence/bootstrap-sdk.json` y `readonly-sdk-verification.json`
registran extracción SDK real de las ocho APK. `fixture-0.3-sdk.json` sólo sirve
a pruebas de transición3→4 con mínimo3 explícito; la política pública sigue4.
