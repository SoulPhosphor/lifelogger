package com.datadragon.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebAddressTest {

    @Test
    fun acceptsCommonWebpageInput() {
        listOf(
            "example.com",
            "www.example.com",
            "https://example.com",
            "http://example.com",
            "https://example.com/page",
            "https://example.co.uk/page?q=1#top",
            "example.com:8080/path",
        ).forEach { assertTrue(it, WebAddress.isValid(it)) }
    }

    @Test
    fun rejectsTextThatIsNotAWebpage() {
        listOf(
            "",
            "   ",
            "just some notes",
            "example",
            "example.",
            ".com",
            "hello world.com",
            "mailto:someone@example.com",
            "ftp://example.com",
            "https://user:pass@example.com",
            "example.com:notaport",
        ).forEach { assertFalse(it, WebAddress.isValid(it)) }
    }

    @Test
    fun suppliesHttpsOnlyWhenNoSchemeWasTyped() {
        assertEquals("https://example.com", WebAddress.openable("example.com"))
        assertEquals("https://www.example.com/page", WebAddress.openable("www.example.com/page"))
        // An address that already carries a scheme is opened exactly as typed.
        assertEquals("http://example.com", WebAddress.openable("http://example.com"))
        assertEquals("https://example.com", WebAddress.openable("  https://example.com  "))
    }

    @Test
    fun nothingToOpenForAnInvalidAddress() {
        assertNull(WebAddress.openable("just some notes"))
    }
}
