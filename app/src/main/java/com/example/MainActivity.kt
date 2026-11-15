package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.burner.BurnerScreen
import com.example.ui.burner.BurnerViewModel
import com.example.ui.connect.ConnectScreen
import com.example.ui.connect.ConnectViewModel
import com.example.ui.forge.AiForgeScreen
import com.example.ui.forge.AiForgeViewModel
import com.example.ui.nano.NanoEditorScreen
import com.example.ui.nano.NanoViewModel
import com.example.ui.terminal.TerminalScreen
import com.example.ui.terminal.TerminalViewModel
import com.example.ui.theme.PocketBurnerTheme

sealed class PocketDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    data object Burner : PocketDestination("burner", "Burner", Icons.Filled.Bolt, Icons.Outlined.Bolt)
    data object Forge : PocketDestination("forge", "Forge", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
    data object Connect : PocketDestination("connect", "Connect", Icons.Filled.SettingsInputComponent, Icons.Outlined.SettingsInputComponent)
    data object Terminal : PocketDestination("terminal", "Terminal", Icons.Filled.Terminal, Icons.Outlined.Terminal)
    data object Nano : PocketDestination("nano", "Nano", Icons.Filled.Code, Icons.Outlined.Code)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = AppContainer.getInstance(applicationContext)
        setContent {
            PocketBurnerTheme {
                PocketBurnerRoot(container = container)
            }
        }
    }
}

@Composable
fun PocketBurnerRoot(container: AppContainer) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val destinations = remember {
        listOf(
            PocketDestination.Burner,
            PocketDestination.Forge,
            PocketDestination.Connect,
            PocketDestination.Terminal,
            PocketDestination.Nano,
        )
    }

    val burnerViewModel: BurnerViewModel = viewModel(factory = BurnerViewModel.Factory(container))
    val forgeViewModel: AiForgeViewModel = viewModel(factory = AiForgeViewModel.Factory(container))
    val connectViewModel: ConnectViewModel = viewModel(factory = ConnectViewModel.Factory(container))
    val terminalViewModel: TerminalViewModel = viewModel(factory = TerminalViewModel.Factory(container))
    val nanoViewModel: NanoViewModel = viewModel(factory = NanoViewModel.Factory(container))

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        destinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("nav_${dest.route}"),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail {
                        destinations.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label,
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag("rail_${dest.route}"),
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = PocketDestination.Burner.route,
                    modifier = Modifier.weight(1f),
                ) {
                    composable(PocketDestination.Burner.route) {
                        BurnerScreen(
                            viewModel = burnerViewModel,
                            onNavigateConnect = { navigateTo(PocketDestination.Connect.route) },
                        )
                    }
                    composable(PocketDestination.Forge.route) {
                        AiForgeScreen(
                            viewModel = forgeViewModel,
                            onOpenInNano = { fileName, code, targetBoardId ->
                                nanoViewModel.loadGeneratedScript(fileName, code, targetBoardId)
                                navigateTo(PocketDestination.Nano.route)
                            },
                        )
                    }
                    composable(PocketDestination.Connect.route) {
                        ConnectScreen(viewModel = connectViewModel)
                    }
                    composable(PocketDestination.Terminal.route) {
                        TerminalScreen(
                            viewModel = terminalViewModel,
                            onNavigateConnect = { navigateTo(PocketDestination.Connect.route) },
                            onOpenNano = { fileName ->
                                nanoViewModel.openFileFromCommand(fileName)
                                navigateTo(PocketDestination.Nano.route)
                            },
                        )
                    }
                    composable(PocketDestination.Nano.route) {
                        NanoEditorScreen(
                            viewModel = nanoViewModel,
                            onNavigateConnect = { navigateTo(PocketDestination.Connect.route) },
                            onToggleEncryption = { terminalViewModel.toggleEncryption() },
                        )
                    }
                }
            }
        }
    }
}
