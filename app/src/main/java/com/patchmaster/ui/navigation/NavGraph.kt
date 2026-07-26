package com.patchmaster.ui.navigation

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.patchmaster.ui.screens.*
import com.patchmaster.PatchMasterApp
import java.io.File
import java.io.FileOutputStream

object Routes {
    const val HOME = "home"
    const val FILE_BROWSER = "file_browser"
    const val AGENT_CHAT = "agent_chat"
    const val CONSOLE = "console"
    const val SETTINGS = "settings"
    const val TEMPLATES = "templates"
    const val APK_DETAIL = "apk_detail"
}

class NavigationViewModel {
    var currentApkUri: Uri? = null
    var currentApkPath: String = ""
    var onApkOpened: ((String) -> Unit)? = null
    var onSaveRequested: ((String) -> Unit)? = null

    fun handleOpenedApk(context: Context, uri: Uri) {
        currentApkUri = uri
        val copy = copyUriToInternal(context, uri)
        if (copy != null) {
            currentApkPath = copy.absolutePath
            PatchMasterApp.instance.aresAgent.setApkPath(copy.absolutePath)
            onApkOpened?.invoke(copy.absolutePath)
        }
    }

    private fun copyUriToInternal(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val apkDir = File(context.filesDir, "imported_apks").also { it.mkdirs() }
            val fileName = "imported_${System.currentTimeMillis()}.apk"
            val outFile = File(apkDir, fileName)
            FileOutputStream(outFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            outFile
        } catch (e: Exception) {
            null
        }
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

    LaunchedEffect(Unit) {
        viewModel.onApkOpened = { path ->
            navController.navigate(Routes.APK_DETAIL) {
                popUpTo(Routes.HOME)
            }
        }
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
                    viewModel.currentApkPath = path
                    PatchMasterApp.instance.aresAgent.setApkPath(path)
                    navController.navigate(Routes.APK_DETAIL)
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

        composable(Routes.APK_DETAIL) {
            val apkPath = viewModel.currentApkPath
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
