package com.fcplus.forocoches

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedOriginsTest {

    @Test
    fun `accepts trusted forocoches https origins`() {
        assertTrue(TrustedOrigins.isTrustedForocochesUrl("https://forocoches.com/foro/"))
        assertTrue(TrustedOrigins.isTrustedForocochesUrl("https://www.forocoches.com/foro/private.php"))
    }

    @Test
    fun `rejects non https and lookalike origins`() {
        assertFalse(TrustedOrigins.isTrustedForocochesUrl("http://forocoches.com/foro/"))
        assertFalse(TrustedOrigins.isTrustedForocochesUrl("https://forocoches.com.evil.test/foro/"))
        assertFalse(TrustedOrigins.isTrustedForocochesUrl("https://evil.test/?next=forocoches.com"))
    }

    @Test
    fun `falls back to default url for untrusted input`() {
        assertEquals(
            TrustedOrigins.DEFAULT_URL,
            TrustedOrigins.trustedUrlOrDefault("https://evil.test/")
        )
    }
}
