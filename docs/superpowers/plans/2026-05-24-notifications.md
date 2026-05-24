# Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Avisar al usuario de nuevos mensajes privados, menciones/citas/respuestas (contador de notificaciones FC), y nuevos hilos de usuarios favoritos — polling en background cada 15 minutos con WorkManager, notificaciones Android nativas.

**Architecture:** WorkManager corre `NotificationWorker` periódicamente; este fetcha la página principal de FC y parsea los contadores de badge del header, compara con los últimos valores guardados en `NotificationRepository`, y dispara notificaciones Android si hay novedades. Los usuarios favoritos se gestionan en el panel de settings (JS ↔ `SettingsBridge`) y se comprueban individualmente via HTTP.

**Tech Stack:** WorkManager CoroutineWorker, Android NotificationManager + NotificationChannel, SharedPreferences, HttpURLConnection, Robolectric para tests de parse/repo.

---

## Codebase Context

Estructura actual relevante:
- `IgnoreListWorker.kt` — patrón a seguir para CoroutineWorker
- `IgnoreListRepository.kt` — patrón SharedPreferences con `fc_filtro` prefs
- `IgnoreListFetcher.kt` — patrón HTTP + parse (suspend fun, Dispatchers.IO)
- `ForocochesApp.kt` — registra PeriodicWork; aquí añadiremos el nuevo worker
- `SettingsBridge.kt` — JavascriptInterface; añadiremos métodos de favoritos
- `settings-panel.js` — panel de configuración; añadiremos sección favoritos
- `AndroidManifest.xml` — añadir `POST_NOTIFICATIONS` permission
- Tests usan Robolectric, mismo patrón en todos los archivos de test

## File Structure

**Nuevos archivos:**
- `app/src/main/java/com/domenechobiol/forocoches/NotificationRepository.kt` — persiste contadores (lastPmCount, lastNotifCount) y usuarios favoritos (username → userId)
- `app/src/main/java/com/domenechobiol/forocoches/NotificationFetcher.kt` — HTTP fetch + parse de badge counts + último threadId por usuario favorito
- `app/src/main/java/com/domenechobiol/forocoches/NotificationHelper.kt` — crea canal, muestra notificaciones Android
- `app/src/main/java/com/domenechobiol/forocoches/NotificationWorker.kt` — CoroutineWorker periódico
- `app/src/test/java/com/domenechobiol/forocoches/NotificationRepositoryTest.kt`
- `app/src/test/java/com/domenechobiol/forocoches/NotificationFetcherTest.kt`

**Archivos modificados:**
- `app/src/main/AndroidManifest.xml` — POST_NOTIFICATIONS permission
- `app/src/main/java/com/domenechobiol/forocoches/ForocochesApp.kt` — schedule NotificationWorker
- `app/src/main/java/com/domenechobiol/forocoches/MainActivity.kt` — request permission en runtime
- `app/src/main/java/com/domenechobiol/forocoches/SettingsBridge.kt` — addFavoriteUser, removeFavoriteUser, getFavoriteUsersJson
- `app/src/main/assets/settings-panel.js` — sección de favoritos

---

## Task 1: NotificationRepository

