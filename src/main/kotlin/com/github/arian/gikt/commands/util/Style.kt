package com.github.arian.gikt.commands.util

enum class Style(private val code: Int) {
    BOLD(1),
    RED(31),
    GREEN(32),
    CYAN(36);

    private fun sgr(): String = "\u001B[${code}m"

    fun format(string: String): String = "${sgr()}$string$RESET"

    companion object {
        private const val RESET = "\u001B[m"
    }
}
