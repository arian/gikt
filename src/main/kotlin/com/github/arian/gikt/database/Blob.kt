package com.github.arian.gikt.database

data class Blob(
    override val data: ByteArray
) : GiktObject() {
    override val type = "blob"

    override fun equals(other: Any?): Boolean =
        super.equals(other)

    override fun hashCode(): Int =
        super.hashCode()

    companion object {
        fun parse(bytes: ByteArray): Blob =
            Blob(bytes)
    }
}
