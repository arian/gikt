package com.github.arian.gikt

import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.TreeEntry
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
): TreeEntry {
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
