package dev.lorenzods.anonimizadorpdf.presentation.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Library : Screen("library")
    data object Settings : Screen("settings")

    data object Viewer : Screen("viewer/{docId}") {
        const val ARG_DOC_ID = "docId"
        fun create(docId: Long) = "viewer/$docId"
    }

    data object Anonymize : Screen("anonymize/{docId}") {
        const val ARG_DOC_ID = "docId"
        fun create(docId: Long) = "anonymize/$docId"
    }
}