**Files:**
- Create: `app/src/main/java/com/domenechobiol/forocoches/NotificationRepository.kt`
- Test: `app/src/test/java/com/domenechobiol/forocoches/NotificationRepositoryTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/domenechobiol/forocoches/NotificationRepositoryTest.kt
package com.domenechobiol.forocoches

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationRepositoryTest {

    private lateinit var repo: NotificationRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        repo = NotificationRepository(ctx)
    }

    @Test
    fun `getLastPmCount devuelve -1 inicialmente`() {
        assertEquals(-1, repo.getLastPmCount())
    }

    @Test
    fun `setLastPmCount persiste el valor`() {
        repo.setLastPmCount(3)
        assertEquals(3, repo.getLastPmCount())
    }

    @Test
    fun `getLastNotifCount devuelve -1 inicialmente`() {
        assertEquals(-1, repo.getLastNotifCount())
    }

    @Test
    fun `setLastNotifCount persiste el valor`() {
        repo.setLastNotifCount(7)
        assertEquals(7, repo.getLastNotifCount())
    }

    @Test
    fun `getFavoriteUsers devuelve lista vacía inicialmente`() {
        assertTrue(repo.getFavoriteUsers().isEmpty())
    }

    @Test
    fun `addFavoriteUser persiste username y userId`() {
        repo.addFavoriteUser("LuoJi", "882386")
        val favs = repo.getFavoriteUsers()
        assertEquals(1, favs.size)
        assertEquals("882386", favs["LuoJi"])
    }

    @Test
    fun `removeFavoriteUser elimina el usuario`() {
        repo.addFavoriteUser("LuoJi", "882386")
        repo.removeFavoriteUser("LuoJi")
        assertTrue(repo.getFavoriteUsers().isEmpty())
    }

    @Test
    fun `getLastSeenThreadId devuelve null inicialmente`() {
        assertNull(repo.getLastSeenThreadId("LuoJi"))
    }

    @Test
    fun `setLastSeenThreadId persiste y getLastSeenThreadId lo devuelve`() {
        repo.setLastSeenThreadId("LuoJi", "12345")
        assertEquals("12345", repo.getLastSeenThreadId("LuoJi"))
    }

    @Test
    fun `removeFavoriteUser elimina también el lastSeenThreadId`() {
        repo.addFavoriteUser("LuoJi", "882386")
        repo.setLastSeenThreadId("LuoJi", "12345")
        repo.removeFavoriteUser("LuoJi")
        assertNull(repo.getLastSeenThreadId("LuoJi"))
    }
}
```

- [ ] **Step 2: Run tests — verificar que fallan**

```
.\gradlew test --tests "com.domenechobiol.forocoches.NotificationRepositoryTest" 2>&1 | tail -5
```
Esperado: `FAILED` (clase no existe)

- [ ] **Step 3: Implementar NotificationRepository**

```kotlin
// app/src/main/java/com/domenechobiol/forocoches/NotificationRepository.kt
package com.domenechobiol.forocoches

import android.content.Context

class NotificationRepository(context: Context) {

    private val prefs = context.getSharedPreferences("fc_notifications", Context.MODE_PRIVATE)

    fun getLastPmCount(): Int = prefs.getInt("last_pm_count", -1)
    fun setLastPmCount(count: Int) { prefs.edit().putInt("last_pm_count", count).apply() }

    fun getLastNotifCount(): Int = prefs.getInt("last_notif_count", -1)
    fun setLastNotifCount(count: Int) { prefs.edit().putInt("last_notif_count", count).apply() }

    fun getFavoriteUsers(): Map<String, String> {
        val usernames = prefs.getStringSet("fav_users", emptySet()) ?: emptySet()
        return usernames.associateWith { prefs.getString("fav_uid_$it", "") ?: "" }
    }

    fun addFavoriteUser(username: String, userId: String) {
        val current = prefs.getStringSet("fav_users", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(username)
        prefs.edit()
            .putStringSet("fav_users", current)
            .putString("fav_uid_$username", userId)
            .apply()
    }

    fun removeFavoriteUser(username: String) {
        val current = prefs.getStringSet("fav_users", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(username)
        prefs.edit()
            .putStringSet("fav_users", current)
            .remove("fav_uid_$username")
            .remove("last_thread_$username")
            .apply()
    }

    fun getLastSeenThreadId(username: String): String? = prefs.getString("last_thread_$username", null)
    fun setLastSeenThreadId(username: String, threadId: String) {
        prefs.edit().putString("last_thread_$username", threadId).apply()
    }
}
```

- [ ] **Step 4: Run tests — verificar que pasan**

