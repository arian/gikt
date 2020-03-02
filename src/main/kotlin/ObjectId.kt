private val HEX_ARRAY = "0123456789abcdef".toCharArray()

internal fun ByteArray.toHexString(): String {
    return this
        .map { it.toInt() and 0xFF }
        .flatMap { listOf(HEX_ARRAY[it ushr 4], HEX_ARRAY[it and 0x0F]) }
        .let { String(it.toCharArray()) }
}

internal fun String.hexToByteArray() =
    ByteArray(length / 2) {
        ((Character.digit(this[it * 2], 16) shl 4) + Character.digit(this[it * 2 + 1], 16)).toByte()
    }

class ObjectId(val bytes: ByteArray) {
    constructor(bytes: String) : this(bytes.hexToByteArray())

    val hex: String get() = bytes.toHexString()

    override fun toString() = hex
}
