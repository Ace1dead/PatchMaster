package com.patchmaster.ui.navigation

import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.patchmaster.ui.screens.*
import com.patchmaster.PatchMasterApp
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val FILE_BROWSER = "file_browser"
    const val AGENT_CHAT = "agent_chat"
    const val CONSOLE = "console"
    const val SETTINGS = "settings"
    const val TEMPLATES = "templates"
    const val APK_DETAIL = "apk_detail/{apkPath}"
}

class NavigationViewModel {
    var currentApkUri: Uri? = null
    var currentApkPath: String = ""
    var onApkOpened: ((String) -> Unit)? = null
    var onSaveRequested: ((String) -> Unit)? = null

    fun handleOpenedApk(uri: Uri) {
        currentApkUri = uri
        currentApkPath = uri.toString()
        onApkOpened?.invoke(uri.toString())
    }

    fun handleSaveApk(uri: Uri) {
        onSaveRequested?.invoke(uri.toString())
    }
}

@Composable
fun PatchMasterNavHost(
    onOpenApk: () -> Unit,
    onCreateApk: () -> Unit,
    onViewModelReady: (NavigationViewModel) -> Unit
) {
    val navController = rememberNavController()
    val viewModel = remember { NavigationViewModel() }

    LaunchedEffect(Unit) {
        onViewModelReady(viewModel)
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToBrowser = { navController.navigate(Routes.FILE_BROWSER) },
                onNavigateToChat = { navController.navigate(Routes.AGENT_CHAT) },
                onNavigateToConsole = { navController.navigate(Routes.CONSOLE) },
                onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateToTemplates = { navController.navigate(Routes.TEMPLATES) },
                onOpenApk = onOpenApk
            )
        }

        composable(Routes.FILE_BROWSER) {
            FileBrowserScreen(
                onApkSelected = { path ->
                    navController.navigate("apk_detail/$path")
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.TEMPLATES) {
            ModTemplateScreen(
                apkPath = viewModel.currentApkPath.ifEmpty { null },
                onNavigateBack = { navController.popBackStack() },
                onApplyTemplate = { template ->
                    if (viewModel.currentApkPath.isNotEmpty()) {
                        PatchMasterApp.instance.aresAgent.setApkPath(viewModel.currentApkPath)
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            PatchMasterApp.instance.aresAgent.processInput(
                                "Apply ${template.name} mod to the APK"
                            )
                        }
                        navController.navigate(Routes.AGENT_CHAT)
                    }
                }
            )
        }

        composable(Routes.AGENT_CHAT) {
            AgentChatScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenApk = onOpenApk
            )
        }

        composable(Routes.CONSOLE) {
            ConsoleScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Routes.APK_DETAIL) { backStackEntry ->
            val apkPath = backStackEntry.arguments?.getString("apkPath") ?: ""
            ApkDetailScreen(
                apkPath = apkPath,
                onModify = { path ->
                    viewModel.currentApkPath = path
                    navController.navigate(Routes.AGENT_CHAT)
                },
                onNavigateBack = { navController.popBackStack() },
                onSaveApk = onCreateApk
            )
        }
    }
}
