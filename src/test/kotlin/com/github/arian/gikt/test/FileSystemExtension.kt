package com.github.arian.gikt.test

import com.github.marschall.memoryfilesystem.MemoryFileSystemBuilder
import java.nio.file.FileSystem
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

class FileSystemExtension : BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private var fileSystem: FileSystem? = null

    inner class FileSystemProvider {
        fun get() = checkNotNull(fileSystem)
    }

    override fun beforeEach(context: ExtensionContext) {
        fileSystem = MemoryFileSystemBuilder.newLinux().build()
    }

    override fun afterEach(context: ExtensionContext) {
        fileSystem?.close()
    }

    override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return parameterContext.parameter.type.isAssignableFrom(FileSystemProvider::class.java)
    }

    override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
        return FileSystemProvider()
    }
}
