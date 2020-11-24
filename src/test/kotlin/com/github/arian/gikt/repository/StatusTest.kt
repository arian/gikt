package com.github.arian.gikt.repository

import com.github.arian.gikt.createDirectory
import com.github.arian.gikt.test.FileSystemExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Path

@ExtendWith(FileSystemExtension::class)
class StatusTest(private val fileSystemProvider: FileSystemExtension.FileSystemProvider) {

    private lateinit var path: Path

    @BeforeEach
    fun before() {
        path = fileSystemProvider.get().getPath("gitk-workspace").createDirectory()
    }

    @Test
    fun `status of empty`() {
        val repository = Repository(path)
        val status = Status(repository)
        val scan = status.scan(repository.index.load())

        assertEquals(emptySet<String>(), scan.changes.all())
        assertEquals(emptySet<String>(), scan.untracked)
    }
}
