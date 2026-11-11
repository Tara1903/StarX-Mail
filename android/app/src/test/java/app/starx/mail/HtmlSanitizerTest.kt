package app.starx.mail

import app.starx.mail.core.security.HtmlSanitizer
import org.junit.Assert.*
import org.junit.Test

class HtmlSanitizerTest {

    @Test
    fun `sanitize removes malicious script tags`() {
        val dangerousHtml = "<p>Hello</p><script>alert('xss');</script><b>World</b>"
        val sanitized = HtmlSanitizer.sanitize(dangerousHtml)

        assertFalse(sanitized.contains("<script>", ignoreCase = true))
        assertFalse(sanitized.contains("alert('xss')", ignoreCase = true))
        assertTrue(sanitized.contains("<p>Hello</p>"))
        assertTrue(sanitized.contains("<b>World</b>"))
    }

    @Test
    fun `sanitize replaces javascript links`() {
        val dangerousLink = "<a href=\"javascript:stealTokens()\">Click me</a>"
        val sanitized = HtmlSanitizer.sanitize(dangerousLink)

        assertFalse(sanitized.contains("javascript:", ignoreCase = true))
        assertTrue(sanitized.contains("href=\"#\""))
    }

    @Test
    fun `sanitize strips inline event handlers`() {
        val htmlWithEvents = "<img src=\"logo.png\" onerror=\"malicious()\" onload=\"hack()\" />"
        val sanitized = HtmlSanitizer.sanitize(htmlWithEvents)

        assertFalse(sanitized.contains("onerror", ignoreCase = true))
        assertFalse(sanitized.contains("onload", ignoreCase = true))
    }

    @Test
    fun `wrapWithStyles includes viewport and theme colors`() {
        val raw = "<p>Content</p>"
        val wrappedLight = HtmlSanitizer.wrapWithStyles(raw, isDarkTheme = false)
        val wrappedDark = HtmlSanitizer.wrapWithStyles(raw, isDarkTheme = true)

        assertTrue(wrappedLight.contains("viewport"))
        assertTrue(wrappedLight.contains("#FFFFFF")) // Light bg
        assertTrue(wrappedDark.contains("#121218")) // Dark bg
    }
}
