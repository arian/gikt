package com.github.arian.gikt.repository

import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.deflate
import com.github.arian.gikt.mkdirp
import com.github.arian.gikt.test.FileSystemExtension
import com.github.arian.gikt.write
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(FileSystemExtension::class)
class RepositoryTest(private val fileSystemProvider: FileSystemExtension.FileSystemProvider) {

    private lateinit var ws: Path

    @BeforeEach
    fun before() {
        ws = fileSystemProvider.get().getPath("gitk-objects").mkdirp()
    }

    @Test
    fun `load object`() {
        val repo = Repository(ws)
        val blob = Blob("".toByteArray(Charsets.UTF_8))
        repo.database.store(blob)

        val loaded = repo.loadObject(blob.oid)

        assertEquals(blob, loaded)
    }

    @Test
    fun `load bad object`() {
        val repo = Repository(ws)
        assertThrows<Repository.BadObject> {
            repo.loadObject(ObjectId("b6fc4c620b67d95f953a5c1c1230aaab5db5a1b0"))
        }
    }

    @Test
    fun `load bad object that contains un-inflatable stuff`() {
        val repo = Repository(ws)

        ws.resolve(".git/objects/b6")
            .mkdirp()
            .resolve("fc4c620b67d95f953a5c1c1230aaab5db5a1b0")
            .write("foo")

        assertThrows<Repository.BadObject> {
            repo.loadObject(ObjectId("b6fc4c620b67d95f953a5c1c1230aaab5db5a1b0"))
        }
    }

    @Test
    fun `load bad object that contains un-parsable stuff`() {
        val repo = Repository(ws)

        ws.resolve(".git/objects/b6")
            .mkdirp()
            .resolve("fc4c620b67d95f953a5c1c1230aaab5db5a1b0")
            .write("foo".toByteArray(Charsets.UTF_8).deflate())

        assertThrows<Repository.BadObject> {
            repo.loadObject(ObjectId("b6fc4c620b67d95f953a5c1c1230aaab5db5a1b0"))
        }
    }
}
