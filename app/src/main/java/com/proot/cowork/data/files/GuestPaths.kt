package com.proot.cowork.data.files

/** Well-known paths inside the Ubuntu proot-distro guest (login shell is root). */
object GuestPaths {
    const val HOME = "/root"
    const val DESKTOP = "/root/Desktop"
    const val ARTIFACTS_DIR = "/root/Desktop/Artifacts"

    fun isAllowed(path: String): Boolean {
        val normalized = path.trim().removeSuffix("/")
        return normalized == HOME ||
            normalized.startsWith("$HOME/") ||
            normalized == "/tmp" ||
            normalized.startsWith("/tmp/")
    }

    fun ensureArtifactsCmd(): String =
        "mkdir -p '${ARTIFACTS_DIR}' '${DESKTOP}'"
}
