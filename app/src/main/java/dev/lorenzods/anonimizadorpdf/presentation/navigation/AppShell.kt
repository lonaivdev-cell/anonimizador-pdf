package dev.lorenzods.anonimizadorpdf.presentation.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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

private val topLevelDestinations = listOf(
    TopLevelDestination(Screen.Home.route, R.string.nav_home, Icons.Outlined.Home),
    TopLevelDestination(Screen.Library.route, R.string.nav_library, Icons.AutoMirrored.Outlined.MenuBook),
    TopLevelDestination(Screen.Settings.route, R.string.nav_settings, Icons.Outlined.Settings),
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
    val isTopLevel = topLevelDestinations.any { it.route == currentRoute }

    // A share arriving while the user is on another screen should surface the Library, which owns
    // the consume logic. The Activity clears the Uri once LibraryScreen imports it.
    LaunchedEffect(sharedPdfUri, currentRoute) {
        if (sharedPdfUri != null && currentRoute != null && currentRoute != Screen.Library.route) {
            navController.navigateTopLevel(Screen.Library.route)
        }
    }

    if (isExpanded) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(Modifier.widthIn(max = 280.dp)) {
                    BrandHeader()
                    topLevelDestinations.forEach { dest ->
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
                        topLevelDestinations.forEach { dest ->
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

@Composable
private fun BrandHeader() {
    Row(
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.badge_offline),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

internal fun NavController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
