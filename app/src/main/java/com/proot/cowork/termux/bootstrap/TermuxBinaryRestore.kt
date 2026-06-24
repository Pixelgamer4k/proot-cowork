package com.proot.cowork.termux.bootstrap

import android.util.Log
import java.io.File

/** Restores real apt/dpkg ELF binaries after failed proot wrapper experiments. */
object TermuxBinaryRestore {

    private const val TAG = "TermuxBinaryRestore"

    fun unwrapPackageManagers(prefix: File) {
        unwrapApt(prefix)
        listOf("dpkg").forEach { name -> unwrapOne(prefix, name) }
        File(prefix, "bin/cowork-proot").delete()
        File(prefix, ".termux_proot_wrapped_v1").delete()
        File(prefix, ".termux_proot_wrapped_v2").delete()
    }

    private fun unwrapApt(prefix: File) {
        File(prefix, "bin/cowork-proot").delete()
        File(prefix, "bin/cowork-apt").delete()
        unwrapOne(prefix, "apt")
    }

    private fun unwrapOne(prefix: File, name: String) {
        val real = File(prefix, "bin/$name.real")
        if (!real.isFile) return
        val bin = File(prefix, "bin/$name")
        if (bin.exists()) bin.delete()
        if (!real.renameTo(bin)) {
            Log.w(TAG, "failed to restore $name from $name.real")
        } else {
            Log.i(TAG, "restored bin/$name")
        }
    }
}
