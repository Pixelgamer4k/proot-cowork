package com.proot.cowork

import android.os.Build
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.proot.cowork.data.prootcontainer.ProotContainerTarballLocator
import com.proot.cowork.data.rootfs.RootfsTarballLocator
import com.proot.cowork.data.vnc.VncPortProbe
import com.proot.cowork.domain.desktop.TERMUX_STACK_DESKTOP
import com.proot.cowork.domain.proot.DesktopSession
import com.proot.cowork.domain.proot.DesktopState
import android.provider.Settings
import android.net.Uri
import com.proot.cowork.service.TermuxStackService
import com.proot.cowork.termux.bootstrap.TermuxBootstrap
import com.proot.cowork.termux.bootstrap.TermuxStorageSetup
import com.proot.cowork.ui.ProotCoworkApp
import com.proot.cowork.ui.theme.ProotCoworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val bootstrapMutex = Mutex()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as ProotCoworkApp

        val dropLabel = if (TERMUX_STACK_DESKTOP) {
            ProotContainerTarballLocator.dropDirectoryLabel(this)
        } else {
            RootfsTarballLocator.dropDirectoryLabel(this)
        }

        setContent {
            ProotCoworkTheme {
                ProotCoworkApp(
                    settingsRepository = app.settingsRepository,
                    rootfsRepository = app.rootfsRepository,
                    prootContainerRepository = app.prootContainerRepository,
                    dropDirectoryLabel = dropLabel,
                )
            }
        }

        lifecycleScope.launch {
            if (TERMUX_STACK_DESKTOP) {
                if (com.termux.x11.MainActivity.prefs == null) {
                    com.termux.x11.MainActivity.prefs =
                        com.termux.x11.Prefs(applicationContext)
                }
                val svc = Intent(this@MainActivity, TermuxStackService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc)
                } else {
                    startService(svc)
                }
                if (TermuxStorageSetup.hasStorageAccess(this@MainActivity)) {
                    runBootstrapAndRepair()
                } else {
                    // Let Compose render before opening system settings.
                    kotlinx.coroutines.delay(400)
                    requestTermuxStorageAccess()
                }
                return@launch
            }
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

    override fun onResume() {
        super.onResume()
        if (TERMUX_STACK_DESKTOP && TermuxStorageSetup.hasStorageAccess(this)) {
            lifecycleScope.launch {
                runBootstrapAndRepair()
            }
        }
    }

    private suspend fun runBootstrapAndRepair() {
        bootstrapMutex.withLock {
            val app = application as ProotCoworkApp
            withContext(Dispatchers.IO) {
                TermuxBootstrap.ensureInstalled(applicationContext)
            }
            app.prootContainerRepository.repairStateOnStartup()
        }
    }

    private fun requestTermuxStorageAccess() {
        if (TermuxStorageSetup.hasStorageAccess(this)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    },
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_TERMUX_STORAGE,
            )
        }
    }

    companion object {
        private const val REQUEST_TERMUX_STORAGE = 9001
    }
}
