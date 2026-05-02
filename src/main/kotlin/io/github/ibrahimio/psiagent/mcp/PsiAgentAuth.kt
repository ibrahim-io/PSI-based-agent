package io.github.ibrahimio.psiagent.mcp

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.UUID

object PsiAgentAuth {

    fun defaultTokenDirectory(): Path = Paths.get(System.getProperty("user.home"), ".psi-agent")

    fun tokenFile(tokenDirectory: Path = defaultTokenDirectory()): Path = tokenDirectory.resolve("token")

    fun ensureToken(tokenDirectory: Path = defaultTokenDirectory()): String {
        Files.createDirectories(tokenDirectory)
        val existing = readToken(tokenDirectory)
        if (!existing.isNullOrBlank()) return existing

        val token = UUID.randomUUID().toString().replace("-", "")
        Files.writeString(
            tokenFile(tokenDirectory),
            token,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
        return token
    }

    fun readToken(tokenDirectory: Path = defaultTokenDirectory()): String? {
        val file = tokenFile(tokenDirectory)
        if (!Files.exists(file)) return null
        return Files.readString(file).trim().takeIf { it.isNotBlank() }
    }
}

