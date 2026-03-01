# PSI Agent — Instructions for AI Agents

This project includes a PSI Agent that provides **IntelliJ-powered code search and refactoring** via a local HTTP server. When the IntelliJ sandbox IDE is running with a project open, the following tools are available.

## Server

The MCP server runs at `http://127.0.0.1:9742` automatically when a project is opened in the sandbox IDE (`./gradlew runIde`).

Check if it's running:
```bash
curl http://127.0.0.1:9742/api/health
```

## Available Tools

### 1. PSI Search — find code elements by name
```bash
# Search for methods matching a pattern (supports * and ? wildcards)
curl -X POST http://127.0.0.1:9742/api/search \
  -H "Content-Type: application/json" \
  -d '{"query": "getUserById", "type": "method"}'

# Search all element types
curl -X POST http://127.0.0.1:9742/api/search \
  -H "Content-Type: application/json" \
  -d '{"query": "Service", "type": "all"}'
```

Or via CLI:
```bash
./scripts/psi-agent.sh search "getUserById" --type method
```

### 2. PSI Rename — rename a method and update all usages
```bash
curl -X POST http://127.0.0.1:9742/api/rename \
  -H "Content-Type: application/json" \
  -d '{"file": "src/main/java/Foo.java", "old_name": "calculate", "new_name": "compute"}'
```

Or via CLI:
```bash
./scripts/psi-agent.sh rename src/main/java/Foo.java calculate compute
```

### 3. PSI Find Usages — find all references to a method
```bash
curl -X POST http://127.0.0.1:9742/api/find-usages \
  -H "Content-Type: application/json" \
  -d '{"method_name": "processOrder", "class_name": "OrderService"}'
```

Or via CLI:
```bash
./scripts/psi-agent.sh find-usages processOrder --class OrderService
```

## When to use these tools

- Use `psi_search` instead of `grep` when you need to find method/class definitions with full qualified names and line numbers.
- Use `psi_rename` instead of find-and-replace when renaming a method — it correctly updates all call sites, imports, and references.
- Use `psi_find_usages` to understand where a method is called before making changes.
- These tools work on both **Java** and **Kotlin** files.

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

