package io.github.ibrahimio.psiagent.mcp

/**
 * MCP tool definitions — describes the tools this server exposes.
 * Agents read these schemas to understand what parameters each tool accepts.
 */
object McpToolDefinitions {

    fun allTools(): List<Map<String, Any>> = listOf(
        mapOf(
            "name" to "psi_search",
            "description" to "Search for code elements (methods, classes, fields) in the open IntelliJ project using the PSI index. " +
                    "Supports glob wildcards (* and ?). Returns file paths, line numbers, qualified names, and code snippets.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "query" to mapOf(
                        "type" to "string",
                        "description" to "Name pattern to search for. Supports * and ? wildcards. Example: 'get*', 'UserService', 'calculate'"
                    ),
                    "type" to mapOf(
                        "type" to "string",
                        "enum" to listOf("method", "class", "all"),
                        "description" to "Filter by element type. Default: all"
                    )
                ),
                "required" to listOf("query")
            )
        ),
        mapOf(
            "name" to "psi_rename",
            "description" to "Rename a method/function in a file using IntelliJ's PSI refactoring engine. " +
                    "Automatically updates all usages across the project. Works with both Java and Kotlin files.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "file" to mapOf(
                        "type" to "string",
                        "description" to "Path to the file containing the method. Can be absolute or relative to the project root."
                    ),
                    "old_name" to mapOf(
                        "type" to "string",
                        "description" to "Current name of the method to rename"
                    ),
                    "new_name" to mapOf(
                        "type" to "string",
                        "description" to "New name for the method"
                    )
                ),
                "required" to listOf("file", "old_name", "new_name")
            )
        ),
        mapOf(
            "name" to "psi_find_usages",
            "description" to "Find all usages (call sites, references) of a method across the entire project using the PSI index. " +
                    "Returns file paths, line numbers, and code snippets for each usage.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "method_name" to mapOf(
                        "type" to "string",
                        "description" to "Name of the method to find usages for"
                    ),
                    "class_name" to mapOf(
                        "type" to "string",
                        "description" to "Optional: filter to a specific class that defines the method"
                    )
                ),
                "required" to listOf("method_name")
            )
        )
    )
}

