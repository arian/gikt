package com.github.arian.gikt

import java.nio.file.Path

data class FileStat(
    val ctime: Long = 0,
    val ctimeNS: Int = 0,
    val mtime: Long = 0,
    val mtimeNS: Int = 0,
    val dev: Long = 0,
    val ino: Long = 0,
    val uid: Int = 1000,
    val gid: Int = 1000,
    val size: Long = 0,
    val executable: Boolean = false,
    val directory: Boolean = false
) {

    val file = !directory

    companion object {
        fun of(path: Path): FileStat {
            val attrs: Map<String, Any?> = path.readUnixAttributes()
            val ctime = path.getLastModifiedInstant()

            val views = path.fileSystem.supportedFileAttributeViews()
            val mode = when (views.contains("posix") && path.isExecutable()) {
                true -> 509 // 0775
                false -> 420 // 0644
            }

            return FileStat(
                ctime = ctime.epochSecond,
                ctimeNS = ctime.nano,
                // can't directly access `mtime`
                mtime = ctime.epochSecond,
                mtimeNS = ctime.nano,
                dev = attrs["dev"] as? Long ?: 0L,
                ino = attrs["ino"] as? Long ?: 0L,
                uid = attrs["uid"] as? Int ?: 1000,
                gid = attrs["gid"] as? Int ?: 1000,
                size = path.fileSize(),
                // bitmask all executable bits. 73 is 0111 in octal
                executable = mode and 73 == 73,
                directory = path.isDirectory()
            )
        }
    }
}
