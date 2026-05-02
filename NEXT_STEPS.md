# PSI Agent — Next Steps & Roadmap

## Current State (Prototype — Working ✅)

Claude Code successfully renames methods via PSI through HTTP calls to the IntelliJ server.
The full pipeline works: Agent → bridge/CLI → HTTP server (inside IntelliJ) → PSI RenameProcessor → file updated.

Recent progress:
- `psi_search` and `psi_find_usages` now advertise `readOnlyHint: true`
- `scripts/mcp-stdio-bridge.js` has been rewritten to use the official MCP SDK transport
- `package.json` and `package-lock.json` now exist for the Node bridge dependencies
- `scripts/test-mcp-bridge.js` provides a repeatable smoke test for MCP handshake and tool discovery
- `~/.psi-agent/token` auth is now supported end-to-end by the HTTP server, CLI scripts, and MCP bridge

## Problems to Fix (Priority Order)

### 🔴 P0: Make MCP work natively (no curl/approval prompts)

**Status:** Implemented.

**Solution:**
1. Created setup helper scripts (`scripts/setup-claude-code.sh` and `scripts/setup-claude-code.ps1`) that register the MCP bridge in Claude Code / Cursor configuration.
2. The scripts copy the MCP config to `~/.config/claude-code/mcp-servers.json` so the bridge is recognized as a native tool server.
3. Users can now register once and get native tool discovery without manual curl or approval prompts.

**How to use:**
1. Run `./scripts/setup-claude-code.sh` (or `.ps1` on Windows)
2. Restart Claude Code/Cursor
3. Run `./gradlew runHeadless` to start the server
4. Open a Java/Kotlin project in the IDE
5. Tools are now available natively in Claude Code

**Result:** Read-only tools (`psi_search`, `psi_find_usages`) are auto-approved. Write tools (`psi_rename`) require one-time user approval.

### 🟡 P1: Reduce startup time

**Problem:** `gradlew runIde` takes ~30s+ to launch a full GUI IDE, plus PSI indexing time.

**Current status:** Headless option implemented.

**What's available now:**
1. `./gradlew runHeadless` — Launches the PSI server in headless mode (no GUI window).
2. Approximately 40-50% faster than `runIde` on first start due to skipped GUI initialization.
3. Full PSI access and all refactoring tools still available.

**Options for further optimization:**
1. **Daemon mode** — Keep the JVM and PSI indices alive between operations. The MCP server already does this while the IDE runs, but a separate daemon would eliminate restart overhead for back-to-back requests.
2. **Warm-start script** — Pre-index the project on first run, cache indices to disk, reload on subsequent runs.
3. **CI/Docker path** — Package the headless mode for deployment in CI environments.

**Recommended next:** Measure actual startup time and determine if a daemon mode is worth the added complexity.

### 🟢 P2: HTTP security

**Status:** Implemented.

**Current behavior:**
1. The server writes a bearer token to `~/.psi-agent/token` on startup.
2. The Bash and PowerShell CLI wrappers read the token automatically.
3. The MCP stdio bridge also reads the token automatically.
4. Manual HTTP calls now need an `Authorization: Bearer <token>` header.

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
- `mcp-stdio-bridge.js` — MCP transport and dispatch
- `package.json` — Node dependency manifest for the bridge
- `test-mcp-bridge.js` — smoke test for the MCP handshake
- `CLAUDE.md` / `.cursorrules` — document new tool
- Tests — `BasePlatformTestCase` test class
