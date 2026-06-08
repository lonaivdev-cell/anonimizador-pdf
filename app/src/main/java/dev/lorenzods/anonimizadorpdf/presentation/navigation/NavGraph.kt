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
import dev.lorenzods.anonimizadorpdf.presentation.ui.home.HomeScreen
import dev.lorenzods.anonimizadorpdf.presentation.ui.library.LibraryScreen
import dev.lorenzods.anonimizadorpdf.presentation.ui.settings.SettingsScreen
import dev.lorenzods.anonimizadorpdf.presentation.ui.viewer.ViewerScreen

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
        startDestination = Screen.Home.route,
        modifier = modifier,
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onOpenLibrary = { navController.navigateTopLevel(Screen.Library.route) },
                onOpenSettings = { navController.navigateTopLevel(Screen.Settings.route) },
                onOpenDocument = { docId -> navController.navigate(Screen.Viewer.create(docId)) },
            )
        }

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
            route = Screen.Viewer.route,
            arguments = listOf(navArgument(Screen.Viewer.ARG_DOC_ID) { type = NavType.LongType }),
        ) {
            ViewerScreen(
                onBack = { navController.popBackStack() },
                onNavigateToAnonymize = { docId, manual ->
                    navController.navigate(Screen.Anonymize.create(docId, manual))
                },
            )
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
