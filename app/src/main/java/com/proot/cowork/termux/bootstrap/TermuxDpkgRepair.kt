package com.proot.cowork.termux.bootstrap

import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

/** Clears half-installed packages that block pkg (e.g. failed git/openssh chain). */
object TermuxDpkgRepair {

    private const val TAG = "TermuxDpkgRepair"

    fun repairIfNeeded(prefix: File, filesRoot: String) {
        val marker = File(prefix, ".termux_dpkg_repaired_v2")
        if (marker.isFile) return

        File(prefix, ".termux_dpkg_repaired_v1").delete()

        val dpkg = File(prefix, "bin/dpkg")
        val cowork = File(prefix, "bin/cowork-dpkg")
        val runner = when {
            dpkg.canExecute() -> dpkg
            cowork.canExecute() -> cowork
            else -> return
        }

        val broken = findHalfInstalled(prefix, runner, filesRoot)
        if (broken.isEmpty()) {
            marker.createNewFile()
            return
        }

        Log.i(TAG, "purging half-installed packages: $broken")
        val env = buildMap {
            put("PATH", "${prefix.absolutePath}/bin")
            put("LD_LIBRARY_PATH", File(prefix, "lib").absolutePath)
            put("HOME", File(prefix.parentFile, "home").absolutePath)
        }
        broken.forEach { pkg ->
            runDpkg(runner, env, listOf("--root=$filesRoot", "--purge", "--force-all", pkg))
        }
        marker.createNewFile()
    }

    private fun findHalfInstalled(prefix: File, dpkg: File, filesRoot: String): List<String> {
        val pb = ProcessBuilder(dpkg.absolutePath, "--root=$filesRoot", "-l")
        pb.environment().clear()
        pb.environment().putAll(
            mapOf(
                "PATH" to "${prefix.absolutePath}/bin",
                "LD_LIBRARY_PATH" to File(prefix, "lib").absolutePath,
            ),
        )
        val text = try {
            pb.start().inputStream.bufferedReader().readText()
        } catch (e: Exception) {
            Log.w(TAG, "dpkg -l failed: ${e.message}")
            return emptyList()
        }
        return text.lineSequence()
            .filter { it.startsWith("iU ") || it.startsWith("iF ") }
            .mapNotNull { line ->
                line.trim().split(Regex("\\s+")).getOrNull(1)
            }
            .distinct()
            .toList()
    }

    private fun runDpkg(dpkg: File, env: Map<String, String>, args: List<String>) {
        try {
            val pb = ProcessBuilder(listOf(dpkg.absolutePath) + args)
            pb.environment().clear()
            pb.environment().putAll(env)
            pb.start().waitFor(120, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w(TAG, "dpkg ${args.joinToString(" ")} failed: ${e.message}")
        }
    }
}
