package io.github.ibrahimio.psiagent.mcp

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.ibrahimio.psiagent.refactoring.MethodRenamer
import io.github.ibrahimio.psiagent.refactoring.MethodExtractor
import io.github.ibrahimio.psiagent.refactoring.InlineMethodProcessor
import io.github.ibrahimio.psiagent.refactoring.IntroduceVariableProcessor
import io.github.ibrahimio.psiagent.refactoring.MoveClassProcessor
import io.github.ibrahimio.psiagent.search.PsiCodeSearcher
import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * Lightweight HTTP server that exposes PSI operations as MCP-compatible tool calls.
 * Runs on localhost:9742 (configurable) when a project is open.
 *
 * Agents (Claude Desktop, Cursor, custom scripts) call these endpoints to perform
 * structured code search and refactoring via the IntelliJ PSI engine.
 */
class McpServer(private val project: Project) {

    private val log = Logger.getInstance(McpServer::class.java)
    private val gson = Gson()
    private var server: HttpServer? = null
    private val port = 9742

    fun start() {
        try {
            PsiAgentAuth.ensureToken()
            server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0).apply {
                executor = Executors.newFixedThreadPool(4)

                // MCP discovery — lists available tools
                createContext("/mcp/tools/list") { handleToolsList(it) }

                // MCP tool execution
                createContext("/mcp/tools/call") { handleToolCall(it) }

                // Convenience REST endpoints (easier for shell scripts)
                createContext("/api/search") { handleSearch(it) }
                createContext("/api/rename") { handleRename(it) }
                createContext("/api/inline-method") { handleInlineMethod(it) }
                createContext("/api/introduce-variable") { handleIntroduceVariable(it) }
                createContext("/api/find-usages") { handleFindUsages(it) }
                createContext("/api/health") { handleHealth(it) }
                createContext("/api/move-class") { handleMoveClass(it) }

                start()
            }
            log.info("PSI Agent MCP server started on http://127.0.0.1:$port")
        } catch (e: IOException) {
            log.warn("Failed to start MCP server on port $port: ${e.message}")
        }
    }

    fun stop() {
        server?.stop(1)
        server = null
        log.info("PSI Agent MCP server stopped")
    }

    fun isRunning(): Boolean = server != null

    fun getPort(): Int = port

    // ── MCP Protocol Handlers ──────────────────────────────────────────────

    private fun handleToolsList(exchange: HttpExchange) {
        if (!requireAuth(exchange)) return
        val tools = McpToolDefinitions.allTools()
        sendJson(exchange, 200, mapOf("tools" to tools))
    }

    private fun handleToolCall(exchange: HttpExchange) {
        if (!requireAuth(exchange)) return
        if (exchange.requestMethod != "POST") {
            sendJson(exchange, 405, mapOf("error" to "Method not allowed"))
            return
        }

        try {
            val body = exchange.requestBody.bufferedReader().readText()
            val request = JsonParser.parseString(body).asJsonObject
            val toolName = request.get("name")?.asString
            val params = request.getAsJsonObject("arguments") ?: JsonObject()

            when (toolName) {
                "psi_search" -> executeSearch(exchange, params)
                "psi_rename" -> executeRename(exchange, params)
                "psi_inline_method" -> executeInlineMethod(exchange, params)
                "psi_introduce_variable" -> executeIntroduceVariable(exchange, params)
                "psi_find_usages" -> executeFindUsages(exchange, params)
                "psi_extract_method" -> executeExtractMethod(exchange, params)
                "psi_move_class" -> executeMoveClass(exchange, params)
                else -> sendJson(exchange, 400, mapOf(
                    "error" to "Unknown tool: $toolName",
                    "available" to listOf("psi_search", "psi_rename", "psi_inline_method", "psi_introduce_variable", "psi_find_usages", "psi_extract_method", "psi_move_class")
                ))
            }
        } catch (e: Exception) {
            sendJson(exchange, 500, mapOf("error" to "Internal error: ${e.message}"))
        }
    }

    // ── REST Convenience Handlers ──────────────────────────────────────────

    private fun handleHealth(exchange: HttpExchange) {
        if (!requireAuth(exchange)) return
        sendJson(exchange, 200, mapOf(
            "status" to "ok",
            "project" to project.name,
            "projectPath" to (project.basePath ?: ""),
            "authRequired" to true,
            "tokenFile" to PsiAgentAuth.tokenFile().toString(),
            "mcpToolsUrl" to "http://127.0.0.1:$port/mcp/tools/list"
        ))
    }

    private fun handleSearch(exchange: HttpExchange) {
        if (!requireAuth(exchange)) return
        if (exchange.requestMethod != "POST") {
            sendJson(exchange, 405, mapOf("error" to "POST required"))
            return
        }
        val body = exchange.requestBody.bufferedReader().readText()
        val params = JsonParser.parseString(body).asJsonObject
        executeSearch(exchange, params)
    }

    private fun handleRename(exchange: HttpExchange) {
        if (!requireAuth(exchange)) return
        if (exchange.requestMethod != "POST") {
            sendJson(exchange, 405, mapOf("error" to "POST required"))
            return
        }
        val body = exchange.requestBody.bufferedReader().readText()
        val params = JsonParser.parseString(body).asJsonObject
        executeRename(exchange, params)
    }

    private fun handleInlineMethod(exchange: HttpExchange) {
        if (!requireAuth(exchange)) return
        if (exchange.requestMethod != "POST") {
            sendJson(exchange, 405, mapOf("error" to "POST required"))
            return
        }
        val body = exchange.requestBody.bufferedReader().readText()
        val params = JsonParser.parseString(body).asJsonObject
        executeInlineMethod(exchange, params)
    }

    private fun handleIntroduceVariable(exchange: HttpExchange) {
        if (!requireAuth(exchange)) return
        if (exchange.requestMethod != "POST") {
            sendJson(exchange, 405, mapOf("error" to "POST required"))
            return
        }
        val body = exchange.requestBody.bufferedReader().readText()
        val params = JsonParser.parseString(body).asJsonObject
        executeIntroduceVariable(exchange, params)
    }

    private fun handleFindUsages(exchange: HttpExchange) {
        if (!requireAuth(exchange)) return
        if (exchange.requestMethod != "POST") {
            sendJson(exchange, 405, mapOf("error" to "POST required"))
            return
        }
        val body = exchange.requestBody.bufferedReader().readText()
        val params = JsonParser.parseString(body).asJsonObject
        executeFindUsages(exchange, params)
    }

    private fun handleMoveClass(exchange: HttpExchange) {
        if (!requireAuth(exchange)) return
        if (exchange.requestMethod != "POST") {
            sendJson(exchange, 405, mapOf("error" to "POST required"))
            return
        }
        val body = exchange.requestBody.bufferedReader().readText()
        val params = JsonParser.parseString(body).asJsonObject
        executeMoveClass(exchange, params)
    }

    // ── Tool Execution Logic ───────────────────────────────────────────────

    private fun executeSearch(exchange: HttpExchange, params: JsonObject) {
        val query = params.get("query")?.asString
        val type = params.get("type")?.asString ?: "all"

        if (query.isNullOrBlank()) {
            sendJson(exchange, 400, mapOf("error" to "Missing required parameter: query"))
            return
        }

        val results = ReadAction.compute<Any, Exception> {
            val searcher = PsiCodeSearcher(project)
            when (type) {
                "method" -> searcher.searchMethods(query)
                "class" -> searcher.searchClasses(query)
                "field" -> searcher.searchFields(query)
                else -> searcher.searchAll(query)
            }
        }

        sendJson(exchange, 200, mapOf(
            "tool" to "psi_search",
            "query" to query,
            "type" to type,
            "results" to results
        ))
    }

    private fun executeRename(exchange: HttpExchange, params: JsonObject) {
        val file = params.get("file")?.asString
        val oldName = params.get("old_name")?.asString
        val newName = params.get("new_name")?.asString

        if (file.isNullOrBlank() || oldName.isNullOrBlank() || newName.isNullOrBlank()) {
            sendJson(exchange, 400, mapOf("error" to "Missing required parameters: file, old_name, new_name"))
            return
        }

        // Resolve relative paths against project base
        val resolvedFile = if (file.startsWith("/") || file.contains(":")) {
            file
        } else {
            "${project.basePath}/$file"
        }

        // RenameProcessor must run on EDT with write access
        var result: Any? = null
        ApplicationManager.getApplication().invokeAndWait {
            val renamer = MethodRenamer(project)
            result = renamer.renameMethod(resolvedFile, oldName, newName)
        }

        sendJson(exchange, 200, mapOf(
            "tool" to "psi_rename",
            "result" to result
        ))
    }

    private fun executeInlineMethod(exchange: HttpExchange, params: JsonObject) {
        val file = params.get("file")?.asString
        val methodName = params.get("method_name")?.asString

        if (file.isNullOrBlank() || methodName.isNullOrBlank()) {
            sendJson(exchange, 400, mapOf("error" to "Missing required parameters: file, method_name"))
            return
        }

        val resolvedFile = if (file.startsWith("/") || file.contains(":")) {
            file
        } else {
            "${project.basePath}/$file"
        }

        var result: Any? = null
        ApplicationManager.getApplication().invokeAndWait {
            val inliner = InlineMethodProcessor(project)
            result = inliner.inlineMethod(resolvedFile, methodName)
        }

        sendJson(exchange, 200, mapOf(
            "tool" to "psi_inline_method",
            "result" to result
        ))
    }

    private fun executeIntroduceVariable(exchange: HttpExchange, params: JsonObject) {
        val file = params.get("file")?.asString
        val variableName = params.get("variable_name")?.asString
        val startLine = params.get("start_line")?.asInt
        val startColumn = params.get("start_column")?.asInt
        val endLine = params.get("end_line")?.asInt
        val endColumn = params.get("end_column")?.asInt

        if (file.isNullOrBlank() || variableName.isNullOrBlank() || startLine == null || startColumn == null || endLine == null || endColumn == null) {
            sendJson(exchange, 400, mapOf(
                "error" to "Missing required parameters: file, variable_name, start_line, start_column, end_line, end_column"
            ))
            return
        }

        val resolvedFile = if (file.startsWith("/") || file.contains(":")) {
            file
        } else {
            "${project.basePath}/$file"
        }

        var result: Any? = null
        ApplicationManager.getApplication().invokeAndWait {
            val introducer = IntroduceVariableProcessor(project)
            result = introducer.introduceVariable(resolvedFile, variableName, startLine, startColumn, endLine, endColumn)
        }

        sendJson(exchange, 200, mapOf(
            "tool" to "psi_introduce_variable",
            "result" to result
        ))
    }

    private fun executeFindUsages(exchange: HttpExchange, params: JsonObject) {
        val symbolName = params.get("symbol_name")?.asString ?: params.get("method_name")?.asString
        val className = params.get("class_name")?.asString
        val symbolType = params.get("symbol_type")?.asString

        if (symbolName.isNullOrBlank()) {
            sendJson(exchange, 400, mapOf("error" to "Missing required parameter: symbol_name"))
            return
        }

        val results = ReadAction.compute<Any, Exception> {
            val searcher = PsiCodeSearcher(project)
            searcher.findUsages(symbolName, className, symbolType)
        }

        sendJson(exchange, 200, mapOf(
            "tool" to "psi_find_usages",
            "symbol_name" to symbolName,
            "results" to results
        ))
    }

    private fun executeExtractMethod(exchange: HttpExchange, params: JsonObject) {
        val file = params.get("file")?.asString
        val newMethodName = params.get("new_method_name")?.asString
        val startLine = params.get("start_line")?.asInt
        val endLine = params.get("end_line")?.asInt

        if (file.isNullOrBlank() || newMethodName.isNullOrBlank() || startLine == null || endLine == null) {
            sendJson(exchange, 400, mapOf(
                "error" to "Missing required parameters: file, new_method_name, start_line, end_line"
            ))
            return
        }

        // Resolve relative paths against project base
        val resolvedFile = if (file.startsWith("/") || file.contains(":")) {
            file
        } else {
            "${project.basePath}/$file"
        }

        // ExtractMethodProcessor must run on EDT with write access
        var result: Any? = null
        ApplicationManager.getApplication().invokeAndWait {
            val extractor = MethodExtractor(project)
            result = extractor.extractMethod(resolvedFile, newMethodName, startLine, endLine)
        }

        sendJson(exchange, 200, mapOf(
            "tool" to "psi_extract_method",
            "result" to result
        ))
    }

    private fun executeMoveClass(exchange: HttpExchange, params: JsonObject) {
        val file = params.get("file")?.asString
        val targetPackage = params.get("target_package")?.asString

        if (file.isNullOrBlank() || targetPackage.isNullOrBlank()) {
            sendJson(exchange, 400, mapOf(
                "error" to "Missing required parameters: file, target_package"
            ))
            return
        }

        val resolvedFile = if (file.startsWith("/") || file.contains(":")) {
            file
        } else {
            "${project.basePath}/$file"
        }

        var result: Any? = null
        ApplicationManager.getApplication().invokeAndWait {
            val mover = MoveClassProcessor(project)
            result = mover.moveClass(resolvedFile, targetPackage)
        }

        sendJson(exchange, 200, mapOf(
            "tool" to "psi_move_class",
            "result" to result
        ))
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun requireAuth(exchange: HttpExchange): Boolean {
        val expectedToken = PsiAgentAuth.readToken() ?: return true
        val provided = exchange.requestHeaders.getFirst("Authorization")?.trim()
        if (provided == "Bearer $expectedToken") return true

        exchange.responseHeaders.add("WWW-Authenticate", "Bearer")
        sendJson(exchange, 401, mapOf("error" to "Unauthorized"))
        return false
    }

    private fun sendJson(exchange: HttpExchange, statusCode: Int, data: Any) {
        val json = gson.toJson(data)
        val bytes = json.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
