package dev.lorenzods.anonimizadorpdf.presentation.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import dev.lorenzods.anonimizadorpdf.presentation.ui.anonymize.AnonymizeScreen
import dev.lorenzods.anonimizadorpdf.presentation.ui.library.LibraryScreen
import dev.lorenzods.anonimizadorpdf.presentation.ui.settings.SettingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    isExpanded: Boolean,
    sharedPdfUri: Uri?,
    onSharedConsumed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Library.route,
        modifier = modifier,
    ) {
        composable(Screen.Library.route) {
            LibraryScreen(
                isExpanded = isExpanded,
                sharedPdfUri = sharedPdfUri,
                onSharedConsumed = onSharedConsumed,
                onNavigateToAnonymize = { docId, manual ->
                    navController.navigate(Screen.Anonymize.create(docId, manual))
                },
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }

        composable(
            route = Screen.Anonymize.route,
            arguments = listOf(
                navArgument(Screen.Anonymize.ARG_DOC_ID) { type = NavType.LongType },
                navArgument(Screen.Anonymize.ARG_MANUAL) { type = NavType.BoolType },
            ),
        ) {
            AnonymizeScreen(
                onBack = { navController.popBackStack() },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
    }
}
