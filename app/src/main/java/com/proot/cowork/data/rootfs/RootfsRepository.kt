package com.proot.cowork.data.rootfs

import android.content.Context
import android.net.Uri
import com.proot.cowork.data.prefs.SettingsRepository
import com.proot.cowork.domain.proot.DesktopSession
import com.proot.cowork.domain.proot.DesktopState
import com.proot.cowork.service.ProotDesktopService
import com.proot.cowork.userland.UserlandMigration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

class RootfsRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val importer = RootfsImporter(context)

    suspend fun repairStateOnStartup() = withContext(Dispatchers.IO) {
        UserlandMigration.migrateRootfsLayout(context.filesDir)
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
            settingsRepository.ensureRootfsInstalledIfPresent(rootfsDir)
        }
    }

    /** Import from Storage Access Framework picker (content:// URI with read grant). */
    suspend fun importFromUri(uri: Uri): ImportResult = importRootfs { partialDir, onProgress ->
        importer.importFromUri(uri, partialDir, onProgress)
    }

    /** Import a tarball already on disk in app-readable storage (adb push / app files dir). */
    suspend fun importFromFile(file: File): ImportResult = importRootfs { partialDir, onProgress ->
        importer.importFromFile(file, partialDir, onProgress)
    }

    /**
     * Locate a tarball in app storage (and optional path hint) and import without SAF.
     * Use for adb workflows: push to [RootfsTarballLocator.dropDirectory].
     */
    suspend fun importAutoDiscover(pathHint: String? = null): ImportResult {
        val file = RootfsTarballLocator.discover(context, pathHint)
            ?: return ImportResult.Error(
                buildString {
                    append("No readable ${RootfsTarballLocator.DEFAULT_FILENAME} found.")
                    append(" Copy or adb push it to ")
                    append(RootfsTarballLocator.dropDirectoryLabel(context))
                    append(" then retry.")
                },
            )
        return importFromFile(file)
    }

    private suspend fun importRootfs(
        extract: suspend (partialDir: File, onProgress: suspend (Float) -> Unit) -> ImportResult,
    ): ImportResult = withContext(Dispatchers.IO) {
        stopDesktopService()
        settingsRepository.setImporting(true, 0f)
        DesktopSession.setState(DesktopState.IMPORTING)

        val partialDir = settingsRepository.getRootfsPartialDir()
        val rootfsDir = settingsRepository.getRootfsDir()

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
                if (!RootfsValidator.isValid(partialDir)) {
                    partialDir.deleteRecursively()
                    settingsRepository.clearImportingState()
                    DesktopSession.setState(DesktopState.NO_ROOTFS)
                    ImportResult.Error("Imported rootfs failed validation")
                } else {
                    if (rootfsDir.exists()) {
                        rootfsDir.deleteRecursively()
                    }
                    moveDirectoryPreservingSymlinks(partialDir, rootfsDir)
                    RootfsValidator.prepareUserlandGuest(context, rootfsDir)
                    when {
                        !RootfsValidator.hasVncStack(rootfsDir) -> {
                            settingsRepository.clearImportingState()
                            DesktopSession.setState(DesktopState.NO_ROOTFS)
                            ImportResult.Error(
                                "Rootfs missing tightvncserver. Run rootfs-setup/04-xfce-install.sh",
                            )
                        }
                        !RootfsValidator.hasXfceStack(rootfsDir) -> {
                            settingsRepository.clearImportingState()
                            DesktopSession.setState(DesktopState.NO_ROOTFS)
                            ImportResult.Error(
                                "Rootfs missing startxfce4. Install: apt install -y xfce4 dbus-x11",
                            )
                        }
                        else -> {
                            settingsRepository.setRootfsInstalled("ubuntu")
                            DesktopSession.setState(DesktopState.STARTING)
                            startDesktopService()
                            ImportResult.Success(rootfsDir)
                        }
                    }
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

    private fun moveDirectoryPreservingSymlinks(source: File, dest: File) {
        if (source.renameTo(dest)) return
        copyDirectoryPreservingSymlinks(source, dest)
        source.deleteRecursively()
    }

    private fun copyDirectoryPreservingSymlinks(source: File, dest: File) {
        if (!dest.exists() && !dest.mkdirs()) {
            throw IllegalStateException("Failed to create $dest")
        }
        source.listFiles()?.forEach { entry ->
            val target = File(dest, entry.name)
            val path = entry.toPath()
            when {
                Files.isSymbolicLink(path) -> {
                    if (target.exists()) target.delete()
                    Files.createSymbolicLink(target.toPath(), Files.readSymbolicLink(path))
                }
                entry.isDirectory -> copyDirectoryPreservingSymlinks(entry, target)
                else -> entry.copyTo(target, overwrite = true)
            }
        }
    }
}
