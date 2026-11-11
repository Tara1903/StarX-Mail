package app.starx.mail.presentation.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Inbox : Screen("inbox")
    object Sent : Screen("sent")
    object Drafts : Screen("drafts")
    object Starred : Screen("starred")
    object ArchiveTrash : Screen("archive_trash")
    object Settings : Screen("settings")
    object Search : Screen("search")

    object EmailDetails : Screen("email_details/{emailId}/{threadId}") {
        fun createRoute(emailId: String, threadId: String): String =
            "email_details/$emailId/$threadId"
    }

    object Compose : Screen("compose?draftId={draftId}&replyToId={replyToId}&replyAll={replyAll}&forwardId={forwardId}") {
        fun createRoute(
            draftId: String? = null,
            replyToId: String? = null,
            replyAll: Boolean = false,
            forwardId: String? = null
        ): String {
            val params = mutableListOf<String>()
            draftId?.let { params.add("draftId=$it") }
            replyToId?.let { params.add("replyToId=$it") }
            if (replyAll) params.add("replyAll=true")
            forwardId?.let { params.add("forwardId=$it") }

            return if (params.isEmpty()) "compose" else "compose?${params.joinToString("&")}"
        }
    }
}
