package com.github.arian.gikt

import java.nio.file.Files
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
    val executable: Boolean
) {
    companion object {
        fun of(path: Path): FileStat {
            val attrs = Files.readAttributes(path, "unix:dev,ino,uid,gid")
            val ctime = Files.getLastModifiedTime(path).toInstant()

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
                size = Files.size(path),
                executable = Files.isExecutable(path)
            )
        }
    }
}
