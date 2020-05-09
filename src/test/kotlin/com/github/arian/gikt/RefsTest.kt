package com.github.arian.gikt

import com.github.arian.gikt.test.FileSystemExtension
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@ExtendWith(FileSystemExtension::class)
class RefsTest(private val fileSystemProvider: FileSystemExtension.FileSystemProvider) {

    private lateinit var workspace: Path

    @BeforeEach
    fun before() {
        workspace = fileSystemProvider.get().getPath("gitk").mkdirp()
    }

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
    fun `create invalid branch names`(branchName: String) {
        val refs = Refs(workspace)
        val e = assertThrows<Refs.InvalidBranch> { refs.createBranch(branchName) }
        assertEquals("'$branchName' is not a valid branch name.", e.message)
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
    fun `create valid branch names`(branchName: String) {
        workspace.resolve("HEAD").write("abc123")
        val refs = Refs(workspace)
        refs.createBranch(branchName)
    }

    @Test
    fun `should throw when creating an existing branch`() {
        workspace.resolve("HEAD").write("abc123")
        val refs = Refs(workspace)
        refs.createBranch("topic")
        val e = assertThrows<Refs.InvalidBranch> { refs.createBranch("topic") }
        assertEquals("A branch named 'topic' already exists.", e.message)
    }
}
