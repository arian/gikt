package com.github.arian.gikt

import com.github.arian.gikt.database.ObjectId
import com.github.arian.gikt.test.FileSystemExtension
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(FileSystemExtension::class)
class RefsTest(private val fileSystemProvider: FileSystemExtension.FileSystemProvider) {

    private lateinit var workspace: Path

    @BeforeEach
    fun before() {
        workspace = fileSystemProvider.get().getPath("gitk").mkdirp()
    }

    @Test
    fun `create invalid branch names`() {
        val refs = Refs(workspace)
        val e = assertThrows<Refs.InvalidBranch> { refs.createBranch(".", ObjectId("abc")) }
        assertEquals("'.' is not a valid branch name.", e.message)
    }

    @Test
    fun `create valid branch names`() {
        workspace.resolve("HEAD").write("abc123")
        val refs = Refs(workspace)
        refs.createBranch("topic", ObjectId("abc"))
    }

    @Test
    fun `should throw when creating an existing branch`() {
        workspace.resolve("HEAD").write("abc123")
        val refs = Refs(workspace)
        refs.createBranch("topic", ObjectId(""))
        val e = assertThrows<Refs.InvalidBranch> { refs.createBranch("topic", ObjectId("")) }
        assertEquals("A branch named 'topic' already exists.", e.message)
    }
}
