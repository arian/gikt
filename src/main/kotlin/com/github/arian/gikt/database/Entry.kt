package com.github.arian.gikt.database

import com.github.arian.gikt.FileStat
import com.github.arian.gikt.parents
import java.nio.file.Path

enum class Mode(val mode: String) {
    REGULAR("100644"),
    EXECUTABLE("100755"),
    TREE("40000");

    companion object {
        fun parse(name: String): Mode? = values().find { it.mode == name }
    }
}

class Entry(
    override val name: Path,
    override val mode: Mode = Mode.REGULAR,
    override val oid: ObjectId
) : TreeEntry {

    init {
        require(!name.isAbsolute) { "The path must not be absolute, but relative to the workspace path" }
    }

    val parents: List<Path> get() = name.parents()

    constructor(
        name: Path,
        stat: FileStat,
        oid: ObjectId
    ) : this(
        name,
        when {
            stat.directory -> Mode.TREE
            stat.executable -> Mode.EXECUTABLE
            else -> Mode.REGULAR
        },
        oid
    )
}
