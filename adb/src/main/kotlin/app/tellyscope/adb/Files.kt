// SPDX-License-Identifier: GPL-3.0-only
package app.tellyscope.adb

import okio.Sink
import okio.Source

/** The default shared folders Tellyscope reads and writes. */
enum class TvFolder(val path: String) {
    DOWNLOAD("/sdcard/Download"),
    MOVIES("/sdcard/Movies"),
}

data class TvFile(
    val name: String,
    val size: Long,
    val modifiedMillis: Long,
    val isDir: Boolean = false,
    val path: String = "",
)

data class TvStorage(val name: String, val path: String)

/** Phone ↔ TV file copies over the ADB sync service. */
class Files(private val session: AdbSession) {

    companion object {
        private const val S_IFMT = 0xF000
        private const val S_IFREG = 0x8000
        private const val S_IFDIR = 0x4000
        private const val FILE_MODE = 420 // 0644

        /** One path segment: no separators, no control characters, never "." or "..". */
        fun safeName(name: String): String {
            val cleaned = name.map { if (it == '/' || it == '\\' || it.code < 0x20) '_' else it }.joinToString("").trim()
            return cleaned.takeUnless { it.isEmpty() || it.all { c -> c == '.' } } ?: "file"
        }

        /** "clip.mp4" → "clip (1).mp4" → "clip (2).mp4" until it's free. */
        fun uniqueName(name: String, taken: Set<String>): String {
            if (name !in taken) return name
            val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
            val stem = name.substring(0, dot)
            val ext = name.substring(dot)
            return generateSequence(1) { it + 1 }.map { "$stem ($it)$ext" }.first { it !in taken }
        }
    }

    /** Plain files in [folder], newest first. A missing folder is an empty one. */
    suspend fun list(folder: TvFolder): List<TvFile> = runTransfer { _ ->
        session.list(folder.path)
            .filter { (it.mode and S_IFMT) == S_IFREG && !it.name.startsWith(".") }
            .map { TvFile(it.name, it.size, it.mtimeSec * 1000) }
            .sortedByDescending { it.modifiedMillis }
    }

    /**
     * Copies [source] into [folder] as [name], or "name (1).ext" if that's taken, so nothing on the
     * TV is ever overwritten. Returns the name it was saved under. A cancelled or failed copy deletes
     * the partial file, so a retry doesn't land as "name (1)" beside a truncated one.
     */
    suspend fun push(
        source: Source,
        size: Long?,
        name: String,
        folder: TvFolder,
        onProgress: (Progress) -> Unit = {},
    ): String {
        // Every entry counts as taken, hidden files and folders too, not just what [list] shows.
        val taken = runTransfer { _ -> session.list(folder.path).map { it.name }.toSet() }
        val target = uniqueName(safeName(name), taken)
        val path = "${folder.path}/$target"
        runTransfer(cleanUp = { runCatching { session.shell("rm -f ${shellQuote(path)}") } }) { stopped ->
            session.push(source.counting(size, stopped, onProgress), path, FILE_MODE, System.currentTimeMillis())
        }
        return target
    }

    /** Copies [source] directly into any target directory [destDirPath]. */
    suspend fun pushToPath(
        source: Source,
        size: Long?,
        name: String,
        destDirPath: String,
        onProgress: (Progress) -> Unit = {},
    ): String {
        session.shell("mkdir -p ${shellQuote(destDirPath)}")
        val taken = runTransfer { _ -> session.list(destDirPath).map { it.name }.toSet() }
        val target = uniqueName(safeName(name), taken)
        val path = "${destDirPath.trimEnd('/')}/$target"
        runTransfer(cleanUp = { runCatching { session.shell("rm -f ${shellQuote(path)}") } }) { stopped ->
            session.push(source.counting(size, stopped, onProgress), path, FILE_MODE, System.currentTimeMillis())
        }
        return target
    }

    /** Copies [file] from [folder] into [sink]. On cancel the caller deletes what it was writing to. */
    suspend fun pull(folder: TvFolder, file: TvFile, sink: Sink, onProgress: (Progress) -> Unit = {}) {
        require(safeName(file.name) == file.name) { "not a plain file name: ${file.name}" }
        runTransfer { stopped -> session.pull(sink.counting(file.size, stopped, onProgress), "${folder.path}/${file.name}") }
    }

