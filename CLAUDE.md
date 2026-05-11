# PSI Agent — Instructions for AI Agents

This project includes a PSI Agent that provides **IntelliJ-powered code search and refactoring** via a local HTTP server. When the IntelliJ sandbox IDE is running with a project open, the following tools are available.

## Quick Start (Claude Code)

For native tool discovery with no manual setup:

1. **Register the bridge** (one-time setup):
   - Windows: `.\scripts\setup-claude-code.ps1`
   - Linux/macOS: `./scripts/setup-claude-code.sh`

2. **Start the server** (each session):
   - `./gradlew runHeadless` (recommended — faster, no GUI)
   - Or `./gradlew runIde` (shows IDE window)

3. **Open a project**: Open or create a Java/Kotlin project in the IDE

4. **Use the tools**: In Claude Code, the `psi_search`, `psi_rename`, and `psi_find_usages` tools are now available natively

`psi_extract_method` is also wired into the server and bridge, and is being validated against Java and Kotlin projects.

Kotlin extract-method is wired through ExtractKotlinFunctionHandler and has been tested on Kotlin code.

Extract-method is blocked in unit test mode; validate it in a real IDE session (`runIde` or `runHeadless`).

**MAJOR UPDATE: `psi_rename` now supports EVERY PSI node type**, including:
- ✅ Methods and functions (Java/Kotlin)
- ✅ Classes and interfaces (Java/Kotlin)
- ✅ Fields and properties (Java/Kotlin)
- ✅ Variables and parameters (Java/Kotlin)
- ✅ Type aliases (Kotlin)
- ✅ Any other renamable symbol in your code

The shared resolver works from both declaration sites and usage sites, making it seamless to rename any symbol.

`MethodExtractorTest.kt` covers Java and Kotlin extraction regressions.

`MethodExtractor` validates blank method names and invalid line ranges before invoking PSI refactoring.

That's it! No manual HTTP calls, no approval prompts.

## Server

The MCP server runs at `http://127.0.0.1:9742` automatically when a project is opened in the sandbox IDE (`./gradlew runIde`).

For faster startup without the IDE GUI (recommended for agent use), use:
```bash
./gradlew runHeadless
```

On first start the server writes a bearer token to `~/.psi-agent/token`. The CLI wrappers and MCP bridge read that file automatically.

Check if it's running:
```bash
curl -H "Authorization: Bearer <token>" http://127.0.0.1:9742/api/health
```

## Local MCP setup

Prefer the repository-local MCP config when the client supports it:

- `.mcp.json` contains the full stdio bridge definition with an absolute Windows path.
- `mcp-config.json` is a portable copy you can paste into a client config.
- `package.json` defines the Node dependency for the SDK-backed bridge.

Before using the bridge locally, install the Node dependency:
```bash
npm ci
```

For Claude Code, the native MCP registration should point at:

```bash
node C:\Users\iibra\IdeaProjects\PSI-based-agent\scripts\mcp-stdio-bridge.js
```

Smoke-test the bridge with the mock backend before relying on an agent integration:
```bash
npm run test:bridge
```

If Claude Code is not discovering the tools automatically, fall back to the HTTP endpoints or the CLI wrapper in `scripts/psi-agent.ps1`.

## Available Tools

### 1. PSI Search — find any code element by name
```bash
# Search for methods matching a pattern
curl -X POST http://127.0.0.1:9742/api/search \
  -H "Content-Type: application/json" \
  -d '{"query": "getUserById", "type": "method"}'

# Search for classes
curl -X POST http://127.0.0.1:9742/api/search \
  -H "Content-Type: application/json" \
  -d '{"query": "Service", "type": "class"}'

# Search all element types (methods, classes, fields, properties, etc.)
curl -X POST http://127.0.0.1:9742/api/search \
  -H "Content-Type: application/json" \
  -d '{"query": "config*", "type": "all"}'
```

Or via CLI:
```bash
./scripts/psi-agent.sh search "getUserById" --type method
./scripts/psi-agent.sh search "config*" --type all
```

