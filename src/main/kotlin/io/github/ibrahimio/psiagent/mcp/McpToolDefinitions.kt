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
        ),
        mapOf(
            "name" to "psi_create_method",
            "description" to "Create a new method in a Java or Kotlin class. Generates method stub with placeholder or custom body.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "file" to mapOf(
                        "type" to "string",
                        "description" to "Path to the file containing the target class"
                    ),
                    "class_name" to mapOf(
                        "type" to "string",
                        "description" to "Name of the class where the method will be added"
                    ),
                    "method_name" to mapOf(
                        "type" to "string",
                        "description" to "Name for the new method"
                    ),
                    "return_type" to mapOf(
                        "type" to "string",
                        "description" to "Return type (e.g., 'void', 'String', 'int'). Default: void"
                    ),
                    "parameters" to mapOf(
                        "type" to "string",
                        "description" to "Comma-separated parameter list (e.g., 'String name, int age'). Default: empty"
                    ),
                    "method_body" to mapOf(
                        "type" to "string",
                        "description" to "Optional method body. If empty, generates a placeholder TODO comment"
                    ),
                    "is_static" to mapOf(
                        "type" to "boolean",
                        "description" to "If true, method is marked as static. Default: false"
                    ),
                    "visibility" to mapOf(
                        "type" to "string",
                        "enum" to listOf("public", "protected", "private"),
                        "description" to "Access level. Default: public"
                    )
                ),
                "required" to listOf("file", "class_name", "method_name")
            )
        ),
        mapOf(
            "name" to "psi_create_class",
            "description" to "Create a new class or interface in a file. Generates class stub with optional package declaration.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "file" to mapOf(
                        "type" to "string",
                        "description" to "Path to the file where the class will be added"
                    ),
                    "package_name" to mapOf(
                        "type" to "string",
                        "description" to "Package name for the new class (e.g., 'com.example'). Default: empty"
                    ),
                    "class_name" to mapOf(
                        "type" to "string",
                        "description" to "Name for the new class"
                    ),
                    "extends" to mapOf(
                        "type" to "string",
                        "description" to "Optional parent class to extend (e.g., 'BaseClass'). Default: empty"
                    ),
                    "is_interface" to mapOf(
                        "type" to "boolean",
                        "description" to "If true, creates an interface instead of a class. Default: false"
                    )
                ),
                "required" to listOf("file", "class_name")
            )
        ),
        mapOf(
            "name" to "psi_create_field",
            "description" to "Create a new field in a Java or Kotlin class. Generates field with optional initialization.",
            "inputSchema" to mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "file" to mapOf(
                        "type" to "string",
                        "description" to "Path to the file containing the target class"
                    ),
                    "class_name" to mapOf(
                        "type" to "string",
                        "description" to "Name of the class where the field will be added"
                    ),
                    "field_name" to mapOf(
                        "type" to "string",
                        "description" to "Name for the new field"
                    ),
                    "field_type" to mapOf(
                        "type" to "string",
                        "description" to "Type of the field (e.g., 'String', 'int', 'List<String>')"
                    ),
                    "initial_value" to mapOf(
                        "type" to "string",
                        "description" to "Optional initial value (e.g., 'null', '0', 'new ArrayList<>()'). Default: empty"
                    ),
                    "visibility" to mapOf(
                        "type" to "string",
                        "enum" to listOf("public", "protected", "private", "internal"),
                        "description" to "Access level. Default: private"
                    ),
                    "is_static" to mapOf(
                        "type" to "boolean",
                        "description" to "If true, field is marked as static. Default: false"
                    ),
                    "is_final" to mapOf(
                        "type" to "boolean",
                        "description" to "If true, field is marked as final. Default: false"
                    )
                ),
                "required" to listOf("file", "class_name", "field_name", "field_type")
            )
        )
    )
}
