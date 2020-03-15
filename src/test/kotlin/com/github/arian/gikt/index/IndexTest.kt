package com.github.arian.gikt.index

import com.github.arian.gikt.FileStat
import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.database.toHexString
import com.github.arian.gikt.readBytes
import com.github.arian.gikt.touch
import com.google.common.jimfs.Jimfs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndexTest {

    private lateinit var workspacePath: Path

    @BeforeEach
    fun before() {
        val fs = Jimfs.newFileSystem()
        workspacePath = fs.getPath("gitk-index")
        Files.createDirectory(workspacePath)
    }

    @Test
    fun createIndex() {
        val indexPath = workspacePath.resolve("index")
        val index = Index(workspacePath, indexPath)
        val path = workspacePath.resolve("a.txt").touch()

        index.add(
            path,
            ObjectId("1234512345123451234512345123451234512345"),
            FileStat(executable = false)
        )

        val success = index.writeUpdates()
        assertTrue(success)

        val result = indexPath.readBytes()
        val resultString = result.toString(Charsets.UTF_8)
        assertEquals("DIRC", resultString.substring(0, 4))

        assertEquals(
            "44 49 52 43 00 00 00 02 00 00 00 01".replace(" ", ""),
            result.toHexString().substring(0, 24)
        )

        assertTrue(resultString.contains("a.txt"))
    }

    @Test
    fun `return false when index is locked`() {
        val index = Index(workspacePath, workspacePath.resolve("index"))
        workspacePath.resolve("index.lock").touch()
        val success = index.writeUpdates()
        assertFalse(success)
    }

}