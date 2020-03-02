import com.google.common.jimfs.Jimfs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class WorkspaceTest {

    lateinit var path: Path

    @BeforeEach
    fun before() {
        val fs = Jimfs.newFileSystem()
        path = fs.getPath("gitk-workspace")
        Files.createDirectory(path)
    }

    @Test
    fun listFiles() {
        val a = path.resolve("a.txt").touch()
        val b = path.resolve("b.txt").touch()
        path.resolve(".git").mkdirp()
        path.resolve(".git/HEAD").touch()

        val workspace = Workspace(path)

        val files = workspace.listFiles()

        assertEquals(2, files.size)
        assertTrue(files.contains(a))
        assertTrue(files.contains(b))
    }

    @Test
    fun listFilesRecursive() {
        path.resolve(".git").mkdirp()
        path.resolve(".git/HEAD").touch()

        val a = path.resolve("a.txt").touch()
        val b = path.resolve("b.txt").touch()
        path.resolve("a/b/c").mkdirp()
        val c = path.resolve("a/d.txt").touch()
        val d = path.resolve("a/b/c/d.txt").touch()

        val workspace = Workspace(path)
        val files = workspace.listFiles()

        assertTrue(files.contains(a))
        assertTrue(files.contains(b))
        assertTrue(files.contains(c))
        assertTrue(files.contains(d))
        assertEquals(4, files.size)
    }
}

