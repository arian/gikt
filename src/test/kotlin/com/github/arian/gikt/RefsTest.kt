package com.github.arian.gikt

import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.test.FileSystemExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Path

@ExtendWith(FileSystemExtension::class)
class RefsTest(private val fileSystemProvider: FileSystemExtension.FileSystemProvider) {

    private lateinit var git: Path

    @BeforeEach
    fun before() {
        git = fileSystemProvider.get().getPath("/foo/bar/gitk").mkdirp()
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
    fun `readHead commit ID oid`() {
        val oid = ObjectId("abcd")
        val refs = Refs(git)
        refs.updateHead(oid)
        assertEquals(oid, refs.readHead())
    }

    @Test
    fun `readHead with symbolic ref`() {
        val oid = ObjectId("abcd")

        val refs = Refs(git)
        refs.createBranch("topic", oid)
        refs.setHead("topic", oid)

        assertEquals(oid, refs.readHead())
    }

    @Test
    fun `currentRef returns the oid if the hash is directly in the HEAD`() {
        val oid = ObjectId("abcd")
        git.resolve("HEAD").write(oid.hex)
        val refs = Refs(git)
        assertEquals(oid, refs.currentRef().oid)
        assertEquals("HEAD", refs.currentRef().shortName)
    }

    @Test
    fun `currentRef returns the oid of the sym ref`() {
        git.resolve("HEAD").write("abc123")
        val refs = Refs(git)

        val oid = ObjectId("abcd")
        refs.createBranch("topic", oid)
        refs.setHead("topic", oid)

        assertEquals(oid, refs.currentRef().oid)
        assertEquals("topic", refs.currentRef().shortName)
    }

    @Test
    fun `listBranches should list the created branches alphabetically`() {
        val refs = Refs(git)
        val oid = ObjectId("abcd")
        refs.createBranch("topic", oid)
        refs.createBranch("bar", oid)
        refs.createBranch("foo/qux", oid)

        assertEquals(
            listOf("refs/heads/bar", "refs/heads/foo/qux", "refs/heads/topic"),
            refs.listBranches().map { it.longName }
        )
    }

    @Test
    fun `listBranches SymRef shortName property just shows the branch name`() {
        val refs = Refs(git)
        val oid = ObjectId("abcd")
        refs.createBranch("topic", oid)
        refs.createBranch("bar", oid)
        refs.createBranch("foo/qux", oid)

        assertEquals(
            listOf("bar", "foo/qux", "topic"),
            refs.listBranches().map { it.shortName }
        )
    }

    @Test
    fun `reverseRefs returns a map of oids to its associated references`() {
        val refs = Refs(git)
        val oid1 = ObjectId("abcd")
        val oid2 = ObjectId("ef12")
        refs.createBranch("topic", oid1)
        refs.createBranch("bar", oid1)
        refs.createBranch("foo/qux", oid2)

        val head = ObjectId("def12def12def12def12def12def12def12def12")
        git.resolve("HEAD").write(head.hex)

        assertEquals(
            mapOf(
                oid1 to listOf("bar", "topic"),
                oid2 to listOf("foo/qux"),
                head to listOf("HEAD")
            ),
            refs.reverseRefs().mapValues { list -> list.value.map { it.shortName } }
        )
    }

    @Test
    fun `delete branch`() {
        val refs = Refs(git)
        val oid = ObjectId("abcd")
        refs.createBranch("topic", oid)
        refs.deleteBranch("topic")
        assertEquals(emptyList<Any>(), refs.listBranches())
    }

    @Test
    fun `delete unknown branch`() {
        val refs = Refs(git)
        val oid = ObjectId("abcd")
        refs.createBranch("topic", oid)
        val e = assertThrows<Refs.InvalidBranch> { refs.deleteBranch("bla") }
        assertEquals("branch 'bla' not found.", e.message)
    }
}
