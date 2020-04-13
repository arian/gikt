package com.github.arian.gikt

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.Deflater
import java.util.zip.DeflaterInputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

fun ByteArray.utf8() = toString(Charsets.UTF_8)

fun ByteArray.sha1(): ByteArray {
    val data = this
    return MessageDigest.getInstance("SHA-1").run {
        update(data)
        digest()
    }
}

fun ByteArray.deflateInto(out: OutputStream) {
    val def = DeflaterInputStream(inputStream(), Deflater())
    out.use { def.copyTo(it) }
}

fun ByteArray.deflate(): ByteArray =
    ByteArrayOutputStream().also { deflateInto(it) }.toByteArray()

fun ByteArray.inflateInto(out: OutputStream) {
    val inf = InflaterInputStream(inputStream(), Inflater())
    out.use { inf.copyTo(it) }
}

fun ByteArray.inflate(): ByteArray =
    ByteArrayOutputStream().also { inflateInto(it) }.toByteArray()
