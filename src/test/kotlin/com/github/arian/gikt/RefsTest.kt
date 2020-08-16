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
        val oid = ObjectId("abcd")
        refs.createBranch("topic", oid)
        assertEquals("abc123", git.resolve("HEAD").readText())
        assertEquals("${oid.hex}\n", git.resolve("refs/heads/topic").readText())
    }

    @Test
    fun `should throw when creating an existing branch`() {
        git.resolve("HEAD").write("abc123")
        val refs = Refs(git)
        refs.createBranch("topic", ObjectId(""))
        val e = assertThrows<Refs.InvalidBranch> { refs.createBranch("topic", ObjectId("")) }
        assertEquals("A branch named 'topic' already exists.", e.message)
    }

    @Test
    fun `setHead to a branch name should create a symbolic reference`() {
        git.resolve("HEAD").write("abc123")
        val oid = ObjectId("abc")

        val refs = Refs(git)
        refs.createBranch("topic", oid)
        refs.setHead("topic", oid)

        val headText = git.resolve("HEAD").readText()
        assertEquals("ref: refs/heads/topic\n", headText)
    }

    @Test
    fun `setHead to a non-existing name should store the commit ID oid`() {
        git.resolve("HEAD").write("abc123")
        val refs = Refs(git)
        val oid = ObjectId("abcd")
        refs.setHead("topic", oid)
        assertEquals("${oid.hex}\n", git.resolve("HEAD").readText())
    }

    @Test
    fun `readOidOrSymRef commit ID oid`() {
        val oid = ObjectId("abcd")
        val refs = Refs(git)
        refs.updateHead(oid)
        assertEquals(oid, refs.readHead())
    }

    @Test
    fun `readOidOrSymRef symbolic ref`() {
        val oid = ObjectId("abcd")

        val refs = Refs(git)
        refs.createBranch("topic", oid)
        refs.setHead("topic", oid)

        assertEquals(oid, refs.readHead())
    }
}
