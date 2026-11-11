package app.starx.mail.core.security

import java.util.regex.Pattern

object HtmlSanitizer {
    private val SCRIPT_PATTERN = Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    private val JS_URI_PATTERN = Pattern.compile("href=[\"']javascript:[^\"']*[\"']", Pattern.CASE_INSENSITIVE)
    private val ON_EVENT_PATTERN = Pattern.compile("on[a-z]+\\s*=\\s*[\"'][^\"']*[\"']", Pattern.CASE_INSENSITIVE)
    private val IFRAME_PATTERN = Pattern.compile("<iframe[^>]*>.*?</iframe>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)

    /**
     * Sanitizes email HTML body, removing script tags, JS links, and dangerous event handlers
     */
    fun sanitize(rawHtml: String?): String {
        if (rawHtml.isNullOrBlank()) return "<p></p>"

        var safe = SCRIPT_PATTERN.matcher(rawHtml).replaceAll("")
        safe = JS_URI_PATTERN.matcher(safe).replaceAll("href=\"#\"")
        safe = ON_EVENT_PATTERN.matcher(safe).replaceAll("")
        safe = IFRAME_PATTERN.matcher(safe).replaceAll("")

        return safe
    }

    /**
     * Wraps HTML with responsive styling matching the StarX theme
     */
    fun wrapWithStyles(htmlContent: String, isDarkTheme: Boolean = false): String {
        val bgColor = if (isDarkTheme) "#121218" else "#FFFFFF"
        val textColor = if (isDarkTheme) "#E4E2EB" else "#191820"
        val linkColor = if (isDarkTheme) "#A594F9" else "#6750A4"
        val borderColor = if (isDarkTheme) "#2E2D38" else "#E5E1EC"

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        font-size: 15px;
                        line-height: 1.6;
                        color: $textColor;
                        background-color: $bgColor;
                        margin: 0;
                        padding: 12px;
                        word-wrap: break-word;
                    }
                    a {
                        color: $linkColor;
                        text-decoration: underline;
                    }
                    img {
                        max-width: 100% !important;
                        height: auto !important;
                        border-radius: 8px;
                    }
                    table {
                        max-width: 100% !important;
                        border-collapse: collapse;
                    }
                    pre, code {
                        background-color: ${if (isDarkTheme) "#1E1E28" else "#F1EFF8"};
                        padding: 8px 12px;
                        border-radius: 6px;
                        font-size: 13px;
                        overflow-x: auto;
                    }
                    blockquote {
                        margin: 8px 0;
                        padding-left: 12px;
                        border-left: 3px solid $borderColor;
                        color: ${if (isDarkTheme) "#9D9AAB" else "#6E6B7B"};
                    }
                    hr {
                        border: 0;
                        border-top: 1px solid $borderColor;
                        margin: 16px 0;
                    }
                </style>
            </head>
            <body>
                $htmlContent
            </body>
            </html>
        """.trimIndent()
    }
}
