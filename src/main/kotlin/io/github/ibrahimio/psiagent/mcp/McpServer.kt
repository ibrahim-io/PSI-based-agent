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
import io.github.ibrahimio.psiagent.refactoring.DeleteSymbolProcessor
import io.github.ibrahimio.psiagent.refactoring.DeleteSymbolResult
import io.github.ibrahimio.psiagent.refactoring.RenameResult
import io.github.ibrahimio.psiagent.refactoring.InlineMethodProcessor
import io.github.ibrahimio.psiagent.refactoring.InlineMethodResult
import io.github.ibrahimio.psiagent.refactoring.IntroduceVariableProcessor
import io.github.ibrahimio.psiagent.refactoring.IntroduceVariableResult
import io.github.ibrahimio.psiagent.refactoring.MoveClassProcessor
import io.github.ibrahimio.psiagent.refactoring.MoveClassResult
import io.github.ibrahimio.psiagent.refactoring.CreateMethodProcessor
import io.github.ibrahimio.psiagent.refactoring.CreateClassProcessor
import io.github.ibrahimio.psiagent.refactoring.CreateFieldProcessor
import io.github.ibrahimio.psiagent.refactoring.CreateMethodResult
import io.github.ibrahimio.psiagent.refactoring.CreateClassResult
import io.github.ibrahimio.psiagent.refactoring.CreateFieldResult
import io.github.ibrahimio.psiagent.search.PsiCodeSearcher
import io.github.ibrahimio.psiagent.visualization.PsiChangeRecord
import io.github.ibrahimio.psiagent.visualization.PsiDiffService
import io.github.ibrahimio.psiagent.visualization.PsiFileSnapshot
import io.github.ibrahimio.psiagent.visualization.PsiSnapshotSerializer
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
                createContext("/api/delete-symbol") { handleDeleteSymbol(it) }
                createContext("/api/find-usages") { handleFindUsages(it) }
                createContext("/api/health") { handleHealth(it) }
                createContext("/api/move-class") { handleMoveClass(it) }
                createContext("/api/create-method") { handleCreateMethod(it) }
                createContext("/api/create-class") { handleCreateClass(it) }
                createContext("/api/create-field") { handleCreateField(it) }

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
                "psi_delete_symbol" -> executeDeleteSymbol(exchange, params)
                "psi_find_usages" -> executeFindUsages(exchange, params)
                "psi_extract_method" -> executeExtractMethod(exchange, params)
                "psi_move_class" -> executeMoveClass(exchange, params)
                "psi_create_method" -> executeCreateMethod(exchange, params)
                "psi_create_class" -> executeCreateClass(exchange, params)
                "psi_create_field" -> executeCreateField(exchange, params)
                else -> sendJson(exchange, 400, mapOf(
                    "error" to "Unknown tool: $toolName",
                    "available" to listOf("psi_search", "psi_rename", "psi_inline_method", "psi_introduce_variable", "psi_delete_symbol", "psi_find_usages", "psi_extract_method", "psi_move_class", "psi_create_method", "psi_create_class", "psi_create_field")
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

    private fun handleDeleteSymbol(exchange: HttpExchange) {
        if (!requireAuth(exchange)) return
        if (exchange.requestMethod != "POST") {
            sendJson(exchange, 405, mapOf("error" to "POST required"))
            return
        }
        val body = exchange.requestBody.bufferedReader().readText()
        val params = JsonParser.parseString(body).asJsonObject
        executeDeleteSymbol(exchange, params)
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

    private fun handleCreateMethod(exchange: HttpExchange) {
        if (!requireAuth(exchange)) return
        if (exchange.requestMethod != "POST") {
            sendJson(exchange, 405, mapOf("error" to "POST required"))
            return
        }
        val body = exchange.requestBody.bufferedReader().readText()
        val params = JsonParser.parseString(body).asJsonObject
        executeCreateMethod(exchange, params)
    }

    private fun handleCreateClass(exchange: HttpExchange) {
        if (!requireAuth(exchange)) return
        if (exchange.requestMethod != "POST") {
            sendJson(exchange, 405, mapOf("error" to "POST required"))
            return
        }
        val body = exchange.requestBody.bufferedReader().readText()
        val params = JsonParser.parseString(body).asJsonObject
        executeCreateClass(exchange, params)
    }

    private fun handleCreateField(exchange: HttpExchange) {
        if (!requireAuth(exchange)) return
        if (exchange.requestMethod != "POST") {
            sendJson(exchange, 405, mapOf("error" to "POST required"))
            return
        }
        val body = exchange.requestBody.bufferedReader().readText()
        val params = JsonParser.parseString(body).asJsonObject
        executeCreateField(exchange, params)
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

        val beforeSnapshot = snapshotFile(resolvedFile)

        // RenameProcessor must run on EDT with write access
        var result: RenameResult? = null
        ApplicationManager.getApplication().invokeAndWait {
            val renamer = MethodRenamer(project)
            result = renamer.renameMethod(resolvedFile, oldName, newName)
        }

        val renameResult = result ?: return sendJson(exchange, 500, mapOf("error" to "Rename refactoring did not return a result"))
        val afterSnapshot = snapshotFile(renameResult.affectedFiles.firstOrNull { it.endsWith(".java") || it.endsWith(".kt") } ?: resolvedFile)
        publishPsiChange("psi_rename", resolvedFile, beforeSnapshot, afterSnapshot, renameResult.success, renameResult.message, renameResult.affectedFiles)

        sendJson(exchange, 200, mapOf(
            "tool" to "psi_rename",
            "result" to renameResult
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

        val beforeSnapshot = snapshotFile(resolvedFile)

        var result: InlineMethodResult? = null
        ApplicationManager.getApplication().invokeAndWait {
            val inliner = InlineMethodProcessor(project)
            result = inliner.inlineMethod(resolvedFile, methodName)
        }

        val inlineResult = result ?: return sendJson(exchange, 500, mapOf("error" to "Inline refactoring did not return a result"))
        val afterSnapshot = snapshotFile(resolvedFile)
        publishPsiChange("psi_inline_method", resolvedFile, beforeSnapshot, afterSnapshot, inlineResult.success, inlineResult.message, inlineResult.affectedFiles)

        sendJson(exchange, 200, mapOf(
            "tool" to "psi_inline_method",
            "result" to inlineResult
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

        val beforeSnapshot = snapshotFile(resolvedFile)

        var result: IntroduceVariableResult? = null
        ApplicationManager.getApplication().invokeAndWait {
            val introducer = IntroduceVariableProcessor(project)
            result = introducer.introduceVariable(resolvedFile, variableName, startLine, startColumn, endLine, endColumn)
        }

        val introduceResult = result ?: return sendJson(exchange, 500, mapOf("error" to "Introduce-variable refactoring did not return a result"))
        val afterSnapshot = snapshotFile(resolvedFile)
        publishPsiChange("psi_introduce_variable", resolvedFile, beforeSnapshot, afterSnapshot, introduceResult.success, introduceResult.message, introduceResult.affectedFiles)

        sendJson(exchange, 200, mapOf(
            "tool" to "psi_introduce_variable",
            "result" to introduceResult
        ))
    }

    private fun executeDeleteSymbol(exchange: HttpExchange, params: JsonObject) {
        val file = params.get("file")?.asString
        val symbolName = params.get("symbol_name")?.asString ?: params.get("name")?.asString
        val symbolType = params.get("symbol_type")?.asString ?: "all"

        if (file.isNullOrBlank() || symbolName.isNullOrBlank()) {
            sendJson(exchange, 400, mapOf("error" to "Missing required parameters: file, symbol_name"))
            return
        }

        val resolvedFile = if (file.startsWith("/") || file.contains(":")) {
            file
        } else {
            "${project.basePath}/$file"
        }

        val beforeSnapshot = snapshotFile(resolvedFile)

        var result: DeleteSymbolResult? = null
        ApplicationManager.getApplication().invokeAndWait {
            val deleter = DeleteSymbolProcessor(project)
            result = deleter.deleteSymbol(resolvedFile, symbolName, symbolType)
        }

        val deleteResult = result ?: return sendJson(exchange, 500, mapOf("error" to "Delete-symbol refactoring did not return a result"))
        val afterSnapshot = snapshotFile(resolvedFile)
        publishPsiChange("psi_delete_symbol", resolvedFile, beforeSnapshot, afterSnapshot, deleteResult.success, deleteResult.message, deleteResult.affectedFiles)

        sendJson(exchange, 200, mapOf(
            "tool" to "psi_delete_symbol",
            "result" to deleteResult
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

        val beforeSnapshot = snapshotFile(resolvedFile)

        // ExtractMethodProcessor must run on EDT with write access
        var result: Any? = null
        ApplicationManager.getApplication().invokeAndWait {
            val extractor = MethodExtractor(project)
            result = extractor.extractMethod(resolvedFile, newMethodName, startLine, endLine)
        }

        val extractResult = result as? io.github.ibrahimio.psiagent.refactoring.ExtractMethodResult
            ?: return sendJson(exchange, 500, mapOf("error" to "Extract-method refactoring did not return a result"))
        val afterSnapshot = snapshotFile(resolvedFile)
        publishPsiChange("psi_extract_method", resolvedFile, beforeSnapshot, afterSnapshot, extractResult.success, extractResult.message, extractResult.affectedFiles)

        sendJson(exchange, 200, mapOf(
            "tool" to "psi_extract_method",
            "result" to extractResult
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

        val beforeSnapshot = snapshotFile(resolvedFile)

        var result: MoveClassResult? = null
        ApplicationManager.getApplication().invokeAndWait {
            val mover = MoveClassProcessor(project)
            result = mover.moveClass(resolvedFile, targetPackage)
        }

        val moveResult = result ?: return sendJson(exchange, 500, mapOf("error" to "Move-class refactoring did not return a result"))
        val afterSnapshotPath = moveResult.affectedFiles.firstOrNull { it != resolvedFile && (it.endsWith(".java") || it.endsWith(".kt")) }
            ?: moveResult.affectedFiles.lastOrNull { it.endsWith(".java") || it.endsWith(".kt") }
            ?: resolvedFile
        val afterSnapshot = snapshotFile(afterSnapshotPath)
        publishPsiChange("psi_move_class", resolvedFile, beforeSnapshot, afterSnapshot, moveResult.success, moveResult.message, moveResult.affectedFiles)

        sendJson(exchange, 200, mapOf(
            "tool" to "psi_move_class",
            "result" to moveResult
        ))
    }

    private fun executeCreateMethod(exchange: HttpExchange, params: JsonObject) {
        val file = params.get("file")?.asString
        val className = params.get("class_name")?.asString
        val methodName = params.get("method_name")?.asString
        val returnType = params.get("return_type")?.asString ?: "void"
        val parameters = params.get("parameters")?.asString ?: ""
        val methodBody = params.get("method_body")?.asString ?: ""
        val isStatic = params.get("is_static")?.asBoolean ?: false
        val visibility = params.get("visibility")?.asString ?: "public"

        if (file.isNullOrBlank() || className.isNullOrBlank() || methodName.isNullOrBlank()) {
            sendJson(exchange, 400, mapOf(
                "error" to "Missing required parameters: file, class_name, method_name"
            ))
            return
        }

        val resolvedFile = if (file.startsWith("/") || file.contains(":")) {
            file
        } else {
            "${project.basePath}/$file"
        }

        val beforeSnapshot = snapshotFile(resolvedFile)

        var result: CreateMethodResult? = null
        ApplicationManager.getApplication().invokeAndWait {
            val creator = CreateMethodProcessor(project)
            result = creator.createMethod(resolvedFile, className, methodName, returnType, parameters, methodBody, isStatic, visibility)
        }

        val createResult = result ?: return sendJson(exchange, 500, mapOf("error" to "Create-method did not return a result"))
        val afterSnapshot = snapshotFile(resolvedFile)
        publishPsiChange("psi_create_method", resolvedFile, beforeSnapshot, afterSnapshot, createResult.success, createResult.message, createResult.affectedFiles)

        sendJson(exchange, 200, mapOf(
            "tool" to "psi_create_method",
            "result" to createResult
        ))
    }

    private fun executeCreateClass(exchange: HttpExchange, params: JsonObject) {
        val file = params.get("file")?.asString
        val packageName = params.get("package_name")?.asString ?: ""
        val className = params.get("class_name")?.asString
        val extends = params.get("extends")?.asString ?: ""
        val isInterface = params.get("is_interface")?.asBoolean ?: false
        val isKotlin = file?.endsWith(".kt") ?: false

        if (file.isNullOrBlank() || className.isNullOrBlank()) {
            sendJson(exchange, 400, mapOf(
                "error" to "Missing required parameters: file, class_name"
            ))
            return
        }

        val resolvedFile = if (file.startsWith("/") || file.contains(":")) {
            file
        } else {
            "${project.basePath}/$file"
        }

        val beforeSnapshot = snapshotFile(resolvedFile)

        var result: CreateClassResult? = null
        ApplicationManager.getApplication().invokeAndWait {
            val creator = CreateClassProcessor(project)
            result = creator.createClass(resolvedFile, packageName, className, extends, isInterface, isKotlin)
        }

        val createResult = result ?: return sendJson(exchange, 500, mapOf("error" to "Create-class did not return a result"))
        val afterSnapshot = snapshotFile(resolvedFile)
        publishPsiChange("psi_create_class", resolvedFile, beforeSnapshot, afterSnapshot, createResult.success, createResult.message, createResult.affectedFiles)

        sendJson(exchange, 200, mapOf(
            "tool" to "psi_create_class",
            "result" to createResult
        ))
    }

    private fun executeCreateField(exchange: HttpExchange, params: JsonObject) {
        val file = params.get("file")?.asString
        val className = params.get("class_name")?.asString
        val fieldName = params.get("field_name")?.asString
        val fieldType = params.get("field_type")?.asString
        val initialValue = params.get("initial_value")?.asString ?: ""
        val visibility = params.get("visibility")?.asString ?: "private"
        val isStatic = params.get("is_static")?.asBoolean ?: false
        val isFinal = params.get("is_final")?.asBoolean ?: false

        if (file.isNullOrBlank() || className.isNullOrBlank() || fieldName.isNullOrBlank() || fieldType.isNullOrBlank()) {
            sendJson(exchange, 400, mapOf(
                "error" to "Missing required parameters: file, class_name, field_name, field_type"
            ))
            return
        }

        val resolvedFile = if (file.startsWith("/") || file.contains(":")) {
            file
        } else {
            "${project.basePath}/$file"
        }

        val beforeSnapshot = snapshotFile(resolvedFile)

        var result: CreateFieldResult? = null
        ApplicationManager.getApplication().invokeAndWait {
            val creator = CreateFieldProcessor(project)
            result = creator.createField(resolvedFile, className, fieldName, fieldType, initialValue, visibility, isStatic, isFinal)
        }

        val createResult = result ?: return sendJson(exchange, 500, mapOf("error" to "Create-field did not return a result"))
        val afterSnapshot = snapshotFile(resolvedFile)
        publishPsiChange("psi_create_field", resolvedFile, beforeSnapshot, afterSnapshot, createResult.success, createResult.message, createResult.affectedFiles)

        sendJson(exchange, 200, mapOf(
            "tool" to "psi_create_field",
            "result" to createResult
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

    private fun snapshotFile(filePath: String?): PsiFileSnapshot? {
        if (filePath.isNullOrBlank()) return null
        return ReadAction.compute<PsiFileSnapshot?, Exception> {
            PsiSnapshotSerializer(project).snapshot(filePath)
        }
    }

    private fun publishPsiChange(
        toolName: String,
        filePath: String,
        before: PsiFileSnapshot?,
        after: PsiFileSnapshot?,
        success: Boolean,
        message: String,
        affectedFiles: List<String>
    ) {
        project.getService(PsiDiffService::class.java).publish(
            PsiChangeRecord(
                toolName = toolName,
                filePath = filePath,
                success = success,
                message = message,
                affectedFiles = affectedFiles,
                before = before,
                after = after
            )
        )
    }
}
