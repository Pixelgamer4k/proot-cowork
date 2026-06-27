package com.proot.cowork.data.files

enum class FilesViewMode {
    LIST,
    GRID,
}

enum class FilesSortOrder {
    NAME_ASC,
    NAME_DESC,
    SIZE_ASC,
    SIZE_DESC,
    DATE_ASC,
    DATE_DESC,
}

object FileBrowserSort {
    fun sorted(entries: List<GuestFileEntry>, order: FilesSortOrder): List<GuestFileEntry> {
        val foldersFirst = compareBy<GuestFileEntry> { !it.isDirectory }
        val comparator = when (order) {
            FilesSortOrder.NAME_ASC -> foldersFirst.thenBy { it.name.lowercase() }
            FilesSortOrder.NAME_DESC -> foldersFirst.thenByDescending { it.name.lowercase() }
            FilesSortOrder.SIZE_ASC -> foldersFirst.thenBy { it.sizeBytes }
            FilesSortOrder.SIZE_DESC -> foldersFirst.thenByDescending { it.sizeBytes }
            FilesSortOrder.DATE_ASC -> foldersFirst.thenBy { it.lastModified }
            FilesSortOrder.DATE_DESC -> foldersFirst.thenByDescending { it.lastModified }
        }
        return entries.sortedWith(comparator)
    }
}
