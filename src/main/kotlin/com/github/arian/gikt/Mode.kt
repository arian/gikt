package com.github.arian.gikt

enum class Mode(val mode: String, val asInt: Int) {
    REGULAR("100644", 33188),
    EXECUTABLE("100755", 33261),
    TREE("40000", 16384);

    companion object {

        fun parse(name: String): Mode? = values().find { it.mode == name }

        fun fromInt(value: Int): Mode? = values().find { it.asInt == value }

        fun fromStat(stat: FileStat): Mode =
            when {
                stat.directory -> TREE
                stat.executable -> EXECUTABLE
                else -> REGULAR
            }
    }
}
