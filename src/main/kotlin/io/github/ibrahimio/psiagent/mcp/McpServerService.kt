package io.github.ibrahimio.psiagent.mcp

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Project-level service that owns the MCP server lifecycle.
 */
@Service(Service.Level.PROJECT)
class McpServerService(private val project: Project) : Disposable {

    private val log = Logger.getInstance(McpServerService::class.java)
    private var mcpServer: McpServer? = null

    fun ensureStarted() {
        if (mcpServer?.isRunning() == true) return
        mcpServer = McpServer(project).also { it.start() }
        log.info("PSI Agent MCP server ready — http://127.0.0.1:${mcpServer!!.getPort()}")
    }

    override fun dispose() {
        mcpServer?.stop()
        mcpServer = null
    }
}

/**
 * Starts the MCP server when a project is opened.
 */
class McpServerStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        project.getService(McpServerService::class.java).ensureStarted()
    }
}