```
.\gradlew test --tests "com.domenechobiol.forocoches.NotificationRepositoryTest" 2>&1 | tail -5
```
Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/domenechobiol/forocoches/NotificationRepository.kt app/src/test/java/com/domenechobiol/forocoches/NotificationRepositoryTest.kt
git commit -m "feat: NotificationRepository para contadores y usuarios favoritos"
```

---

## Task 2: NotificationFetcher

**Files:**
- Create: `app/src/main/java/com/domenechobiol/forocoches/NotificationFetcher.kt`
- Test: `app/src/test/java/com/domenechobiol/forocoches/NotificationFetcherTest.kt`

**Nota sobre HTML:** Los métodos `parse*` son públicos y testables con HTML de fixture. El patrón de badge de FC usa `class="notification-count"` o similar — los tests usan HTML real capturado en el primer run (ver Task 4 Step 2). Mientras tanto los tests verifican el comportamiento con HTML de ejemplo construido con los patrones más probables de vBulletin/FC.

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/domenechobiol/forocoches/NotificationFetcherTest.kt
package com.domenechobiol.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationFetcherTest {

    private val fetcher = NotificationFetcher()

    @Test
    fun `parsePmCount devuelve 0 si no hay PMs en el HTML`() {
        assertEquals(0, fetcher.parsePmCount("<html><body></body></html>"))
    }

    @Test
    fun `parsePmCount extrae el número de mensajes privados`() {
        val html = """<a href="private.php">Mensajes Privados <span class="badge-count">5</span></a>"""
        assertEquals(5, fetcher.parsePmCount(html))
    }

    @Test
    fun `parsePmCount extrae desde formato texto parenthesis`() {
        val html = """<a href="private.php">Mensajes Privados (3 nuevos)</a>"""
        assertEquals(3, fetcher.parsePmCount(html))
    }

    @Test
    fun `parseNotifCount devuelve 0 si no hay notificaciones`() {
        assertEquals(0, fetcher.parseNotifCount("<html><body></body></html>"))
    }

    @Test
    fun `parseNotifCount extrae el contador de notificaciones`() {
        val html = """<span class="notification-count">12</span>"""
        assertEquals(12, fetcher.parseNotifCount(html))
    }

    @Test
    fun `parseLatestThreadId devuelve null si no hay hilos`() {
        assertNull(fetcher.parseLatestThreadId("<html><body></body></html>"))
    }

    @Test
    fun `parseLatestThreadId extrae el primer threadId`() {
        val html = """<a href="showthread.php?t=99999">Título del hilo</a>"""
        assertEquals("99999", fetcher.parseLatestThreadId(html))
    }
}
```

- [ ] **Step 2: Run tests — verificar que fallan**

```
.\gradlew test --tests "com.domenechobiol.forocoches.NotificationFetcherTest" 2>&1 | tail -5
```
Esperado: `FAILED`

- [ ] **Step 3: Implementar NotificationFetcher**

