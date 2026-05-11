package io.github.ibrahimio.psiagent.mcp

/**
 * MCP tool definitions — describes the tools this server exposes.
 * Agents read these schemas to understand what parameters each tool accepts.
 */
object McpToolDefinitions {

    fun allTools(): List<Map<String, Any>> = listOf(
        mapOf(
            "name" to "psi_search",
            "description" to "Search for any code element (methods, classes, fields, variables, properties, type aliases, etc.) " +
                    "in the open IntelliJ project using the PSI index. Supports glob wildcards (* and ?). " +
                    "Returns file paths, line numbers, qualified names, and code snippets.",
            "annotations" to mapOf(
                "readOnlyHint" to true
            ),
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
            "description" to "Rename ANY PSI node (methods, classes, variables, parameters, fields, properties, type aliases, etc.) " +
                    "using IntelliJ's PSI refactoring engine. Automatically updates all usages across the project. " +
                    "Works with both Java and Kotlin files.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "file" to mapOf(
                        "type" to "string",
                        "description" to "Path to the file containing the symbol. Can be absolute or relative to the project root."
                    ),
                    "old_name" to mapOf(
                        "type" to "string",
                        "description" to "Current name of the symbol to rename (method, class, variable, field, property, etc.)"
                    ),
                    "new_name" to mapOf(
                        "type" to "string",
                        "description" to "New name for the symbol"
                    )
                ),
                "required" to listOf("file", "old_name", "new_name")
            )
        ),
        mapOf(
            "name" to "psi_inline_method",
            "description" to "Inline a simple Java or Kotlin method/function at its usage sites using PSI. Currently supports zero-parameter methods/functions with a single return expression or expression body.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "file" to mapOf(
                        "type" to "string",
                        "description" to "Path to the file containing the method to inline"
                    ),
                    "method_name" to mapOf(
                        "type" to "string",
                        "description" to "Name of the method/function to inline"
                    )
                ),
                "required" to listOf("file", "method_name")
            )
        ),
        mapOf(
            "name" to "psi_find_usages",
            "description" to "Find all usages (call sites, references) of any symbol (method, class, variable, field, property, etc.) " +
                    "across the entire project using the PSI index. Returns file paths, line numbers, and code snippets for each usage.",
            "annotations" to mapOf(
                "readOnlyHint" to true
            ),
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "method_name" to mapOf(
                        "type" to "string",
                        "description" to "Name of the symbol to find usages for (method, class, variable, field, property, etc.)"
                    ),
                    "class_name" to mapOf(
                        "type" to "string",
                        "description" to "Optional: filter to a specific class that defines the method"
                    )
                ),
                "required" to listOf("method_name")
            )
        ),
        mapOf(
            "name" to "psi_extract_method",
            "description" to "Extract a block of code into a new method. Automatically updates all references and handles method signature generation.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "file" to mapOf(
                        "type" to "string",
                        "description" to "Path to the file containing the code to extract"
                    ),
                    "new_method_name" to mapOf(
                        "type" to "string",
                        "description" to "Name for the new extracted method"
                    ),
                    "start_line" to mapOf(
                        "type" to "integer",
                        "description" to "Line number where the code block starts (1-based)"
                    ),
                    "end_line" to mapOf(
                        "type" to "integer",
                        "description" to "Line number where the code block ends (1-based, inclusive)"
                    )
                ),
                "required" to listOf("file", "new_method_name", "start_line", "end_line")
            )
        ),
        mapOf(
            "name" to "psi_introduce_variable",
            "description" to "Introduce a local variable from a selected Java or Kotlin expression using PSI-guided refactoring. Requires a precise line/column range that selects the expression to extract.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "file" to mapOf(
                        "type" to "string",
                        "description" to "Path to the file containing the expression"
                    ),
                    "variable_name" to mapOf(
                        "type" to "string",
                        "description" to "Name for the new local variable"
                    ),
                    "start_line" to mapOf(
                        "type" to "integer",
                        "description" to "Line number where the expression selection starts (1-based)"
                    ),
                    "start_column" to mapOf(
                        "type" to "integer",
                        "description" to "Column where the expression selection starts (1-based)"
                    ),
                    "end_line" to mapOf(
                        "type" to "integer",
                        "description" to "Line number where the expression selection ends (1-based, inclusive)"
                    ),
                    "end_column" to mapOf(
                        "type" to "integer",
                        "description" to "Column where the expression selection ends (1-based, inclusive)"
                    )
                ),
                "required" to listOf("file", "variable_name", "start_line", "start_column", "end_line", "end_column")
            )
        ),
        mapOf(
            "name" to "psi_move_class",
            "description" to "Move a Java or Kotlin class/object to a different package using IntelliJ's PSI refactoring engine. Automatically updates imports and references.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "file" to mapOf(
                        "type" to "string",
                        "description" to "Path to the file containing the class to move"
                    ),
                    "target_package" to mapOf(
                        "type" to "string",
                        "description" to "Target package name, for example 'com.example.moved'"
                    )
                ),
                "required" to listOf("file", "target_package")
            )
        )
    )
}
