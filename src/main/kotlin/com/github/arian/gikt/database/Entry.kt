package com.github.arian.gikt.database

import com.github.arian.gikt.FileStat
import com.github.arian.gikt.Mode
import com.github.arian.gikt.parents
import com.github.arian.gikt.relativeTo
import java.nio.file.Path

class Entry(
    override val name: Path,
    override val mode: Mode = Mode.REGULAR,
    override val oid: ObjectId
) : TreeEntry {

    init {
        require(!name.isAbsolute) { "The path must not be absolute, but relative to the workspace path" }
    }

    override val key: Path by lazy { name.parent?.let { name.relativeTo(it) } ?: name }

    val parents: List<Path> get() = name.parents()

    constructor(
        name: Path,
        stat: FileStat,
        oid: ObjectId
    ) : this(
        name,
        Mode.fromStat(stat),
        oid
    )
}