```kotlin
// app/src/main/java/com/domenechobiol/forocoches/NotificationFetcher.kt
package com.domenechobiol.forocoches

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class NotificationFetcher {

    companion object {
        private const val BASE_URL = "https://forocoches.com/foro"
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        // Patrones para PM count: badge-count span cerca de private.php, o formato "(N nuevos)"
        private val PM_BADGE = Regex("""href="private\.php"[\s\S]{0,200}?class="[^"]*(?:badge|count)[^"]*"[^>]*>(\d+)""", RegexOption.IGNORE_CASE)
        private val PM_PARENS = Regex("""href="private\.php"[\s\S]{0,200}?\((\d+)\s*nuevo""", RegexOption.IGNORE_CASE)

        // Patrón para notificaciones generales (menciones, citas, respuestas)
        private val NOTIF_BADGE = Regex("""class="[^"]*notification-count[^"]*"[^>]*>(\d+)""", RegexOption.IGNORE_CASE)
        private val NOTIF_BULLET = Regex("""class="[^"]*notif[^"]*badge[^"]*"[^>]*>(\d+)""", RegexOption.IGNORE_CASE)

        // Thread más reciente de un usuario
        private val THREAD_ID = Regex("""showthread\.php\?t=(\d+)""")
    }

    suspend fun fetchMainPage(cookie: String): String = withContext(Dispatchers.IO) {
        get("$BASE_URL/", cookie)
    }

    suspend fun fetchUserThreadsPage(cookie: String, userId: String): String = withContext(Dispatchers.IO) {
        get("$BASE_URL/search.php?do=finduser&userid=$userId&contenttype=vBForum_Thread&showposts=0", cookie)
    }

    fun parsePmCount(html: String): Int {
        PM_BADGE.find(html)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        PM_PARENS.find(html)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return 0
    }

    fun parseNotifCount(html: String): Int {
        NOTIF_BADGE.find(html)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        NOTIF_BULLET.find(html)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        return 0
    }

    fun parseLatestThreadId(html: String): String? =
        THREAD_ID.find(html)?.groupValues?.get(1)

    private fun get(url: String, cookie: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("Cookie", cookie)
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        val html = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        return html
    }
}
```

- [ ] **Step 4: Run tests — verificar que pasan**

```
.\gradlew test --tests "com.domenechobiol.forocoches.NotificationFetcherTest" 2>&1 | tail -5
```
Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/domenechobiol/forocoches/NotificationFetcher.kt app/src/test/java/com/domenechobiol/forocoches/NotificationFetcherTest.kt
git commit -m "feat: NotificationFetcher con parsers para PM, notificaciones y últimos hilos"
```

---

## Task 3: NotificationHelper

**Files:**
- Create: `app/src/main/java/com/domenechobiol/forocoches/NotificationHelper.kt`

- [ ] **Step 1: Implementar NotificationHelper** (no unit-testable independientemente — se valida en runtime)

```kotlin
// app/src/main/java/com/domenechobiol/forocoches/NotificationHelper.kt
package com.domenechobiol.forocoches

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object NotificationHelper {

    private const val CHANNEL_ID = "fc_notifications"
    private const val CHANNEL_NAME = "FC+ Notificaciones"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        channel.description = "Notificaciones de Forocoches Plus"
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun show(context: Context, id: Int, title: String, text: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(id, notification)
        }
    }

    // Notification IDs estables por tipo
    const val ID_PM = 1001
    const val ID_NOTIF = 1002
    const val ID_FAVORITE_BASE = 2000 // 2000 + índice del usuario favorito
}
```

- [ ] **Step 2: Commit**

```
git add app/src/main/java/com/domenechobiol/forocoches/NotificationHelper.kt
git commit -m "feat: NotificationHelper con canal y helper de show"
```

---

## Task 4: NotificationWorker

**Files:**
- Create: `app/src/main/java/com/domenechobiol/forocoches/NotificationWorker.kt`

- [ ] **Step 1: Implementar NotificationWorker**

```kotlin
// app/src/main/java/com/domenechobiol/forocoches/NotificationWorker.kt
package com.domenechobiol.forocoches

