package com.proot.cowork

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.lifecycleScope
import com.proot.cowork.data.proot.RuntimeBootstrap
import com.proot.cowork.data.rootfs.RootfsTarballLocator
import com.proot.cowork.data.vnc.VncPortProbe
import com.proot.cowork.domain.proot.DesktopSession
import com.proot.cowork.domain.proot.DesktopState
import com.proot.cowork.ui.ProotCoworkApp
import com.proot.cowork.ui.theme.ProotCoworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ProotCoworkApp

        setContent {
            val scope = rememberCoroutineScope()
            val importLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri: Uri? ->
                if (uri != null) {
                    scope.launch {
                        try {
                            contentResolver.takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION,
                            )
                        } catch (_: SecurityException) {
                            // OpenDocument grants session access; persistable is optional.
                        }
                        app.rootfsRepository.importFromUri(uri)
                    }
                }
            }

            ProotCoworkTheme {
                ProotCoworkApp(
                    settingsRepository = app.settingsRepository,
                    rootfsRepository = app.rootfsRepository,
                    dropDirectoryLabel = RootfsTarballLocator.dropDirectoryLabel(this),
                    onImportDroppedFile = {
                        scope.launch {
                            app.rootfsRepository.importAutoDiscover()
                        }
                    },
                    onImportChooseFile = {
                        importLauncher.launch(
                            arrayOf(
                                "application/gzip",
                                "application/x-gzip",
                                "application/octet-stream",
                            ),
                        )
                    },
                )
            }
        }

        lifecycleScope.launch {
            app.rootfsRepository.repairStateOnStartup()
            val state = app.settingsRepository.rootfsState.first { !it.isImporting }
            if (state.isInstalled && app.rootfsRepository.canStartDesktop()) {
                val vncAlreadyUp = withContext(Dispatchers.IO) { VncPortProbe.isOpen() }
                if (vncAlreadyUp) {
                    DesktopSession.setState(DesktopState.RUNNING)
                } else {
                    app.rootfsRepository.startDesktopService()
                }
            }
        }
    }
}
