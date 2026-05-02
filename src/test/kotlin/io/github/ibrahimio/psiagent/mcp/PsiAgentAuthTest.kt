package io.github.ibrahimio.psiagent.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class PsiAgentAuthTest {

    @Test
    fun ensureTokenCreatesAndReusesTokenFile() {
        val tokenDir = Files.createTempDirectory("psi-agent-auth-test")

        val first = PsiAgentAuth.ensureToken(tokenDir)
        assertNotNull(first)
        assertTrue(first.isNotBlank())
        assertEquals(first, PsiAgentAuth.readToken(tokenDir))
        assertEquals(tokenDir.resolve("token"), PsiAgentAuth.tokenFile(tokenDir))
        assertTrue(Files.exists(PsiAgentAuth.tokenFile(tokenDir)))

        val second = PsiAgentAuth.ensureToken(tokenDir)
        assertEquals(first, second)
    }
}