import android.content.Context
import android.webkit.CookieManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class NotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val cookie = CookieManager.getInstance().getCookie("https://forocoches.com")
            ?: return Result.success()
        if (cookie.isBlank()) return Result.success()

        val notifRepo = NotificationRepository(applicationContext)
        val fetcher = NotificationFetcher()

        return try {
            checkMainNotifications(cookie, fetcher, notifRepo)
            checkFavoriteUsers(cookie, fetcher, notifRepo)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun checkMainNotifications(
        cookie: String,
        fetcher: NotificationFetcher,
        repo: NotificationRepository
    ) {
        val html = fetcher.fetchMainPage(cookie)

        // Debug: log HTML snippet para verificar patrones (eliminar cuando los patrones estén confirmados)
        android.util.Log.d("FC_NOTIF", "html_length=${html.length}")
        val pmIdx = html.indexOf("private.php")
        if (pmIdx >= 0) android.util.Log.d("FC_NOTIF", "pm_context: ${html.substring(pmIdx, minOf(html.length, pmIdx + 300))}")

        val pmCount = fetcher.parsePmCount(html)
        val notifCount = fetcher.parseNotifCount(html)

        val lastPm = repo.getLastPmCount()
        if (lastPm >= 0 && pmCount > lastPm) {
            val diff = pmCount - lastPm
            NotificationHelper.show(
                applicationContext,
                NotificationHelper.ID_PM,
                "FC+ Mensajes Privados",
                "Tienes $diff nuevo${if (diff == 1) "" else "s"} mensaje${if (diff == 1) "" else "s"} privado${if (diff == 1) "" else "s"}"
            )
        }
        repo.setLastPmCount(pmCount)

        val lastNotif = repo.getLastNotifCount()
        if (lastNotif >= 0 && notifCount > lastNotif) {
            val diff = notifCount - lastNotif
            NotificationHelper.show(
                applicationContext,
                NotificationHelper.ID_NOTIF,
                "FC+ Notificaciones",
                "Tienes $diff nueva${if (diff == 1) "" else "s"} notificación${if (diff == 1) "" else "es"}"
            )
        }
        repo.setLastNotifCount(notifCount)
    }

    private suspend fun checkFavoriteUsers(
        cookie: String,
        fetcher: NotificationFetcher,
        repo: NotificationRepository
    ) {
        val favorites = repo.getFavoriteUsers()
        favorites.entries.forEachIndexed { index, (username, userId) ->
            val html = fetcher.fetchUserThreadsPage(cookie, userId)
            val latestThreadId = fetcher.parseLatestThreadId(html) ?: return@forEachIndexed
            val lastSeen = repo.getLastSeenThreadId(username)
            if (lastSeen != null && latestThreadId != lastSeen) {
                NotificationHelper.show(
                    applicationContext,
                    NotificationHelper.ID_FAVORITE_BASE + index,
                    "FC+ Nuevo hilo",
                    "@$username ha subido un hilo nuevo"
                )
            }
            if (lastSeen == null || latestThreadId != lastSeen) {
                repo.setLastSeenThreadId(username, latestThreadId)
            }
        }
    }
}
```

- [ ] **Step 2: Commit**

```
git add app/src/main/java/com/domenechobiol/forocoches/NotificationWorker.kt
git commit -m "feat: NotificationWorker con polling de PMs, notificaciones y favoritos"
```

---

## Task 5: Manifest, ForocochesApp y MainActivity

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/domenechobiol/forocoches/ForocochesApp.kt`
- Modify: `app/src/main/java/com/domenechobiol/forocoches/MainActivity.kt`

- [ ] **Step 1: Añadir POST_NOTIFICATIONS permission y schedular worker**

En `app/src/main/AndroidManifest.xml`, añadir dentro de `<manifest>` antes de `<application>`:
```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
```

- [ ] **Step 2: Schedular NotificationWorker y crear canal en ForocochesApp**

Reemplazar el contenido de `ForocochesApp.kt`:
```kotlin
package com.domenechobiol.forocoches

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ForocochesApp : Application() {
    override fun onCreate() {
        super.onCreate()

        NotificationHelper.createChannel(this)

        val ignoreRequest = PeriodicWorkRequestBuilder<IgnoreListWorker>(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ignore-list-refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            ignoreRequest
        )

        val notifRequest = PeriodicWorkRequestBuilder<NotificationWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "notification-poll",
            ExistingPeriodicWorkPolicy.KEEP,
            notifRequest
        )
    }
}
```

- [ ] **Step 3: Pedir permiso POST_NOTIFICATIONS en MainActivity (Android 13+)**

