package com.github.arian.gikt

import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.test.FileSystemExtension
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(FileSystemExtension::class)
class RefsTest(private val fileSystemProvider: FileSystemExtension.FileSystemProvider) {

    private lateinit var git: Path

    @BeforeEach
    fun before() {
        git = fileSystemProvider.get().getPath("gitk").mkdirp()
    }

    @Test
    fun `readHead returns null if there is no HEAD file`() {
        val refs = Refs(git)
        assertNull(refs.readHead())
    }

    @Test
    fun `readHead returns the oid from the git HEAD file`() {
        val refs = Refs(git)
        val root = ObjectId("def12def12def12def12def12def12def12def12")
        git.resolve("HEAD").write(root.hex)
        assertEquals(root, refs.readHead())
    }

    @Test
    fun `create invalid branch names`() {
        val refs = Refs(git)
        val e = assertThrows<Refs.InvalidBranch> { refs.createBranch(".", ObjectId("abc")) }
        assertEquals("'.' is not a valid branch name.", e.message)
    }

    @Test
    fun `create valid branch names`() {
        git.resolve("HEAD").write("abc123")
        val refs = Refs(git)
        refs.createBranch("topic", ObjectId("abc"))
    }

    @Test
    fun `should throw when creating an existing branch`() {
        git.resolve("HEAD").write("abc123")
        val refs = Refs(git)
        refs.createBranch("topic", ObjectId(""))
        val e = assertThrows<Refs.InvalidBranch> { refs.createBranch("topic", ObjectId("")) }
        assertEquals("A branch named 'topic' already exists.", e.message)
    }
}
