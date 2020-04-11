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

    private lateinit var workspace: Path

    @BeforeEach
    fun before() {
        val fs = fileSystemProvider.get()
        workspace = fs.getPath("gitk-objects")
        Files.createDirectory(workspace)
    }

    @Test
    fun store() {
        val database = Database(workspace)

        val blob = Blob("hello".toByteArray())

        database.store(blob)

        val obj = workspace.resolve("b6/fc4c620b67d95f953a5c1c1230aaab5db5a1b0")
        assertTrue(obj.exists())

        val timeBefore = Files.getLastModifiedTime(obj)

        database.store(blob)

        val timeAfter = Files.getLastModifiedTime(obj)

        assertEquals(timeBefore, timeAfter, "should not have modified the file")
    }
}
