package com.github.arian.gikt.commands.util

enum class Style(internal val code: Int) {
    BOLD(1),
    RED(31),
    GREEN(32),
    YELLOW(33),
    CYAN(36);

    fun format(string: String): String = format(code.toString(), string)
}

private const val RESET = "\u001B[m"

private fun sgr(code: String): String = "\u001B[${code}m"

fun List<Style>.format(string: String): String =
    format(map { it.code }.joinToString(separator = ";"), string)

fun format(code: String, string: String): String = "${sgr(code)}$string$RESET"
