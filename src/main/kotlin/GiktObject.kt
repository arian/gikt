abstract class GiktObject {
    abstract val type: String
    abstract val data: ByteArray
    val oid: ObjectId by lazy { ObjectId(content.sha1()) }
    val content: ByteArray by lazy { "$type ${data.size}".toByteArray() + 0 + data }
}
