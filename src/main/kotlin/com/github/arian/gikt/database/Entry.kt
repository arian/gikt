package com.github.arian.gikt.database

import com.github.arian.gikt.FileStat
import java.nio.file.Path

enum class Mode(val mode: String) {
    REGULAR("100644"),
    EXECUTABLE("100755"),
    TREE("40000")
}

class Entry private constructor(
    override val name: Path,
    override val mode: Mode = Mode.REGULAR,
    override val oid: ObjectId
) : TreeEntry {

    init {
        require(!name.isAbsolute) { "The path must not be absolute, but relative to the workspace path" }
    }

    val parents: List<Path>
        get() {
            val names = (0 until name.nameCount).map { name.getName(it) }
            return names.dropLast(1)
        }

    constructor(
        name: Path,
        stat: FileStat,
        oid: ObjectId
    ) : this(
        name,
        when (stat.executable) {
            true -> Mode.EXECUTABLE
            false -> Mode.REGULAR
        },
        oid
    )
}
