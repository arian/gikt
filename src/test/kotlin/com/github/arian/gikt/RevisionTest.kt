package com.github.arian.gikt

import com.github.arian.gikt.Revision.Rev
import com.github.arian.gikt.database.Author
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.repository.Repository
import com.github.arian.gikt.test.FileSystemExtension
import java.nio.file.Path
import java.time.ZonedDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class RevisionTest {

    @ParameterizedTest
    @ValueSource(
        strings = [
            ".",
            ".name",
            "a..b",
            "a.lock",
            "topic/",
            "a b",
            "a\tb",
            "a\nb",
            "a\u007f",
            "a^"
        ]
    )
    fun `invalid revision names`(name: String) {
        assertFalse(Revision.validRef(name))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "topic",
            "a/b",
            "a-b",
            "a.b"
        ]
    )
    fun `valid revision names`(name: String) {
        assertTrue(Revision.validRef(name))
    }

    @Test
    fun `parse ref`() {
        assertEquals(
            Rev.Ref("HEAD"),
            Revision.parse("HEAD")
        )
    }

    @Test
    fun `parse head alias`() {
        assertEquals(
            Rev.Ref("HEAD"),
            Revision.parse("@")
        )
    }

    @Test
    fun `parse parent`() {
        assertEquals(
            Rev.Parent(Rev.Ref("HEAD")),
            Revision.parse("@^")
        )
    }

    @Test
    fun `parse grand parent`() {
        assertEquals(
            Rev.Parent(Rev.Parent(Rev.Ref("HEAD"))),
            Revision.parse("HEAD^^")
        )
    }

    @Test
    fun `parse ancestor`() {
        assertEquals(
            Rev.Ancestor(Rev.Ref("HEAD"), 42),
            Revision.parse("HEAD~42")
        )
    }

    @Test
    fun `parse ancestor of parent`() {
        assertEquals(
            Rev.Parent(Rev.Ancestor(Rev.Ref("abc123"), 3)),
            Revision.parse("abc123~3^")
        )
    }

    @Nested
    @ExtendWith(FileSystemExtension::class)
    inner class Resolve(private val fileSystemProvider: FileSystemExtension.FileSystemProvider) {

        private lateinit var git: Path

        @BeforeEach
        fun before() {
            git = fileSystemProvider.get().getPath("gitk-objects").mkdirp()
        }

        @Test
        fun `resolve HEAD id`() {
            val repo = Repository(git)
            val hex = "abc12abc12abc12abc12abc12abc12abc12abc12"
            git.resolve(".git").mkdirp()
            git.resolve(".git/HEAD").write(hex)
            val oid = Revision.resolve(Rev.Ref("HEAD"), repo)
            assertEquals(hex, oid?.hex)
        }

        @Test
        fun `resolve name from refs`() {
            val repo = Repository(git)
            val hex = "abc12abc12abc12abc12abc12abc12abc12abc12"
            git.resolve(".git/refs").mkdirp()
            git.resolve(".git/refs/master").write(hex)
            val oid = Revision.resolve(Rev.Ref("master"), repo)
            assertEquals(hex, oid?.hex)
        }

        @Test
        fun `resolve name from heads`() {
            val repo = Repository(git)
            val hex = "abc12abc12abc12abc12abc12abc12abc12abc12"
            git.resolve(".git/refs/heads").mkdirp()
            git.resolve(".git/refs/heads/master").write(hex)
            val oid = Revision.resolve(Rev.Ref("master"), repo)
            assertEquals(hex, oid?.hex)
        }

        @Test
        fun `resolve parent HEAD id`() {
            git.resolve(".git").mkdirp()
            val repo = Repository(git)

            val root = "def12def12def12def12def12def12def12def12"
            val commit = Commit(
                parent = ObjectId(root),
                message = "commit".toByteArray(),
                author = Author("author", "auth@or.com", ZonedDateTime.now()),
                tree = ObjectId("abc12def12def12def12def12def12def12def12")
            )
            repo.database.store(commit)
            git.resolve(".git/HEAD").write(commit.oid.hex)

            val oid = Revision.parse("HEAD^")?.let { Revision.resolve(it, repo) }
            assertEquals(root, oid?.hex)
        }

        @Test
        fun `resolve ancestor HEAD id`() {
            git.resolve(".git").mkdirp()
            val repo = Repository(git)

            val root = "def12def12def12def12def12def12def12def12"
            val commit = Commit(
                parent = ObjectId(root),
                message = "commit".toByteArray(),
                author = Author("author", "auth@or.com", ZonedDateTime.now()),
                tree = ObjectId("abc12def12def12def12def12def12def12def12")
            )
            repo.database.store(commit)
            git.resolve(".git/HEAD").write(commit.oid.hex)

            val oid = Revision.parse("HEAD~1")?.let { Revision.resolve(it, repo) }
            assertEquals(root, oid?.hex)
        }

        @Test
        fun `resolve second ancestor HEAD id`() {
            git.resolve(".git").mkdirp()
            val repo = Repository(git)

            val root = "def12def12def12def12def12def12def12def12"
            val first = Commit(
                parent = ObjectId(root),
                message = "commit".toByteArray(),
                author = Author("author", "auth@or.com", ZonedDateTime.now()),
                tree = ObjectId("abc12def12def12def12def12def12def12def12")
            )
            repo.database.store(first)

            val second = Commit(
                parent = first.oid,
                message = "commit".toByteArray(),
                author = Author("author", "auth@or.com", ZonedDateTime.now()),
                tree = ObjectId("abc12def12def12def12def12def12def12def12")
            )
            repo.database.store(second)

            git.resolve(".git/HEAD").write(second.oid.hex)

            val oid = Revision.parse("HEAD~2")?.let { Revision.resolve(it, repo) }
            assertEquals(root, oid?.hex)
        }

        @Test
        fun `Revision resolve`() {
            git.resolve(".git").mkdirp()
            val repo = Repository(git)
            val root = "def12def12def12def12def12def12def12def12"
            git.resolve(".git/HEAD").write(root)
            val oid = Revision(repo, "HEAD").resolve()
            assertEquals(root, oid.hex)
        }

        @Test
        fun `Revision not found exception`() {
            git.resolve(".git").mkdirp()
            val repo = Repository(git)
            val e = assertThrows<Revision.InvalidObject> { Revision(repo, "HEAD").resolve() }
            assertEquals("Not a valid object name: 'HEAD'", e.message)
        }
    }
}
