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
- Treat `psi_search` and `psi_find_usages` as read-only operations.
- Treat `psi_rename` as a write operation that changes files.

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
