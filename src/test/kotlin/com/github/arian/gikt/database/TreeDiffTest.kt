package com.github.arian.gikt.database

import com.github.arian.gikt.Workspace
import com.github.arian.gikt.mkdirp
import com.github.arian.gikt.readBytes
import com.github.arian.gikt.relativeTo
import com.github.arian.gikt.stat
import com.github.arian.gikt.test.FileSystemExtension
import com.github.arian.gikt.write
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.nio.file.Path

@ExtendWith(FileSystemExtension::class)
internal class TreeDiffTest(private val fileSystemProvider: FileSystemExtension.FileSystemProvider) {

    private lateinit var rootPath: Path
    private lateinit var database: Database
    private lateinit var workspace: Workspace
    private lateinit var treeDiff: TreeDiff

    @BeforeEach
    fun before() {
        rootPath = fileSystemProvider.get().getPath("gitk-root").mkdirp()
        database = Database(rootPath.resolve(".git/db"))
        workspace = Workspace(rootPath)
        treeDiff = TreeDiff(rootPath.fileSystem, database)
    }

    private fun treeWithFiles(vararg files: Pair<String, String>): Tree {
        val entries = files.map { (name, content) ->
            val p = rootPath.resolve(name)
            p.parent.mkdirp()
            p.write(content)

            val blob = Blob(p.readBytes())
            database.store(blob)
            Entry(p.relativeTo(rootPath), p.stat(), blob.oid)
        }

        val tree = Tree.build(rootPath, entries)
        tree.traverse { database.store(it) }
        database.store(tree)

        return tree
    }

    @Test
    fun `no changes for same tree`() {
        val treeA = treeWithFiles("a.txt" to "")
        val treeB = treeWithFiles("a.txt" to "")

        val diff = treeDiff.compareOids(treeA.oid, treeB.oid)
        assertEquals(emptyMap<Any, Any>(), diff)
    }

    @Test
    fun `file removed`() {
        val treeA = treeWithFiles("a.txt" to "")
        val treeB = treeWithFiles()

        val diff = treeDiff.compareOids(treeA.oid, treeB.oid).toStrings()

        val a = requireNotNull(treeA["a.txt"])
        assertEquals(mapOf("a.txt" to Pair(a.oid.hex, null)), diff)
    }

    @Test
    fun `file added`() {
        val treeA = treeWithFiles()
        val treeB = treeWithFiles("a.txt" to "")

        val diff = treeDiff.compareOids(treeA.oid, treeB.oid).toStrings()

        val a = requireNotNull(treeB["a.txt"])
        assertEquals(mapOf("a.txt" to Pair(null, a.oid.hex)), diff)
    }

    @Test
    fun `file updated`() {
        val treeA = treeWithFiles("a.txt" to "")
        val treeB = treeWithFiles("a.txt" to "a")

        val diff = treeDiff.compareOids(treeA.oid, treeB.oid).toStrings()

        val a = requireNotNull(treeA["a.txt"])
        val b = requireNotNull(treeB["a.txt"])
        assertEquals(mapOf("a.txt" to Pair(a.oid.hex, b.oid.hex)), diff)
    }

    @Test
    fun `not changed file`() {
        val treeA = treeWithFiles("a/x.txt" to "x")
        val treeB = treeWithFiles("a/x.txt" to "x")

        val diff = treeDiff.compareOids(treeA.oid, treeB.oid)
        assertEquals(emptyMap<Any, Any>(), diff)
    }

    @Test
    fun `nested files`() {
        val treeA = treeWithFiles("a/x.txt" to "", "a/b.txt" to "")
        val treeB = treeWithFiles("a/x.txt" to "", "a/b/c.txt" to "a")

        val diff = treeDiff.compareOids(treeA.oid, treeB.oid).toStrings()

        assertEquals(
            mapOf(
                "a/b.txt" to ("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391" to null),
                "a/b/c.txt" to (null to "2e65efe2a145dda7ee51d1741299f848e5bf752e")
            ),
            diff
        )
    }
}

private fun TreeDiffMap.toStrings() =
    mapKeys { (k, _) -> k.toString() }
        .mapValues { (_, diff) ->
            val (from, to) = diff.toHexPair()
            from?.hex to to?.hex
        }
