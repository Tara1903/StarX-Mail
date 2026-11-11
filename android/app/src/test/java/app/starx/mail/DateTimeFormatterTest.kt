package app.starx.mail

import app.starx.mail.core.util.DateTimeFormatter
import org.junit.Assert.*
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DateTimeFormatterTest {

    @Test
    fun `parseIso correctly parses valid UTC ISO string`() {
        val iso = "2026-11-11T12:30:00Z"
        val parsed = DateTimeFormatter.parseIso(iso)

        assertNotNull(parsed)
    }

    @Test
    fun `parseIso returns null on blank or invalid date`() {
        assertNull(DateTimeFormatter.parseIso(""))
        assertNull(DateTimeFormatter.parseIso("invalid-date-format"))
    }

    @Test
    fun `formatRelativeTime returns time string for current day`() {
        val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
        val formatted = DateTimeFormatter.formatRelativeTime(nowIso)

        assertNotNull(formatted)
        assertTrue(formatted.isNotBlank())
    }
}
