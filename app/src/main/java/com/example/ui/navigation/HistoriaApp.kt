package com.example.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.core.AppThemeMode
import com.example.core.ThemePreferences
import com.example.data.ai.HistoriaAiService
import com.example.data.repository.HistoriaRepository
import com.example.ui.auth.SignInScreen
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOutUser
import com.example.ui.components.AtmosphericBackground
import com.example.ui.components.ThemeModeCycleButton
import com.example.ui.screens.ArchiveScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.StudioScreen
import com.example.ui.screens.ThemeSettingsScreen
import com.example.ui.viewmodel.ArchiveUiState
import com.example.ui.viewmodel.HistoriaViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

object HistoriaRoutes {
    const val STUDIO = "studio"
    const val ARCHIVE = "archive"
    const val THEME = "theme"
    const val PLAYER = "player/{historyId}"
    fun playerRoute(historyId: String): String = "player/$historyId"
}

private data class TopTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val TopTabs = listOf(
    TopTab(
        route = HistoriaRoutes.STUDIO,
        labelRes = R.string.nav_studio,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome
    ),
    TopTab(
        route = HistoriaRoutes.ARCHIVE,
        labelRes = R.string.nav_archive,
        selectedIcon = Icons.Filled.CollectionsBookmark,
        unselectedIcon = Icons.Outlined.CollectionsBookmark
    ),
    TopTab(
        route = HistoriaRoutes.THEME,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Palette,
        unselectedIcon = Icons.Outlined.Palette
    )
)

@Composable
fun HistoriaRoot(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    auth: FirebaseAuth = Firebase.auth
) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    val user = currentUser
    if (user == null) {
        SignInScreen(
            themeMode = themeMode,
            onThemeModeChange = onThemeModeChange,
            onAuthSuccess = {}
        )
    } else {
        androidx.compose.runtime.key(user.uid) {
            HistoriaAuthenticatedShell(
                currentUser = user,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoriaAuthenticatedShell(
    currentUser: FirebaseUser,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val uid = currentUser.uid
    val viewModel: HistoriaViewModel = viewModel(
        key = uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                HistoriaViewModel(
                    repository = HistoriaRepository(db),
                    aiService = HistoriaAiService(),
                    themePreferences = ThemePreferences(app),
                    currentUserId = uid
                )
            }
        }
    )

    val archiveState by viewModel.archiveState.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val rethemeInProgress by viewModel.rethemeInProgress.collectAsStateWithLifecycle()
    val studioForm by viewModel.studioForm.collectAsStateWithLifecycle()
    val lastCreatedId by viewModel.lastCreatedId.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val activeApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()
    val activeApiModel by viewModel.customApiModel.collectAsStateWithLifecycle()
    val apiValidationState by viewModel.apiKeyValidationState.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isPlayerRoute = currentRoute?.startsWith("player/") == true

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    LaunchedEffect(lastCreatedId) {
        val createdId = lastCreatedId
        if (!createdId.isNullOrBlank()) {
            viewModel.clearLastCreatedId()
            navController.navigate(HistoriaRoutes.playerRoute(createdId))
        }
    }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionMessage()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets.navigationBars,
            topBar = {
                if (!isPlayerRoute) {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineLarge
                            )
                        },
                        actions = {
                            ThemeModeCycleButton(
                                themeMode = themeMode,
                                onThemeModeChange = onThemeModeChange
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            },
            bottomBar = {
                if (!useRail && !isPlayerRoute) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        TopTabs.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) }
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            AtmosphericBackground {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail && !isPlayerRoute) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
                        ) {
                            TopTabs.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        NavHost(
                            navController = navController,
                            startDestination = HistoriaRoutes.STUDIO
                        ) {
                            composable(HistoriaRoutes.STUDIO) {
                                StudioScreen(
                                    form = studioForm,
                                    generationState = generationState,
                                    archiveState = archiveState,
                                    activeApiKey = activeApiKey,
                                    activeApiModel = activeApiModel,
                                    apiValidationState = apiValidationState,
                                    onApplyApiKey = viewModel::applyAndValidateApiKey,
                                    onResetDefaultApi = viewModel::resetToDefaultFirebaseAi,
                                    onClearApiValidation = viewModel::clearApiValidationStatus,
                                    onPromptChange = viewModel::updatePrompt,
                                    onTypeChange = viewModel::updateGenerationType,
                                    onCanvasThemeChange = viewModel::updateBackgroundTheme,
                                    onStyleChange = viewModel::updateVisualStyle,
                                    onAspectRatioChange = viewModel::updateAspectRatio,
                                    onSceneCountChange = viewModel::updateSceneCount,
                                    onGenerate = viewModel::generateProduction,
                                    onDismissError = viewModel::clearGenerationError,
                                    onOpenProduction = { id ->
                                        navController.navigate(HistoriaRoutes.playerRoute(id))
                                    }
                                )
                            }

                            composable(HistoriaRoutes.ARCHIVE) {
                                ArchiveScreen(
                                    archiveState = archiveState,
                                    onOpenProduction = { id ->
                                        navController.navigate(HistoriaRoutes.playerRoute(id))
                                    },
                                    onToggleFavorite = viewModel::toggleFavorite,
                                    onDeleteProduction = viewModel::deleteHistory
                                )
                            }

                            composable(HistoriaRoutes.THEME) {
                                ThemeSettingsScreen(
                                    themeMode = themeMode,
                                    currentUser = currentUser,
                                    activeApiKey = activeApiKey,
                                    activeApiModel = activeApiModel,
                                    apiValidationState = apiValidationState,
                                    onApplyApiKey = viewModel::applyAndValidateApiKey,
                                    onResetDefaultApi = viewModel::resetToDefaultFirebaseAi,
                                    onClearApiValidation = viewModel::clearApiValidationStatus,
                                    onThemeModeChange = onThemeModeChange,
                                    onSignOut = {
                                        signOutUser(
                                            credentialManager = credentialManager,
                                            scope = scope
                                        )
                                    }
                                )
                            }

                            composable(
                                route = HistoriaRoutes.PLAYER,
                                arguments = listOf(navArgument("historyId") { type = NavType.StringType })
                            ) { entry ->
                                val historyId = entry.arguments?.getString("historyId").orEmpty()
                                val production = (archiveState as? ArchiveUiState.Success)
                                    ?.items
                                    ?.firstOrNull { it.id == historyId }

                                PlayerScreen(
                                    production = production,
                                    rethemeInProgress = rethemeInProgress,
                                    onBack = { navController.popBackStack() },
                                    onRethemeWithGemini = viewModel::rethemeProduction
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
