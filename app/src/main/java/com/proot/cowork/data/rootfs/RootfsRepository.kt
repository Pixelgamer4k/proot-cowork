package com.proot.cowork.data/rootfs

import android.content.Context
import android.net.Uri
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.domain.proot.DesktopSession
import com.proot.cowork.domain.proot.DesktopState
import com.proot.cowork.service.ProotDesktopService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class RootfsRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val importer = RootfsImporter(context)

    suspend fun repairStateOnStartup() = withContext(Dispatchers.IO) {
        val rootfsDir = settingsRepository.getRootfsDir()
        val partialDir = settingsRepository.getRootfsPartialDir()
        partialDir.deleteRecursively()

        if (!RootfsValidator.isValid(rootfsDir)) {
            if (rootfsDir.exists()) {
                rootfsDir.deleteRecursively()
            }
            settingsRepository.clearRootfsInstalled()
            DesktopSession.setState(DesktopState.NO_ROOTFS)
        } else {
            settingsRepository.clearImportingState()
        }
    }

    suspend fun importFromUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        stopDesktopService()
        settingsRepository.setImporting(true, 0f)
        DesktopSession.setState(DesktopState.IMPORTING)

        val partialDir = settingsRepository.getRootfsPartialDir()
        val rootfsDir = settingsRepository.getRootfsDir()

        val result = try {
            importer.import(
                sourceUri = uri,
                destDir = partialDir,
            ) { progress ->
                settingsRepository.setImporting(true, progress)
            }
        } catch (e: Exception) {
            partialDir.deleteRecursively()
            ImportResult.Error(e.message ?: "Import failed")
        }

        when (result) {
            is ImportResult.Success -> {
                if (!RootfsValidator.isValid(partialDir)) {
                    partialDir.deleteRecursively()
                    settingsRepository.clearImportingState()
                    DesktopSession.setState(DesktopState.NO_ROOTFS)
                    ImportResult.Error("Imported rootfs failed validation")
                } else {
                    if (rootfsDir.exists()) {
                        rootfsDir.deleteRecursively()
                    }
                    if (!partialDir.renameTo(rootfsDir)) {
                        partialDir.copyRecursively(rootfsDir, overwrite = true)
                        partialDir.deleteRecursively()
                    }
                    settingsRepository.setRootfsInstalled("ubuntu")
                    DesktopSession.setState(DesktopState.STARTING)
                    startDesktopService()
                    ImportResult.Success(rootfsDir)
                }
            }
            is ImportResult.Error -> {
                partialDir.deleteRecursively()
                settingsRepository.clearImportingState()
                DesktopSession.setState(DesktopState.NO_ROOTFS)
                result
            }
        }
    }

    fun canStartDesktop(): Boolean = RootfsValidator.isValid(settingsRepository.getRootfsDir())

    fun startDesktopService() {
        if (!canStartDesktop()) return
        val intent = android.content.Intent(context, ProotDesktopService::class.java)
        context.startForegroundService(intent)
    }

    fun stopDesktopService() {
        val intent = android.content.Intent(context, ProotDesktopService::class.java).apply {
            action = ProotDesktopService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun rebootDesktopService() {
        if (!canStartDesktop()) return
        val intent = android.content.Intent(context, ProotDesktopService::class.java).apply {
            action = ProotDesktopService.ACTION_REBOOT
        }
        context.startForegroundService(intent)
    }
}
