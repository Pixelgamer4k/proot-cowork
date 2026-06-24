package com.proot.cowork.data.prootcontainer

import android.content.Context
import android.net.Uri
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.data.rootfs.ImportResult
import com.proot.cowork.domain.desktop.TermuxStackSession
import com.proot.cowork.domain.proot.DesktopSession
import com.proot.cowork.domain.proot.DesktopState
import com.proot.cowork.termux.bootstrap.TermuxBootstrap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ProotContainerRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val importer = ProotContainerImporter(context)

    suspend fun repairStateOnStartup() = withContext(Dispatchers.IO) {
        val partial = getPartialDir()
        partial.deleteRecursively()
        if (ProotContainerValidator.isInstalled(context)) {
            settingsRepository.clearImportingState()
            settingsRepository.ensureRootfsInstalledIfPresent(
                ProotContainerValidator.rootfsDir(context),
            )
            ProotContainerSysdata.installIfNeeded(context)
            DesktopSession.setState(DesktopState.RUNNING)
        } else {
            val containerDir = ProotContainerValidator.containerDir(context)
            if (containerDir.exists()) {
                containerDir.deleteRecursively()
            }
            settingsRepository.clearRootfsInstalled()
            DesktopSession.setState(DesktopState.NO_ROOTFS)
        }
    }

    fun isInstalled(): Boolean = ProotContainerValidator.isInstalled(context)

    suspend fun importFromUri(uri: Uri): ImportResult = importContainer { partial, onProgress ->
        importer.importFromUri(uri, partial, onProgress)
    }

    suspend fun importFromFile(file: File): ImportResult = importContainer { partial, onProgress ->
        importer.importFromFile(file, partial, onProgress)
    }

    suspend fun importAutoDiscover(pathHint: String? = null): ImportResult {
        val file = ProotContainerTarballLocator.discover(context, pathHint)
            ?: return ImportResult.Error(
                buildString {
                    append("No readable ${ProotContainerTarballLocator.DEFAULT_FILENAME} found.")
                    append(" Build it in Termux, then copy to ")
                    append(ProotContainerTarballLocator.dropDirectoryLabel(context))
                },
            )
        return importFromFile(file)
    }

    private suspend fun importContainer(
        extract: suspend (partialDir: File, onProgress: suspend (Float) -> Unit) -> ImportResult,
    ): ImportResult = withContext(Dispatchers.IO) {
        if (!TermuxBootstrap.ensureInstalled(context)) {
            return@withContext ImportResult.Error("Termux bootstrap not ready — wait a moment and retry")
        }
        settingsRepository.setImporting(true, 0f)
        DesktopSession.setState(DesktopState.IMPORTING)
        TermuxStackSession.appendLog("Importing proot container…")

        val partialDir = getPartialDir()
        val result = try {
            extract(partialDir) { progress ->
                settingsRepository.setImporting(true, progress)
            }
        } catch (e: Exception) {
            partialDir.deleteRecursively()
            ImportResult.Error(e.message ?: "Import failed")
        }

        when (result) {
            is ImportResult.Success -> {
                if (!ProotContainerValidator.isInstalled(context)) {
                    settingsRepository.clearImportingState()
                    DesktopSession.setState(DesktopState.NO_ROOTFS)
                    ImportResult.Error("Imported container failed validation")
                } else {
                    settingsRepository.setRootfsInstalled(ProotContainerValidator.DEFAULT_DISTRO)
                    DesktopSession.setState(DesktopState.RUNNING)
                    TermuxStackSession.appendLog("Ubuntu + XFCE ready — run: proot-xfce-start ubuntu")
                    result
                }
            }
            is ImportResult.Error -> {
                partialDir.deleteRecursively()
                settingsRepository.clearImportingState()
                DesktopSession.setState(DesktopState.NO_ROOTFS)
                TermuxStackSession.appendLog("Import failed: ${result.message}")
                result
            }
        }
    }

    private fun getPartialDir(): File = context.filesDir.resolve("proot-container.partial")
}
