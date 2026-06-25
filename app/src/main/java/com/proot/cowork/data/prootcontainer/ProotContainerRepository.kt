package com.proot.cowork.data.prootcontainer

import android.content.Context
import android.net.Uri
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.data.rootfs.ImportResult
import com.proot.cowork.domain.desktop.StackFrontLayer
import com.proot.cowork.domain.desktop.TermuxStackSession
import com.proot.cowork.domain.importing.ImportPhase
import com.proot.cowork.domain.importing.ImportProgressUpdate
import com.proot.cowork.domain.importing.ImportSession
import com.proot.cowork.domain.proot.DesktopSession
import com.proot.cowork.domain.proot.DesktopState
import com.proot.cowork.termux.bootstrap.TermuxBootstrap
import com.proot.cowork.termux.proot.ProotXfceLauncher
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
        settingsRepository.clearImportingState()
        ImportSession.reset()

        if (ProotContainerValidator.isInstalled(context)) {
            settingsRepository.ensureRootfsInstalledIfPresent(
                ProotContainerValidator.rootfsDir(context),
            )
            ProotContainerSysdata.installIfNeeded(context)
            when {
                ProotXfceLauncher.isRunning() -> {
                    DesktopSession.setState(DesktopState.RUNNING)
                    TermuxStackSession.setFrontLayer(StackFrontLayer.X11)
                }
                DesktopSession.state.value == DesktopState.STOPPED -> {
                    // User powered off — do not auto-restart on resume.
                }
                else -> startDesktopIfPossible()
            }
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
                "No ${ProotContainerTarballLocator.DEFAULT_FILENAME} found in app storage. " +
                    "Use Choose file… or copy to ${ProotContainerTarballLocator.dropDirectoryLabel(context)}",
            )
        return importFromFile(file)
    }

    suspend fun startDesktopIfPossible(distro: String = ProotContainerValidator.DEFAULT_DISTRO): Boolean =
        withContext(Dispatchers.IO) {
            if (!isInstalled()) return@withContext false
            if (!TermuxBootstrap.ensureInstalled(context)) return@withContext false

            DesktopSession.setState(DesktopState.STARTING)
            TermuxStackSession.setFrontLayer(StackFrontLayer.X11)
            ImportSession.update(ImportPhase.STARTING_DESKTOP, 1f, "Launching Ubuntu XFCE…")

            val started = ProotXfceLauncher.start(context, distro)
            ImportSession.reset()
            if (started) {
                DesktopSession.setState(DesktopState.RUNNING)
                TermuxStackSession.appendLog("Ubuntu desktop running")
            } else {
                DesktopSession.setState(DesktopState.STOPPED)
                DesktopSession.appendLog("Could not start desktop — tap Reboot to retry")
            }
            started
        }

    fun stopDesktop() {
        ProotXfceLauncher.stop()
        DesktopSession.setState(DesktopState.STOPPED)
    }

    suspend fun rebootDesktop(distro: String = ProotContainerValidator.DEFAULT_DISTRO) {
        stopDesktop()
        startDesktopIfPossible(distro)
    }

    suspend fun waitForBootstrap(timeoutMs: Long = 120_000L): Boolean = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (TermuxBootstrap.ensureInstalled(context)) return@withContext true
            kotlinx.coroutines.delay(400)
        }
        TermuxBootstrap.ensureInstalled(context)
    }

    private suspend fun importContainer(
        extract: suspend (partialDir: File, onProgress: suspend (ImportProgressUpdate) -> Unit) -> ImportResult,
    ): ImportResult = withContext(Dispatchers.IO) {
        if (!waitForBootstrap()) {
            return@withContext ImportResult.Error(
                "Termux is still starting. Wait a few seconds and try again.",
            )
        }

        stopDesktop()
        ImportSession.begin()
        settingsRepository.setImporting(true, 0f)
        DesktopSession.setState(DesktopState.IMPORTING)

        val partialDir = getPartialDir()
        val result = try {
            extract(partialDir) { update ->
                ImportSession.update(update.phase, update.progress, update.detail)
            }
        } catch (e: Exception) {
            partialDir.deleteRecursively()
            ImportResult.Error(e.message ?: "Import failed")
        }

        when (result) {
            is ImportResult.Success -> {
                if (!ProotContainerValidator.isInstalled(context)) {
                    ImportSession.reset()
                    settingsRepository.clearImportingState()
                    DesktopSession.setState(DesktopState.NO_ROOTFS)
                    ImportResult.Error("Imported container failed validation")
                } else {
                    settingsRepository.clearImportingState()
                    settingsRepository.setRootfsInstalled(ProotContainerValidator.DEFAULT_DISTRO)
                    ImportSession.update(ImportPhase.STARTING_DESKTOP, 1f, "Launching Ubuntu XFCE…")
                    startDesktopIfPossible()
                    result
                }
            }
            is ImportResult.Error -> {
                partialDir.deleteRecursively()
                ImportSession.reset()
                settingsRepository.clearImportingState()
                DesktopSession.setState(DesktopState.NO_ROOTFS)
                DesktopSession.appendLog("Import failed: ${result.message}")
                result
            }
        }
    }

    private fun getPartialDir(): File = context.filesDir.resolve("proot-container.partial")
}
