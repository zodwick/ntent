package com.scrnstr

object Config {
    val SERVER_URL: String = BuildConfig.SERVER_URL

    val GEMINI_API_KEY: String = BuildConfig.GEMINI_API_KEY

    val WHATSAPP_CONTACTS: List<String> = BuildConfig.WHATSAPP_CONTACTS
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    const val NOTIFICATION_CHANNEL_ID = "scrnstr_actions"
    const val NOTIFICATION_CHANNEL_NAME = "ScrnStr Actions"

    const val SERVICE_NOTIFICATION_ID = 1
    const val RESULT_NOTIFICATION_ID = 2

    const val OVERLAY_AUTO_DISMISS_MS = 10000L
}
