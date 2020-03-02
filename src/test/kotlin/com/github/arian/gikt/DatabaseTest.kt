package com.github.arian.gikt

import com.github.arian.gikt.database.Blob
import com.google.common.jimfs.Jimfs
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals

class DatabaseTest {

    lateinit var workspace: Path

    @BeforeEach
    fun before() {
        val fs = Jimfs.newFileSystem()
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

