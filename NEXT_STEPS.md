# PSI Agent — Next Steps & Roadmap

## Current State (Prototype — Working ✅)

Claude Code successfully renames methods via PSI through `curl` commands to the HTTP server.
The full pipeline works: Agent → curl → HTTP server (inside IntelliJ) → PSI RenameProcessor → file updated.

## Problems to Fix (Priority Order)

### 🔴 P0: Make MCP work natively (no curl/approval prompts)

**Problem:** Claude Code doesn't pick up the stdio bridge as a native MCP tool. It falls back to bash `curl` commands, each requiring manual approval.

**Fix:**
1. Rewrite `scripts/mcp-stdio-bridge.js` using the official `@modelcontextprotocol/sdk` npm package instead of hand-rolled JSON-RPC parsing. This ensures full MCP spec compliance.
2. Register the server globally via `claude mcp add psi-agent -- node <path>/scripts/mcp-stdio-bridge.js` (not just a `.mcp.json` file).
3. Ensure `tools/list` response returns the correct MCP schema shape (tools array directly in `result`, not wrapped in `{"tools": [...]}`).
4. Add `readOnlyHint: true` annotation to `psi_search` and `psi_find_usages` so Claude auto-approves read-only operations.

**Result:** Agent calls `psi_rename`, `psi_search`, `psi_find_usages` as native tools — zero approval prompts for reads, one-time approval for writes.

### 🟡 P1: Reduce startup time

**Problem:** `gradlew runIde` takes ~30s+ to launch a full GUI IDE, plus PSI indexing time.

**Options (pick one):**
1. **Headless IntelliJ mode** — Launch IntelliJ with `java.awt.headless=true`. No GUI window, full PSI access. Available since IntelliJ 2023.3. Fastest path.
2. **Daemon mode** — Keep the JVM and PSI indices alive between operations. The MCP server already does this while the IDE runs, but a headless daemon would eliminate the visible IDE.
3. **Warm-start script** — Pre-index the project on first run, cache indices to disk, reload on subsequent runs.

**Recommended:** Option 1 (headless mode) — add a `gradlew runHeadless` task or a standalone launch script.

### 🟡 P2: HTTP security

**Problem:** `localhost:9742` has no authentication. Any local process can call it.

**Fix:**
1. Generate a random bearer token at server startup.
2. Write it to `~/.psi-agent/token`.
3. Require `Authorization: Bearer <token>` header on all requests.
4. Update CLI scripts and stdio bridge to read the token file automatically.
5. Add rate limiting on write operations (rename, extract).

### 🟢 P3: More refactoring operations

Expand beyond rename. Each follows the same pattern as `MethodRenamer.kt`:

| Tool | IntelliJ Processor | Input |
|---|---|---|
| `psi_extract_method` | `ExtractMethodProcessor` | file, start line, end line, new method name |
| `psi_move_class` | `MoveClassesOrPackagesProcessor` | source file, target package |
| `psi_change_signature` | `ChangeSignatureProcessor` | method, new params, new return type |
| `psi_inline_method` | `InlineMethodProcessor` | file, method name |
| `psi_introduce_variable` | `IntroduceVariableHandler` | file, expression range, variable name |

### 🟢 P4: Real IDE installation

**Problem:** Plugin only runs in sandbox via `runIde`. Installing in a real IDE conflicts with the user's work.

**Fix:**
1. Make the HTTP port configurable via IDE settings (`PersistentStateComponent`).
2. Handle port conflicts — try a port range, write active port to `~/.psi-agent/port`.
3. Add a settings panel: enable/disable server, configure auth, view server status.
4. Publish to JetBrains Marketplace or a custom plugin repo.
5. Make `McpServerStartupActivity` implement `DumbAware` so it works during indexing.

### 🟢 P5: Multi-project support

**Problem:** One server per project on port 9742. Multiple open projects would conflict.

**Fix:**
1. Each `McpServerService` gets its own port (9742, 9743, ...).
2. Write a registry file: `~/.psi-agent/servers.json` → `[{project: "path", port: 9742}, ...]`.
3. CLI scripts and stdio bridge read the registry to find the right port.

## Architecture Decision: Headless vs. GUI

| Approach | Startup | PSI Access | User Experience |
|---|---|---|---|
| `runIde` (current) | ~30s, visible IDE | Full | User sees sandbox window |
| Headless mode | ~15s, no window | Full | Background daemon |
| Real IDE plugin | 0s (already running) | Full | Integrated with user's IDE |

**Long-term target:** Real IDE plugin (P4) with headless fallback for CI/scripting use cases.

## File Checklist for Each Improvement

For each new tool/feature, touch these files:
- `McpToolDefinitions.kt` — add tool schema
- `McpServer.kt` — add endpoint + dispatch
- New `refactoring/XxxProcessor.kt` — core PSI logic
- `plugin.xml` — register any new services
- `psi-agent.sh` / `psi-agent.ps1` — add CLI command
- `mcp-stdio-bridge.js` — no change needed (generic dispatch)
- `CLAUDE.md` / `.cursorrules` — document new tool
- Tests — `BasePlatformTestCase` test class

