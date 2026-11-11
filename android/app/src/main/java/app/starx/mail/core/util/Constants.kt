package app.starx.mail.core.util

object Constants {
    const val DEFAULT_BACKEND_URL = "https://starx-mail-backend.vercel.app/api/v1/"
    const val DATABASE_NAME = "starx_mail.db"
    const val PREFERENCES_NAME = "starx_preferences"

    // Preferences keys
    const val KEY_AUTH_TOKEN = "key_auth_token"
    const val KEY_BACKEND_URL = "key_backend_url"
    const val KEY_ACCOUNT_CONNECTED = "key_account_connected"
    const val KEY_BIOMETRIC_ENABLED = "key_biometric_enabled"
    const val KEY_SYNC_INTERVAL_MINUTES = "key_sync_interval_minutes"
    const val KEY_THEME_MODE = "key_theme_mode" // SYSTEM, LIGHT, DARK

    // Notifications
    const val CHANNEL_EMAILS_ID = "starx_emails_channel"
    const val CHANNEL_DELIVERY_ID = "starx_delivery_channel"
    const val CHANNEL_SYNC_ID = "starx_sync_channel"
    const val NOTIFICATION_SYNC_ID = 1001

    // WorkManager
    const val SYNC_WORK_TAG = "starx_email_sync_work"
}
