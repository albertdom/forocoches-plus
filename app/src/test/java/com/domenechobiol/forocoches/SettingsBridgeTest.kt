package com.domenechobiol.forocoches

import android.content.Context
import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsBridgeTest {

    private lateinit var repo: IgnoreListRepository
    private lateinit var bridge: SettingsBridge

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        repo = IgnoreListRepository(ctx)
        val webView = WebView(ctx)
        bridge = SettingsBridge(repo, webView)
    }

    @Test
    fun `getHideMode devuelve el valor del repo`() {
        repo.setHideMode("complete")
        assertEquals("complete", bridge.getHideMode())
    }

    @Test
    fun `setHideMode persiste valores validos`() {
        bridge.setHideMode("complete")
        assertEquals("complete", repo.getHideMode())
        bridge.setHideMode("message")
        assertEquals("message", repo.getHideMode())
    }

    @Test
    fun `setHideMode ignora valores invalidos`() {
        repo.setHideMode("message")
        bridge.setHideMode("hack")
        assertEquals("message", repo.getHideMode())
    }

    @Test
    fun `getIgnoredUsersJson devuelve JSON array valido`() {
        repo.setIgnoredUsers(listOf("UserUno", "UserDos"))
        val json = bridge.getIgnoredUsersJson()
        assertTrue(json.startsWith("["))
        assertTrue(json.endsWith("]"))
        assertTrue(json.contains("\"UserUno\""))
        assertTrue(json.contains("\"UserDos\""))
    }

    @Test
    fun `getIgnoredUsersJson devuelve array vacio si no hay ignorados`() {
        assertEquals("[]", bridge.getIgnoredUsersJson())
    }

    @Test
    fun `getIgnoredUsersJson escapa comillas en usernames`() {
        repo.setIgnoredUsers(listOf("User\"Quote"))
        val json = bridge.getIgnoredUsersJson()
        assertTrue(json.contains("\\\""))
    }

    @Test
    fun `removeIgnoredUser elimina el usuario correcto`() {
        repo.setIgnoredUsers(listOf("UserUno", "UserDos"))
        bridge.removeIgnoredUser("UserUno")
        assertFalse(repo.getIgnoredUsers().contains("UserUno"))
        assertTrue(repo.getIgnoredUsers().contains("UserDos"))
    }

    @Test
    fun `getLastUpdatedMs devuelve el timestamp del repo`() {
        repo.setIgnoredUsers(listOf("user1"))
        assertTrue(bridge.getLastUpdatedMs() > 0)
    }
}
