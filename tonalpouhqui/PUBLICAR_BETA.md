# Publicación automatizada de la beta

**Estado:** la automatización está publicada, pero **no ejecutada**. Una APK de depuración no se distribuye automáticamente.

La acción **Tonalpouhqui · publicar beta Android firmada**, disponible en la pestaña **Actions** del repositorio público, obtiene el código desde un repositorio privado llamado `zoniksr/Tonalpouhqui-Android`. Este repositorio privado **todavía debe crearse** y contener el código fuente v1.9.1 entregado fuera de GitHub.

Para activarla se deben configurar en GitHub los secretos del repositorio público: `TONALPOUHQUI_PRIVATE_REPO_TOKEN` (acceso de lectura al repositorio privado), `TONALPOUHQUI_KEYSTORE_B64`, `TONALPOUHQUI_STORE_PASSWORD`, `TONALPOUHQUI_KEY_ALIAS`, `TONALPOUHQUI_KEY_PASSWORD`, y el control `TONALPOUHQUI_PRIVACY_REVIEWED=YES` una vez revisada la privacidad.

Son opcionales `TONALPOUHQUI_LICENSE_PUBLIC_KEY_PEM` y `TONALPOUHQUI_API_BASE_URL` (sin un backend verificado, la app seguirá en modo local), así como `TONALPOUHQUI_WEATHER_PROXY_URL` (HTTPS, con el formato JSON Open-Meteo; sin él el clima se desactiva). **Ninguna clave de proveedor meteorológico se coloca en el APK.**

Tras ello, ejecutar Actions → Tonalpouhqui · publicar beta Android firmada → Run workflow. Se realizarán las pruebas, la firma, las verificaciones y la creación automática de un **Pre-release** con su APK y SHA-256.

La aplicación beta sólo se permite bajo el certificado de firma autorizado y caduca el 1 de diciembre de 2026 (UTC). La build comercial sigue requiriendo licencia remota.

**Esta configuración NO publica código fuente ni archivos de firma, y NO modifica los demás proyectos BeHost.**
