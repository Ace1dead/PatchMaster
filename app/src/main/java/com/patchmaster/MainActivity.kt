package com.patchmaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.patchmaster.ui.navigation.PatchMasterNavHost
import com.patchmaster.ui.theme.PatchMasterTheme

class MainActivity : ComponentActivity() {
    private val openDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { navigationViewModel?.handleOpenedApk(it) }
    }

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        uri?.let { navigationViewModel?.handleSaveApk(it) }
    }

    private var navigationViewModel: com.patchmaster.ui.navigation.NavigationViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PatchMasterTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PatchMasterNavHost(
                        onOpenApk = { openDocument.launch(arrayOf("application/vnd.android.package-archive")) },
                        onCreateApk = { createDocument.launch("modded.apk") },
                        onViewModelReady = { navigationViewModel = it }
                    )
                }
            }
        }
    }
}
