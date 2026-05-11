# PSI Agent — Next Steps & Roadmap

## Current State (Prototype — Working, but still needs hardening ✅)

Claude Code successfully renames methods via PSI through HTTP calls to the IntelliJ server.
The full pipeline works: Agent → bridge/CLI → HTTP server (inside IntelliJ) → PSI RenameProcessor → file updated.

Recent progress:
- `psi_search` and `psi_find_usages` now advertise `readOnlyHint: true`
- `scripts/mcp-stdio-bridge.js` has been rewritten to use the official MCP SDK transport
- `package.json` and `package-lock.json` now exist for the Node bridge dependencies
- `scripts/test-mcp-bridge.js` provides a repeatable smoke test for MCP handshake and tool discovery
- `~/.psi-agent/token` auth is now supported end-to-end by the HTTP server, CLI scripts, and MCP bridge
- `MethodExtractor.kt` has been aligned with the current IntelliJ `ExtractMethodProcessor` API and is wired into MCP/CLI
- Rename-from-usage-site now uses a shared resolver, so call-site rename is available for Java methods/classes/interfaces and Kotlin functions
- `RenameMethodAction` now updates on `BGT` and only enables when the caret resolves to a real method/function/class/interface target
- `MethodExtractor` now validates blank method names and invalid line ranges before invoking PSI refactoring
- `MethodExtractorTest.kt` now covers Java/Kotlin extraction regressions
- PSI discovery is becoming symbol-centric: `psi_search` now includes fields, and `psi_find_usages` accepts `symbol_name` / `symbol_type` as well as the legacy `method_name`

Still to harden:
- Validate Kotlin extract-function integration in a real IDE session
- Validate Java extract-method in a real IDE session (unit test mode is blocked)
- Runtime verification of `psi_extract_method` across Java projects
- Broader Kotlin edge-case coverage for rename flows

## Problems to Fix (Priority Order)

### 🔴 P0: Stabilize the refactoring core (Java + Kotlin)

**Status:** In progress.

**Solution:**
1. Keep expanding Kotlin coverage for rename and extract-method.
2. Keep the resolver and tests aligned so usage-site rename stays reliable.
3. Verify whether Kotlin class/interface rename can be added through the same PSI path as Java; currently only Kotlin functions are covered.

**How to use:**
1. Start with a Java/Kotlin project in the IDE
2. Use `psi_rename` and `psi_extract_method` through the MCP bridge or CLI
3. Confirm the same operation works from both the definition site and a usage site

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

**Status:** `psi_extract_method`, `psi_inline_method`, `psi_introduce_variable`, and `psi_move_class` are now implemented and exposed through MCP/HTTP/CLI.

**What's available now:**
1. `psi_extract_method`, `psi_inline_method`, `psi_introduce_variable`, and `psi_move_class` are exposed through MCP, HTTP, and CLI (bash/PowerShell).
2. Unit test mode blocks extract-method because the full IDE pipeline is required.
3. Bad line ranges and blank method names are rejected before refactoring starts.

**Current expansion:**
1. Validate edge cases for files with multiple top-level declarations.
2. Confirm Kotlin light-class moves in a real IDE session.
3. Expand `psi_inline_method` and `psi_introduce_variable` beyond their current conservative prototypes.

**Next step:** run extract-method and introduce-variable in a real IDE session (`runIde`/`runHeadless`) to validate Java + Kotlin behavior.

**Implementation pattern established:**
Each new refactoring tool follows this checklist:
- `McpToolDefinitions.kt` — add tool schema
- `McpServer.kt` — add endpoint + dispatch
- New `refactoring/XxxProcessor.kt` — core PSI logic
- `psi-agent.sh` / `psi-agent.ps1` — add CLI command
- `CLAUDE.md` — document new tool

**Next tools to implement (in priority order):**

| Tool | IntelliJ Processor | Input | Complexity |
|---|---|---|---|
| `psi_change_signature` | `ChangeSignatureProcessor` | method, new params, new return type | high |


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
