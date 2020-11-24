package com.github.arian.gikt

import com.github.arian.gikt.Revision.Companion.resolve
import com.github.arian.gikt.Revision.Res
import com.github.arian.gikt.Revision.Rev
import com.github.arian.gikt.database.Author
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.repository.Repository
import com.github.arian.gikt.test.FileSystemExtension
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
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

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

        private lateinit var ws: Path
        private lateinit var git: Path

        @BeforeEach
        fun before() {
            ws = fileSystemProvider.get().getPath("gitk-objects").mkdirp()
            git = ws.resolve(".git").mkdirp()
        }

        private fun commit(parent: ObjectId? = null): Commit {
            val zoneId = ZoneId.of("Europe/Amsterdam")
            val time = ZonedDateTime.now(Clock.fixed(Instant.parse("2019-08-14T10:08:22.00Z"), zoneId))
            return Commit(
                parents = listOfNotNull(parent),
                message = "commit".toByteArray(),
                author = Author("arian", "arian@example.com", time),
                tree = ObjectId("abc12def12def12def12def12def12def12def12")
            )
        }

        @Test
        fun `resolve HEAD id`() {
            val repo = Repository(ws)
            val commit = commit()
            repo.database.store(commit)
            git.resolve("HEAD").write(commit.oid.hex)
            val oid = Rev.Ref("HEAD").resolve(repo)
            assertEquals(Res.Commit(commit), oid)
        }

        @Test
        fun `resolve name from refs`() {
            val repo = Repository(ws)
            val commit = commit()
            repo.database.store(commit)
            git.resolve("refs").mkdirp()
            git.resolve("refs/master").write(commit.oid.hex)
            val oid = Rev.Ref("master").resolve(repo)
            assertEquals(Res.Commit(commit), oid)
        }

        @Test
        fun `resolve name from heads`() {
            val repo = Repository(ws)
            val commit = commit()
            repo.database.store(commit)
            git.resolve("refs/heads").mkdirp()
            git.resolve("refs/heads/master").write(commit.oid.hex)
            val oid = Rev.Ref("master").resolve(repo)
            assertEquals(Res.Commit(commit), oid)
        }

        @Test
        fun `resolve parent HEAD id`() {
            val repo = Repository(ws)

            val root = commit()
            repo.database.store(root)

            val commit = commit(root.oid)
            repo.database.store(commit)

            git.resolve("HEAD").write(commit.oid.hex)

            val oid = Revision.parse("HEAD^")?.resolve(repo)
            assertEquals(Res.Commit(root), oid)
        }

        @Test
        fun `resolve ancestor HEAD id`() {
            val repo = Repository(ws)

            val root = commit()
            repo.database.store(root)

            val commit = commit(root.oid)
            repo.database.store(commit)
            git.resolve("HEAD").write(commit.oid.hex)

            val oid = Revision.parse("HEAD~1")?.resolve(repo)
            assertEquals(Res.Commit(root), oid)
        }

        @Test
        fun `resolve second ancestor HEAD id`() {
            val repo = Repository(ws)

            val root = commit()
            repo.database.store(root)

            val first = commit(root.oid)
            repo.database.store(first)

            val second = commit(first.oid)
            repo.database.store(second)

            git.resolve("HEAD").write(second.oid.hex)

            val oid = Revision.parse("HEAD~2")?.resolve(repo)
            assertEquals(Res.Commit(root), oid)
        }

        @Test
        fun `Revision resolve`() {
            val repo = Repository(ws)
            val root = commit()
            repo.database.store(root)
            git.resolve("HEAD").write(root.oid.hex)
            val oid = Revision(repo, "HEAD").oid
            assertEquals(root.oid, oid)
        }

        @Test
        fun `Revision not found exception`() {
            val repo = Repository(ws)
            val e = assertThrows<Revision.InvalidObject> { Revision(repo, "HEAD").oid }
            assertEquals("Not a valid object name: 'HEAD'", e.message)
        }

        @Test
        fun `resolve from object id`() {
            val repo = Repository(ws)
            val first = commit()
            repo.database.store(first)
            val oid = Revision(repo, first.oid.hex).oid
            assertEquals(first.oid.hex, oid.hex)
        }

        @Test
        fun `resolve from short object id`() {
            val repo = Repository(ws)
            val first = commit()
            repo.database.store(first)
            val oid = Revision(repo, first.oid.short).oid
            assertEquals(first.oid.hex, oid.hex)
        }

        @Test
        fun `resolve from ambiguous short object id`() {
            val repo = Repository(ws)

            val first = commit()
            repo.database.store(first)
            assertEquals(ObjectId("27ca1a59bdd14378635f344ef3bb0563eda96242"), first.oid)

            git.resolve("objects/27/ca1a59bdd14378635f344ef3bb0563eda96242")
                .copyTo(git.resolve("objects/27/ca1a59bdd14378635f344ef3bb0563eda962aa"))

            val e = assertThrows<Revision.InvalidObject> { Revision(repo, first.oid.short).oid }
            assertEquals(1, e.errors.size)
        }
    }
}
