package com.github.arian.gikt

import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.Database
import com.github.arian.gikt.test.FileSystemExtension
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(FileSystemExtension::class)
class DatabaseTest(private val fileSystemProvider: FileSystemExtension.FileSystemProvider) {

    private lateinit var db: Path

    @BeforeEach
    fun before() {
        db = fileSystemProvider.get().getPath("gitk-objects").mkdirp()
    }

    @Test
    fun store() {
        val database = Database(db)

        val blob = Blob("hello".toByteArray())

        database.store(blob)

        val obj = db.resolve("b6/fc4c620b67d95f953a5c1c1230aaab5db5a1b0")
        assertTrue(obj.exists())

        val timeBefore = Files.getLastModifiedTime(obj)

        database.store(blob)

        val timeAfter = Files.getLastModifiedTime(obj)

        assertEquals(timeBefore, timeAfter, "should not have modified the file")
    }

    @Test
    fun `prefixMatch should return possible ObjectIds from the database`() {
        val aa = db.resolve("aa").mkdirp()
        aa.resolve("0123456789abcdef0123456789abcdef012345").touch()
        aa.resolve("0123456789abcdef0123456789abcdef012346").touch()

        val database = Database(db)
        val matches = database.prefixMatch("aa01234")
        assertEquals(2, matches.size)
    }

    @Test
    fun `prefixMatch should return empty list for not-existing pats`() {
        val database = Database(db)
        val matches = database.prefixMatch("aa01234")
        assertEquals(0, matches.size)
    }
}