En `MainActivity.kt`, añadir los imports:
```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
```

Y añadir este método en `MainActivity`:
```kotlin
private fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
    ) {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
    }
}
```

Y llamarlo al final de `onCreate`, después de `fetchIgnoreListIfNeeded()`:
```kotlin
requestNotificationPermission()
```

- [ ] **Step 4: Build y verificar compilación**

```
.\gradlew assembleDebug 2>&1 | tail -10
```
Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```
git add app/src/main/AndroidManifest.xml app/src/main/java/com/domenechobiol/forocoches/ForocochesApp.kt app/src/main/java/com/domenechobiol/forocoches/MainActivity.kt
git commit -m "feat: permisos de notificación, canal y scheduling de NotificationWorker"
```

---

## Task 6: SettingsBridge — Favorites API

**Files:**
- Modify: `app/src/main/java/com/domenechobiol/forocoches/SettingsBridge.kt`

- [ ] **Step 1: Añadir NotificationRepository y métodos de favoritos**

Reemplazar el contenido de `SettingsBridge.kt`:
```kotlin
package com.domenechobiol.forocoches

import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.runBlocking

class SettingsBridge(
    private val repo: IgnoreListRepository,
    private val notifRepo: NotificationRepository,
    private val webView: WebView
) {

    @JavascriptInterface
    fun getHideMode(): String = repo.getHideMode()

    @JavascriptInterface
    fun setHideMode(mode: String) {
        if (mode == "complete" || mode == "message") repo.setHideMode(mode)
    }

    @JavascriptInterface
    fun getIgnoredUsersJson(): String {
        val users = repo.getIgnoredUsers()
        if (users.isEmpty()) return "[]"
        return "[" + users.joinToString(",") {
            "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        } + "]"
    }

    @JavascriptInterface
    fun removeIgnoredUser(username: String) {
        val users = repo.getIgnoredUsers().toMutableList()
        users.remove(username)
        repo.setIgnoredUsers(users)
    }

    @JavascriptInterface
    fun getLastUpdatedMs(): Long = repo.getLastUpdated()

    @JavascriptInterface
    fun triggerRefresh() {
        Thread {
            val cookie = CookieManager.getInstance().getCookie("https://forocoches.com")
                ?: run { notifyRefreshDone(); return@Thread }
            if (cookie.isBlank()) { notifyRefreshDone(); return@Thread }
            try {
                val users = runBlocking { IgnoreListFetcher().fetch(cookie) }
                if (users.isNotEmpty()) repo.setIgnoredUsers(users)
            } catch (_: Exception) { }
            notifyRefreshDone()
        }.start()
    }

    @JavascriptInterface
    fun getFavoriteUsersJson(): String {
        val favs = notifRepo.getFavoriteUsers()
        if (favs.isEmpty()) return "[]"
        return "[" + favs.entries.joinToString(",") {
            "{\"username\":\"${escapeJson(it.key)}\",\"userId\":\"${escapeJson(it.value)}\"}"
        } + "]"
    }

    @JavascriptInterface
    fun addFavoriteUser(username: String, userId: String) {
        notifRepo.addFavoriteUser(username, userId)
    }

    @JavascriptInterface
    fun removeFavoriteUser(username: String) {
        notifRepo.removeFavoriteUser(username)
    }

    private fun notifyRefreshDone() {
        webView.post {
            webView.evaluateJavascript("if(window.fcOnRefreshDone)window.fcOnRefreshDone()", null)
        }
    }

    private fun escapeJson(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
```

- [ ] **Step 2: Actualizar MainActivity para pasar notifRepo al bridge**

En `MainActivity.kt`, cambiar la línea del bridge:
```kotlin
// Antes:
webView.addJavascriptInterface(SettingsBridge(repo, webView), "Android")

// Después:
val notifRepo = NotificationRepository(this)
webView.addJavascriptInterface(SettingsBridge(repo, notifRepo, webView), "Android")
```

