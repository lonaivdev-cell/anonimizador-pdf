package dev.lorenzods.anonimizadorpdf.presentation.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.lorenzods.anonimizadorpdf.R
import dev.lorenzods.anonimizadorpdf.presentation.ui.onboarding.OnboardingScreen

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

@Composable
fun AppShell(
    windowSizeClass: WindowSizeClass,
    sharedPdfUri: Uri?,
    onSharedConsumed: () -> Unit,
    rootViewModel: RootViewModel = hiltViewModel(),
) {
    val onboardingDone by rootViewModel.onboardingDone.collectAsStateWithLifecycle()
    when (onboardingDone) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        false -> OnboardingScreen(onFinish = rootViewModel::completeOnboarding)
        true -> MainShell(windowSizeClass, sharedPdfUri, onSharedConsumed)
    }
}

@Composable
private fun MainShell(
    windowSizeClass: WindowSizeClass,
    sharedPdfUri: Uri?,
    onSharedConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = currentRoute == Screen.Library.route || currentRoute == Screen.Settings.route

    // A share arriving while the user is on another screen should surface the Library, which owns
    // the consume logic. The Activity clears the Uri once LibraryScreen imports it.
    LaunchedEffect(sharedPdfUri, currentRoute) {
        if (sharedPdfUri != null && currentRoute != null && currentRoute != Screen.Library.route) {
            navController.navigateTopLevel(Screen.Library.route)
        }
    }

    val destinations = listOf(
        TopLevelDestination(Screen.Library.route, R.string.nav_library, Icons.AutoMirrored.Filled.MenuBook),
        TopLevelDestination(Screen.Settings.route, R.string.nav_settings, Icons.Filled.Settings),
    )

    if (isExpanded) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(Modifier.widthIn(max = 280.dp)) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                    )
                    destinations.forEach { dest ->
                        NavigationDrawerItem(
                            icon = { Icon(dest.icon, contentDescription = null) },
                            label = { Text(stringResource(dest.labelRes)) },
                            selected = currentRoute == dest.route,
                            onClick = { navController.navigateTopLevel(dest.route) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                        )
                    }
                }
            },
        ) {
            AppNavHost(navController, isExpanded, sharedPdfUri, onSharedConsumed)
        }
    } else {
        Scaffold(
            bottomBar = {
                if (isTopLevel) {
                    NavigationBar {
                        destinations.forEach { dest ->
                            NavigationBarItem(
                                icon = { Icon(dest.icon, contentDescription = null) },
                                label = { Text(stringResource(dest.labelRes)) },
                                selected = currentRoute == dest.route,
                                onClick = { navController.navigateTopLevel(dest.route) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            AppNavHost(
                navController = navController,
                isExpanded = isExpanded,
                sharedPdfUri = sharedPdfUri,
                onSharedConsumed = onSharedConsumed,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

private fun NavController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
