package com.proot.cowork.data.proot

import android.content.Context
import com.proot.cowork.debug.DebugStatusWriter
import java.io.File

/**
 * Launch proot the same way manual `adb run-as … sh -c 'export …; linker64 …'` does.
 * Direct ProcessBuilder(linker64, …) with a cleared environment breaks guest ELF exec on
 * targetSdk 29+ (W^X / app-data execute restrictions).
 */
object ProotProcessLauncher {

    fun start(
        context: Context,
        argv: List<String>,
        env: Map<String, String>,
    ): Process {
        val tmpDir = File(context.filesDir, "tmp").also { it.mkdirs() }
        val debugDir = File(context.filesDir, "debug").also { it.mkdirs() }
        val launcher = File(tmpDir, "launch-proot.sh")

        DebugStatusWriter.writeTextFile(
            context,
            "last-proot-env.txt",
            env.entries.joinToString("\n") { "${it.key}=${it.value}" },
        )

        val exports = env.entries.joinToString("\n") { (key, value) ->
            "export ${shellName(key)}=${shellQuote(value)}"
        }
        val command = argv.joinToString(" ") { shellQuote(it) }

        launcher.writeText(
            """
            #!/system/bin/sh
            set -eu
            cd ${shellQuote(context.filesDir.absolutePath)}
            unset LD_PRELOAD
            $exports
            exec $command
            """.trimIndent() + "\n",
        )
        launcher.setExecutable(true, false)
        DebugStatusWriter.writeTextFile(context, "last-launcher.sh", launcher.readText())

        return ProcessBuilder(listOf("/system/bin/sh", launcher.absolutePath))
            .directory(context.filesDir)
            .redirectErrorStream(true)
            .start()
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    private fun shellName(name: String): String {
        require(name.matches(Regex("[A-Za-z_][A-Za-z0-9_]*"))) { "Invalid env name: $name" }
        return name
    }
}
