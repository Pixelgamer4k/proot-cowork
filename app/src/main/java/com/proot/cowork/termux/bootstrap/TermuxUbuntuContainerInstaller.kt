package com.proot.cowork.termux.bootstrap

import android.content.Context
import android.util.Log
import com.proot.cowork.domain.desktop.TermuxStackSession
import java.io.File

/**
 * Extracts the pre-built Ubuntu + XFCE proot-distro container from APK assets.
 * Layout matches proot-distro backup format: ubuntu/manifest.json + ubuntu/rootfs/.
 */
object TermuxUbuntuContainerInstaller {

    private const val TAG = "TermuxUbuntuContainer"
    private const val ASSET = "ubuntu-xfce-container.tar.gz"
    private const val MARKER = ".ubuntu_xfce_container_v1"
    private const val CONTAINER = "ubuntu"

    fun isInstalled(prefix: File): Boolean {
        val bash = File(prefix, "var/lib/proot-distro/containers/$CONTAINER/rootfs/usr/bin/bash")
        val session = File(
            prefix,
            "var/lib/proot-distro/containers/$CONTAINER/rootfs/usr/bin/xfce4-session",
        )
        return bash.isFile && session.isFile
    }

    fun installIfNeeded(context: Context, prefix: File): Boolean {
        if (isInstalled(prefix)) {
            File(prefix, MARKER).takeUnless { it.isFile }?.createNewFile()
            return true
        }

        val runtimeDir = File(prefix, "var/lib/proot-distro").also { it.mkdirs() }
        val marker = File(prefix, MARKER)
        val partial = File(prefix, ".ubuntu_xfce_container_extracting")

        return try {
            partial.createNewFile()
            TermuxStackSession.appendLog("Installing bundled Ubuntu + XFCE (large, one-time)…")
            Log.i(TAG, "extracting $ASSET into ${runtimeDir.absolutePath}")

            context.assets.open(ASSET).use { input ->
                if (!AssetExtractor.extractGzipTar(input, runtimeDir)) {
                    TermuxStackSession.appendLog("Ubuntu container extraction failed")
                    return false
                }
            }

            if (!isInstalled(prefix)) {
                TermuxStackSession.appendLog("Ubuntu container missing after extract")
                Log.e(TAG, "validation failed under ${runtimeDir.absolutePath}")
                return false
            }

            marker.createNewFile()
            TermuxStackSession.appendLog("Ubuntu + XFCE ready (proot-xfce-start ubuntu)")
            Log.i(TAG, "ubuntu container installed")
            true
        } catch (e: Exception) {
            Log.e(TAG, "ubuntu container install failed", e)
            TermuxStackSession.appendLog("Ubuntu install error: ${e.message}")
            false
        } finally {
            partial.delete()
        }
    }
}