    /** Copies file at [path] into [sink]. */
    suspend fun pullPath(path: String, size: Long, sink: Sink, onProgress: (Progress) -> Unit = {}) {
        runTransfer { stopped -> session.pull(sink.counting(size, stopped, onProgress), path) }
    }

    /** Deletes [file] from [folder] on the TV. */
    suspend fun delete(folder: TvFolder, file: TvFile): Boolean {
        require(safeName(file.name) == file.name) { "not a plain file name: ${file.name}" }
        val path = "${folder.path}/${file.name}"
        return session.shell("rm -f ${shellQuote(path)}").exitCode == 0
    }

    /** Copies [file] from [fromFolder] into [toFolder] on the TV. */
    suspend fun copy(fromFolder: TvFolder, file: TvFile, toFolder: TvFolder): Boolean {
        require(safeName(file.name) == file.name) { "not a plain file name: ${file.name}" }
        val srcPath = "${fromFolder.path}/${file.name}"
        session.shell("mkdir -p ${shellQuote(toFolder.path)}")
        val taken = runTransfer { _ -> session.list(toFolder.path).map { it.name }.toSet() }
        val targetName = uniqueName(file.name, taken)
        val destPath = "${toFolder.path}/$targetName"
        return session.shell("cp ${shellQuote(srcPath)} ${shellQuote(destPath)}").exitCode == 0
    }

    /** Lists files and directories at [dirPath], directories first, then newest files. */
    suspend fun listPath(dirPath: String): List<TvFile> = runTransfer { _ ->
        val clean = dirPath.trimEnd('/')
        session.list(clean)
            .filter { !it.name.startsWith(".") }
            .map { entry ->
                val isDir = (entry.mode and S_IFMT) == S_IFDIR
                val itemPath = "$clean/${entry.name}"
                TvFile(entry.name, entry.size, entry.mtimeSec * 1000L, isDir = isDir, path = itemPath)
            }
            .sortedWith(compareByDescending<TvFile> { it.isDir }.thenByDescending { it.modifiedMillis })

    }

    suspend fun discoverStorages(): List<TvStorage> {
        val storages = mutableListOf<TvStorage>()
        storages += TvStorage("Internal Storage", "/sdcard")
        storages += TvStorage("Download", "/sdcard/Download")
        storages += TvStorage("Movies", "/sdcard/Movies")

        val res = session.shell("ls -d /storage/* 2>/dev/null")
        if (res.exitCode == 0) {
            for (line in res.output.lines()) {
                val path = line.trim()
                if (path.isNotEmpty() && !path.contains("/self") && !path.contains("knox")) {
                    val last = path.substringAfterLast('/')
                    val name = if (last == "emulated" || path == "/storage/emulated/0") {
                        "Internal Storage (/storage/emulated/0)"
                    } else {
                        "USB Pendrive / External ($last)"
                    }
                    storages += TvStorage(name, path)
                }
            }
        }
        return storages.distinctBy { it.path }
    }

    /** Deletes file or directory at path. */
    suspend fun deletePath(path: String): Boolean {
        return session.shell("rm -rf ${shellQuote(path)}").exitCode == 0
    }

    /** Copies file or folder at srcPath into destDirPath. */
    suspend fun copyPath(srcPath: String, destDirPath: String): Boolean {
        session.shell("mkdir -p ${shellQuote(destDirPath)}")
        val res = session.shell("cp -r ${shellQuote(srcPath)} ${shellQuote(destDirPath)}/")
        return res.exitCode == 0
    }

    /** Moves file or folder at srcPath into destDirPath. */
    suspend fun movePath(srcPath: String, destDirPath: String): Boolean {
        session.shell("mkdir -p ${shellQuote(destDirPath)}")
        val res = session.shell("mv ${shellQuote(srcPath)} ${shellQuote(destDirPath)}/")
        return res.exitCode == 0
    }
}
