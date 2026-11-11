package app.starx.mail.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DateTimeFormatter {
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val monthDayFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())

    fun parseIso(isoString: String?): Date? {
        if (isoString.isNullOrBlank()) return null
        return try {
            // Trim timezone suffix like .123Z or +00:00 for simple parsing
            val clean = isoString.split(".")[0].replace("Z", "")
            isoFormat.parse(clean)
        } catch (_: Exception) {
            null
        }
    }

    fun formatRelativeTime(isoString: String?): String {
        val date = parseIso(isoString) ?: return ""
        val now = Calendar.getInstance()
        val emailCal = Calendar.getInstance().apply { time = date }

        return when {
            isSameDay(now, emailCal) -> timeFormat.format(date)
            isYesterday(now, emailCal) -> "Yesterday"
            now.get(Calendar.YEAR) == emailCal.get(Calendar.YEAR) -> monthDayFormat.format(date)
            else -> SimpleDateFormat("MM/dd/yy", Locale.getDefault()).format(date)
        }
    }

    fun formatFullDateTime(isoString: String?): String {
        val date = parseIso(isoString) ?: return ""
        return fullDateFormat.format(date)
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.ERA) == cal2.get(Calendar.ERA) &&
                cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(today: Calendar, cal: Calendar): Boolean {
        val yesterday = (today.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return isSameDay(yesterday, cal)
    }
}
