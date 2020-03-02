class Blob(
    override val data: ByteArray
) : GiktObject() {
    override val type = "blob"
}
