# Forocoches+

[![GitHub release](https://img.shields.io/github/v/release/neonforger/forolibre)](https://github.com/neonforger/forolibre/releases/latest)

App Android no oficial para Forocoches que mejora la experiencia del foro con filtros, notificaciones y otras utilidades. Funciona sobre la web del foro, así que mantiene el aspecto de siempre.

> **[Descargar APK](https://github.com/neonforger/forolibre/releases/latest)**

<!-- Capturas: pendientes de actualizar -->

## Funcionalidades

### Filtro de usuarios ignorados
- Oculta hilos del listado creados por usuarios ignorados
- Oculta posts de usuarios ignorados dentro de los hilos
- Oculta posts que citan a usuarios ignorados
- Se sincroniza con la lista de ignorados de tu cuenta de Forocoches

### Filtro político
- Oculta hilos cuyos títulos contienen palabras clave configurables
- Activado por defecto con términos de política española
- Toggle on/off desde el panel de ajustes
- Lista de palabras editable: añadir, eliminar, restaurar defaults

### Notificaciones
- Avisa de mensajes privados y de citas/menciones nuevas
- Estilo chat: muestra el remitente y una vista previa del mensaje
- Badge numérico en el icono, sincronizado (se limpia al leer)
- Polling en background cada ~15 min con la app cerrada (WorkManager)
- Modo **instantáneo** opcional (servicio en primer plano, intervalo configurable: 30 s / 60 s / 2 min)

### Diseño antiguo y nuevo
- Todas las funcionalidades funcionan igual en ambos diseños del foro
- Cambia entre diseño antiguo y nuevo desde el panel de ajustes

### Robustez ante cambios del foro
- Selectores configurables vía configuración remota: permite arreglar roturas sin publicar una versión nueva
- Fail-safe (nunca oculta de más) y un "canario" que avisa si el HTML del foro cambia

### Panel de ajustes
- Botón flotante (⚙) en la esquina inferior derecha
- Gestión de usuarios ignorados (con opción de sincronizar)
- Gestión del filtro político
- Notificaciones instantáneas e intervalo
- Cambio de diseño antiguo/nuevo

### Otros
- **Pull-to-refresh**: desliza hacia abajo para recargar la página actual
- **Bloqueador de anuncios**: bloquea dominios publicitarios conocidos + CSS para ocultar elementos de anuncios

## Arquitectura

- **WebView** envuelve la web móvil de Forocoches (`forocoches.com/foro/`)
- **content.js** — script inyectado en cada página para el filtrado de contenido (ambos diseños)
- **settings-panel.js** — script inyectado que crea el panel de ajustes flotante
- **SettingsBridge** — interfaz `@JavascriptInterface` entre JS y Kotlin
- **RemoteConfig** — descarga/cachea la configuración remota de selectores (`fc_config.json`)
- **NotificationFetcher / PmParser** — parseo HTML de notificaciones y mensajes privados
- **NotificationChecker** — lógica común de notificaciones (compartida por worker y servicio)
- **NotificationWorker** — WorkManager para el polling en background
- **NotificationService** — servicio en primer plano para el modo instantáneo
- **ThreadCreatorFetcher / ThreadCreatorCache** — resuelven el creador de un hilo (descarga con corte temprano) para el listado del diseño antiguo móvil
- **IgnoreListRepository / KeywordRepository / NotificationRepository** — persistencia en SharedPreferences

## Requisitos

- Android 8.0+ (API 26)
- Cuenta en Forocoches

## Build

Requiere JDK 17 o superior para el Android Gradle Plugin.

```bash
# Debug
./gradlew assembleDebug

# Release firmado (requiere keystore.properties en la raíz, no versionado)
./gradlew assembleRelease
```

Para generar un release firmado crea un fichero `keystore.properties` en la raíz del proyecto (está en `.gitignore`):

```properties
storeFile=../release.keystore
storePassword=TU_PASSWORD
keyAlias=TU_ALIAS
keyPassword=TU_PASSWORD
```

Sin ese fichero, los builds debug y release-sin-firmar siguen funcionando.