### 2. PSI Rename — rename ANY symbol (methods, fields, variables, classes, properties, etc.)
```bash
# Rename a method
curl -X POST http://127.0.0.1:9742/api/rename \
  -H "Content-Type: application/json" \
  -d '{"file": "src/main/java/Foo.java", "old_name": "calculate", "new_name": "compute"}'

# Rename a field
curl -X POST http://127.0.0.1:9742/api/rename \
  -H "Content-Type: application/json" \
  -d '{"file": "src/main/java/Config.java", "old_name": "apiKey", "new_name": "secretKey"}'

# Rename a Kotlin property
curl -X POST http://127.0.0.1:9742/api/rename \
  -H "Content-Type: application/json" \
  -d '{"file": "src/main/kotlin/User.kt", "old_name": "firstName", "new_name": "givenName"}'

# Rename a local variable
curl -X POST http://127.0.0.1:9742/api/rename \
  -H "Content-Type: application/json" \
  -d '{"file": "src/main/java/Utils.java", "old_name": "temp", "new_name": "buffer"}'
```

Or via CLI:
```bash
./scripts/psi-agent.sh rename src/main/java/Foo.java calculate compute
./scripts/psi-agent.sh rename src/main/kotlin/User.kt firstName givenName
```

### 3. PSI Find Usages — find all references to any symbol
```bash
# Find usages of a method
curl -X POST http://127.0.0.1:9742/api/find-usages \
  -H "Content-Type: application/json" \
  -d '{"method_name": "processOrder", "class_name": "OrderService"}'

# Find usages of a field or property
curl -X POST http://127.0.0.1:9742/api/find-usages \
  -H "Content-Type: application/json" \
  -d '{"method_name": "apiKey"}'
```

Or via CLI:
```bash
./scripts/psi-agent.sh find-usages processOrder --class OrderService
./scripts/psi-agent.sh find-usages apiKey
```

### 4. PSI Extract Method — extract code into a new method
```bash
curl -X POST http://127.0.0.1:9742/api/extract-method \
  -H "Content-Type: application/json" \
  -d '{"file": "src/main/java/Foo.java", "new_method_name": "compute", "start_line": 10, "end_line": 25}'
```

This refactoring path is currently experimental and should be verified on both Java and Kotlin before relying on it for larger changes.

Or via CLI:
```bash
./scripts/psi-agent.sh extract-method src/main/java/Foo.java compute 10 25
```

### 5. PSI Inline Method — inline a simple method/function at its usage sites
```bash
curl -X POST http://127.0.0.1:9742/api/inline-method \
  -H "Content-Type: application/json" \
  -d '{"file": "src/main/java/Foo.java", "method_name": "calculate"}'
```

Or via CLI:
```bash
./scripts/psi-agent.sh inline-method src/main/java/Foo.java calculate
```

### 6. PSI Move Class — move a class/object to another package
```bash
curl -X POST http://127.0.0.1:9742/api/move-class \
  -H "Content-Type: application/json" \
  -d '{"file": "src/main/java/Foo.java", "target_package": "com.example.moved"}'
```

Or via CLI:
```bash
./scripts/psi-agent.sh move-class src/main/java/Foo.java com.example.moved
```

## When to use these tools

- Use `psi_search` instead of `grep` when you need to find method/class/field/variable definitions with full qualified names and line numbers.
- Use `psi_rename` instead of find-and-replace when renaming ANY symbol (methods, classes, fields, variables, properties, type aliases, etc.) — it correctly updates all call sites, imports, and references across the project.
- Use `psi_rename` from either the declaration site OR any usage site thanks to the universal symbol resolver.
- Use `psi_find_usages` to understand where any symbol is used before making changes.
- Use `psi_extract_method` to refactor complex methods by extracting code blocks into their own methods.
- Use `psi_inline_method` to collapse simple helper methods/functions into their call sites.
- Use `psi_move_class` to move classes or Kotlin objects into another package while keeping imports/references updated.
- These tools work on both **Java** and **Kotlin** files.
- Treat `psi_search` and `psi_find_usages` as read-only operations.
- Treat `psi_rename`, `psi_extract_method`, `psi_inline_method`, and `psi_move_class` as write operations that change files.

## MCP Protocol

For MCP-compatible agents, the tool manifest is at:
```
GET http://127.0.0.1:9742/mcp/tools/list
```

Tool calls use:
```
POST http://127.0.0.1:9742/mcp/tools/call
Content-Type: application/json

{"name": "psi_search", "arguments": {"query": "get*", "type": "method"}}
```
