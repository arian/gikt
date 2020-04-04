package com.github.arian.gikt

import com.github.arian.gikt.database.Author
import com.github.arian.gikt.database.Blob
import com.github.arian.gikt.database.Commit
import com.github.arian.gikt.database.Database
import com.github.arian.gikt.database.Entry
import com.github.arian.gikt.database.Tree
import com.github.arian.gikt.index.Index
import java.io.OutputStream
import java.nio.file.FileSystems
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.zip.Deflater
import java.util.zip.DeflaterInputStream
import kotlin.system.exitProcess

fun getPwd(): Path = FileSystems.getDefault().getPath(".").toAbsolutePath().normalize()

fun ByteArray.sha1(): ByteArray {
    val data = this
    return MessageDigest.getInstance("SHA-1").run {
        update(data)
        digest()
    }
}

fun ByteArray.deflateInto(out: OutputStream) {
    val def = DeflaterInputStream(inputStream(), Deflater())
    out.use { def.copyTo(it) }
}

fun main(args: Array<String>) {

    when (val command = args.getOrNull(0)) {

        "init" -> {
            val rootPath = getPwd()
            val gitPath = rootPath.resolve(".git")

            listOf("objects", "refs").forEach {
                val path = gitPath.resolve(it)
                try {
                    path.mkdirp()
                } catch (e: Exception) {
                    System.err.println("fatal: ${e.message}")
                    exitProcess(1)
                }
            }

            println("Initialized empty Gikt repository in $rootPath")
        }

        "commit" -> {
            val name = System.getenv("GIT_AUTHOR_NAME") ?: error("please set GIT_AUTHOR_NAME")
            val email = System.getenv("GIT_AUTHOR_EMAIL") ?: error("please set GIT_AUTHOR_EMAIL")
            val author = Author(
                name,
                email,
                Instant.now().atZone(Clock.systemDefaultZone().zone)
            )
            val message: ByteArray = System.`in`.readAllBytes()
            val firstLine = message.toString(Charsets.UTF_8).split("\n").getOrNull(0) ?: ""

            if (firstLine.isBlank()) {
                System.err.println("gikt: empty commit message")
                exitProcess(1)
            }

            val rootPath = getPwd()
            val gitPath = rootPath.resolve(".git")
            val indexPath = gitPath.resolve("index")
            val dbPath = gitPath.resolve("objects")

            val database = Database(dbPath)
            val index = Index(indexPath)
            val refs = Refs(gitPath)

            val entries = index.load().toList().map {
                val path = rootPath.resolve(it.key).relativeTo(rootPath)
                Entry(path, it.stat, it.oid)
            }

            val root = Tree.build(rootPath, entries)

            root.traverse { database.store(it) }

            val parent = refs.readHead()

            val commit = Commit(parent, root.oid, author, message)
            database.store(commit)
            refs.updateHead(commit.oid)

            val isRoot = parent?.let { "" } ?: "(root-commit) "
            println("[$isRoot${commit.oid.hex} $firstLine")
        }

        "add" -> {
            val rootPath = getPwd()
            val gitPath = rootPath.resolve(".git")
            val dbPath = gitPath.resolve("objects")
            val indexPath = gitPath.resolve("index")

            val workspace = Workspace(rootPath)
            val database = Database(dbPath)
            val index = Index(indexPath)

            try {
                index.loadForUpdate { lock ->

                    val paths = try {
                        args.drop(1)
                            .flatMap {
                                val path = rootPath.resolve(it)
                                workspace.listFiles(path)
                            }
                    } catch (e: Workspace.MissingFile) {
                        System.err.println("fatal: ${e.message}")
                        lock.rollback()
                        exitProcess(128)
                    }

                    try {
                        paths.forEach {
                            val path = rootPath.resolve(it)
                            val data = workspace.readFile(path)
                            val stat = workspace.statFile(path)

                            val blob = Blob(data)
                            database.store(blob)
                            index.add(path.relativeTo(rootPath), blob.oid, stat)
                        }
                    } catch (e: Workspace.NoPermission) {
                        System.err.println("error: ${e.message}")
                        System.err.println("fatal: adding files failed")
                        lock.rollback()
                        exitProcess(128)
                    }

                    index.writeUpdates(lock)
                }
            } catch (e: Lockfile.LockDenied) {
                System.err.println("""
                    fatal: ${e.message}

                    Another gikt process seems to be running in this repository.
                    Please make sure all processes are terminated then try again.
                    If it still fails, a gikt process may have crashed in this
                    repository earlier: remove the file manually to continue.
                """.trimIndent())
                exitProcess(1)
            }

            exitProcess(0)
        }

        "hello" -> {
            println("gikt: hello")
        }

        "ls" -> {
            val rootPath = getPwd()
            val workspace = Workspace(rootPath)
            workspace.listFiles().forEach { println(it) }
        }

        "ls-files" -> {
            val rootPath = getPwd()
            val gitPath = rootPath.resolve(".git")
            val indexPath = gitPath.resolve("index")
            val index = Index(indexPath)

            index.load().forEach { println(it.key) }
        }

        else -> {
            System.err.println("gikt: '$command' is not a gikt command")
            exitProcess(1)
        }
    }
}