Y añadir el import:
```kotlin
import com.domenechobiol.forocoches.NotificationRepository
```

- [ ] **Step 3: Build y verificar**

```
.\gradlew assembleDebug 2>&1 | tail -10
```
Esperado: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/domenechobiol/forocoches/SettingsBridge.kt app/src/main/java/com/domenechobiol/forocoches/MainActivity.kt
git commit -m "feat: SettingsBridge con API de usuarios favoritos"
```

---

## Task 7: settings-panel.js — Sección de Favoritos

**Files:**
- Modify: `app/src/main/assets/settings-panel.js`

La sección de favoritos se añade debajo de la lista de ignorados. Si el usuario está en una página de perfil (`member.php?u=XXX`), aparece el botón "Añadir a favoritos". En el resto de páginas solo se muestra la lista de favoritos actuales.

- [ ] **Step 1: Añadir funciones y UI de favoritos a settings-panel.js**

Dentro del IIFE, después de la definición de `buildUserRows` y antes de `var isChecked = ...`, añadir:

```javascript
  function buildFavoriteRows(favs) {
    if (favs.length === 0)
      return '<div style="color:#666;font-size:12px;padding:4px 0;">No hay usuarios favoritos</div>';
    return favs.map(function(f) {
      return '<div style="display:flex;justify-content:space-between;align-items:center;' +
             'padding:6px 0;border-bottom:1px solid #2a2a2a;">' +
             '<span style="color:#ccc;">@' + escAttr(f.username) + '</span>' +
             '<button onclick="fcRemoveFavorite(\'' + escJs(f.username) + '\')" style="' +
             'background:none;border:none;color:#ff5555;cursor:pointer;font-size:13px;padding:2px 8px;">✕</button>' +
             '</div>';
    }).join('');
  }

  function getCurrentProfileInfo() {
    var params = new URLSearchParams(window.location.search);
    var userId = params.get('u');
    if (!userId || !window.location.href.includes('member.php')) return null;
    // Intentar extraer username del DOM de la página de perfil
    var nameEl = document.querySelector('.member_username, .profileusername, h1, h2');
    var username = nameEl ? nameEl.textContent.trim() : null;
    if (!username || username.length > 40) return null;
    return { userId: userId, username: username };
  }
```

Después de construir `panel.innerHTML`, añadir la sección de favoritos al final del HTML del panel (antes del cierre de la cadena):

```javascript
  // En panel.innerHTML, añadir antes del último cierre:
  var favUsers = JSON.parse(Android.getFavoriteUsersJson());
  var profileInfo = getCurrentProfileInfo();
  var addFavBtn = profileInfo
    ? '<button id="fc-add-fav-btn" onclick="fcAddCurrentFavorite()" style="' +
      'width:100%;margin-top:8px;background:none;border:1px solid #00e5cc;color:#00e5cc;' +
      'border-radius:4px;padding:4px;font-size:12px;cursor:pointer;">' +
      '★ Añadir @' + escAttr(profileInfo.username) + ' a favoritos</button>'
    : '';

  // Añadir al final de panel.innerHTML:
  panel.innerHTML += 
    '<div style="border-top:1px solid #333;margin-top:12px;padding-top:12px;">' +
    '<div style="font-size:12px;color:#888;margin-bottom:8px;">Usuarios favoritos <span style="font-size:10px;">(avisa de nuevos hilos)</span></div>' +
    '<div id="fc-fav-list">' + buildFavoriteRows(favUsers) + '</div>' +
    addFavBtn +
    '</div>';
