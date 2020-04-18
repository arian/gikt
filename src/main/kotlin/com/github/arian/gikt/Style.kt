package com.github.arian.gikt

enum class Style(private val code: Int) {
    RED(31),
    GREEN(32);

    private fun sgr(): String = "\u001B[${code}m"

    fun format(string: String): String = "${sgr()}$string$RESET"

    companion object {
        private const val RESET = "\u001B[m"
    }
}