```

Añadir las funciones globales de favoritos:

```javascript
  window.fcRemoveFavorite = function(username) {
    Android.removeFavoriteUser(username);
    var favs = JSON.parse(Android.getFavoriteUsersJson());
    var list = document.getElementById('fc-fav-list');
    if (list) list.innerHTML = buildFavoriteRows(favs);
  };

  window.fcAddCurrentFavorite = function() {
    var info = getCurrentProfileInfo();
    if (!info) return;
    Android.addFavoriteUser(info.username, info.userId);
    var favs = JSON.parse(Android.getFavoriteUsersJson());
    var list = document.getElementById('fc-fav-list');
    if (list) list.innerHTML = buildFavoriteRows(favs);
    var btn = document.getElementById('fc-add-fav-btn');
    if (btn) { btn.textContent = '✓ Añadido'; btn.disabled = true; }
  };
```

- [ ] **Step 2: Build, instalar y probar manualmente**

```
.\gradlew assembleDebug
```

Instalar en dispositivo. Verificar:
1. Abrir app → FAB ⚙ abre el panel → sección "Usuarios favoritos" visible
2. Navegar a un perfil (`member.php?u=XXX`) → botón "★ Añadir a favoritos" aparece
3. Pulsar el botón → usuario aparece en la lista
4. Pulsar ✕ → usuario desaparece

- [ ] **Step 3: Commit**

```
git add app/src/main/assets/settings-panel.js
git commit -m "feat: sección de usuarios favoritos en el panel de configuración"
```

---

## Task 8: Verificar parsing de notificaciones en runtime

**Nota:** Los patrones del `NotificationFetcher` son estimaciones. Esta tarea verifica que el HTML real de FC coincide y ajusta si es necesario.

- [ ] **Step 1: Trigger manual del worker para capturar HTML**

En `MainActivity.kt`, añadir temporalmente al final de `onCreate`:
```kotlin
// DEBUG TEMPORAL — eliminar después de verificar
androidx.work.OneTimeWorkRequestBuilder<NotificationWorker>().build().also {
    androidx.work.WorkManager.getInstance(this).enqueue(it)
}
```

- [ ] **Step 2: Instalar, abrir la app y filtrar Logcat por `FC_NOTIF`**

```
adb logcat -s FC_NOTIF
```

Verificar las líneas `pm_context:` para ver el HTML real alrededor de `private.php`. Si los contadores aparecen como 0 pero el usuario sí tiene notificaciones, actualizar los patrones `PM_BADGE`, `PM_PARENS`, `NOTIF_BADGE` en `NotificationFetcher.kt` con los valores reales del HTML.

- [ ] **Step 3: Eliminar el trigger manual de MainActivity y hacer commit**

```kotlin
// Eliminar las líneas de DEBUG añadidas en Step 1
```

```
git add app/src/main/java/com/domenechobiol/forocoches/MainActivity.kt app/src/main/java/com/domenechobiol/forocoches/NotificationFetcher.kt
git commit -m "fix: ajustar patrones de parsing de notificaciones con HTML real de FC"
```

---

## Self-Review

**Spec coverage:**
- ✅ Mensajes privados → `parsePmCount` + `ID_PM` notification
- ✅ Menciones/citas/respuestas → `parseNotifCount` + `ID_NOTIF` notification (FC agrupa en un contador)
- ✅ Nuevos hilos de favoritos → `checkFavoriteUsers` + `ID_FAVORITE_BASE + index`
- ✅ Gestión de favoritos en UI → Task 7
- ✅ Polling background → WorkManager 15 min
- ✅ Permission POST_NOTIFICATIONS → Task 5
- ✅ Notification channel → `NotificationHelper.createChannel`

**Placeholder scan:** ninguno detectado. Todos los steps tienen código completo.

**Type consistency:**
- `NotificationRepository.getFavoriteUsers()` → `Map<String, String>` (username → userId) ✅ usado en Task 4 (`favorites.entries`) y Task 6 (`getFavoriteUsersJson`)
- `SettingsBridge` constructor en Task 6 tiene 3 params → `MainActivity` actualizado en mismo task ✅
- `NotificationHelper.ID_FAVORITE_BASE` definido en Task 3, usado en Task 4 ✅
