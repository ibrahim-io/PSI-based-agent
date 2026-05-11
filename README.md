# PSI-based Agent

A working prototype that uses IntelliJ IDEA's **Program Structure Interface (PSI)** for code refactoring, instead of fragile text-based editing. Java rename/search are working; Kotlin parity and extract-method are being hardened.

## What is PSI?

PSI (Program Structure Interface) is IntelliJ IDEA's API for working with code as an **Abstract Syntax Tree (AST)** — the true semantic structure of source code — rather than raw text. This means:

| Approach | Text-based editing | PSI-based refactoring |
|---|---|---|
| Understands code | ❌ No — works on strings | ✅ Yes — understands types, scopes |
| Rename safety | ❌ Can hit wrong matches | ✅ Renames only the right symbol |
| Cross-file changes | ❌ Hard to get right | ✅ Automatic, via `RenameProcessor` |
| Handles overloads | ❌ Renames all occurrences | ✅ Knows which overload you mean |
| Undo/redo | ❌ Manual | ✅ Integrated with IntelliJ undo stack |

## Project Structure

```
PSI-based-agent/
├── build.gradle.kts           # Gradle IntelliJ Platform Plugin build
├── package.json               # Node manifest for the MCP stdio bridge
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat      # Gradle wrapper
├── CLAUDE.md                  # Agent instructions (Claude Code)
├── .mcp.json                  # Local MCP config for the stdio bridge
├── mcp-config.json            # MCP server config (Claude Desktop)
├── scripts/
│   ├── mcp-stdio-bridge.js    # SDK-backed MCP bridge -> IntelliJ HTTP server
│   ├── test-mcp-bridge.js     # Smoke test for initialize + tools/list
│   ├── psi-agent.sh           # CLI tool — talks to MCP server (bash)
│   ├── psi-agent.ps1          # CLI tool — talks to MCP server (PowerShell)
│   ├── refactor.sh            # Fallback shell refactoring (grep/sed)
│   └── search_code.sh         # Fallback "Ibrahim search" (grep-based)
└── src/
    ├── main/kotlin/io/github/ibrahimio/psiagent/
    │   ├── actions/RenameMethodAction.kt      # Right-click context menu action
    │   ├── mcp/
    │   │   ├── McpServer.kt                   # HTTP server exposing PSI tools
    │   │   ├── McpServerService.kt            # Lifecycle (start on project open)
    │   │   └── McpToolDefinitions.kt          # MCP tool schemas
        │   ├── refactoring/MethodRenamer.kt       # Core PSI rename logic
        │   ├── refactoring/MoveClassProcessor.kt  # PSI move-class / move-package refactoring
        │   ├── refactoring/MethodExtractor.kt     # Extract method prototype
    │   ├── search/PsiCodeSearcher.kt          # PSI-indexed code search
    │   └── visualization/PsiChangeVisualizer.kt  # Before/after PSI diff
    └── test/kotlin/.../refactoring/MethodRenamerTest.kt
```

## Building the Plugin

### Prerequisites
- JDK 17+
- Node.js 18+ (for the MCP bridge scripts)
- Internet access (to download IntelliJ SDK ~700 MB on first build)

```bash
# Install Node dependencies for the MCP bridge
npm ci

# Build the plugin ZIP (ready to install in IntelliJ)
./gradlew buildPlugin

# Run tests (uses a headless IntelliJ instance)
./gradlew test

# Run the plugin in a sandboxed IntelliJ instance (with GUI)
./gradlew runIde

# Run the plugin in headless mode (faster, no GUI — recommended for agents)
./gradlew runHeadless

# Smoke-test the MCP bridge against a mock backend
npm run test:bridge
```

## Using the Shell Scripts (Agent Integration)

These scripts let an AI agent trigger refactoring and search **without a running IntelliJ instance**.

### `scripts/refactor.sh` — Rename a method

```bash
# Rename method 'add' to 'sum' in Calculator.java
./scripts/refactor.sh rename-method src/Calculator.java add sum

# Search for all 'get*' methods
./scripts/refactor.sh search "get*" --type method

# Show PSI-like tree of a file
./scripts/refactor.sh visualize src/Calculator.java
```

### `scripts/search_code.sh` — Ibrahim Search

PSI-aware code search that finds definitions **and** usages:

```bash
# Find all occurrences of 'calculateTotal'
./scripts/search_code.sh "calculateTotal"

# Find methods matching a wildcard
./scripts/search_code.sh "get*" --type method

# Find class definition
./scripts/search_code.sh "UserService" --type class

# Find definition + all call sites
./scripts/search_code.sh "processOrder" --usages

# Search in a specific directory
./scripts/search_code.sh "MAX_SIZE" --type field --dir src/main
```

## Example Agent Workflow

Here is how an AI agent uses this tool to rename a method safely:

```
Agent: "I need to rename calculateTotal to computeTotal across the codebase"

Step 1: Find the method
$ ./scripts/search_code.sh "calculateTotal" --type method
  src/main/java/Order.java:12:  public double calculateTotal(List<Item> items) {

Step 2: Find all usages
$ ./scripts/search_code.sh "calculateTotal" --usages
  src/main/java/OrderService.java:34:  double total = order.calculateTotal(items);
  src/test/java/OrderTest.java:20:  assertThat(order.calculateTotal(items), is(10.0));

Step 3: Perform the rename
$ ./scripts/refactor.sh rename-method src/main/java/Order.java calculateTotal computeTotal

=== PSI Method Rename ===
File    : src/main/java/Order.java
Old name: calculateTotal
New name: computeTotal
Result  : SUCCESS
Changed : 3 line(s)
```

## Visualization Output

The `PsiChangeVisualizer` shows what changed at the **PSI node level** (not just text):

```
============================================================
PSI Change Visualization Report
============================================================
Operation : RENAME_METHOD
Summary   : Renamed 'add' → 'sum' (1 node(s) changed across 1 file(s))

Affected Files:
  - /path/to/Calculator.java

PSI Node Changes:
------------------------------------------------------------
  [PsiMethod] /path/to/Calculator.java:2
    BEFORE: public int add(int a, int b) { return a + b; }
    AFTER : public int sum(int a, int b) { return a + b; }

PSI Tree (JSON):
------------------------------------------------------------
{
  "operation": "rename_method",
  "success": true,
  "oldName": "add",
  "newName": "sum",
  "before": [{"type": "PsiMethodImpl", "name": "add", ...}],
  "after":  [{"type": "PsiMethodImpl", "name": "sum", ...}]
}
```

## Core Classes

### `MethodRenamer`
Uses `RenameProcessor` — the same engine IntelliJ uses internally — to rename a method and all its usages across the entire project. The rename is atomic: it either fully succeeds or rolls back.

### `MethodTargetResolver`
Resolves a symbol from either a definition site or a usage site under the caret, but only returns real method/function/class/interface targets. This keeps the rename action available from call sites in Java and from Kotlin function call sites without lighting up on unrelated symbols.

### `MethodExtractor`
Wraps IntelliJ's extract-method refactoring for a selected line range. The Java path uses `ExtractMethodHandler`. Kotlin extraction is now wired through `ExtractKotlinFunctionHandler` but still needs validation in a real IDE session. Extract-method is disabled in unit test mode because the full IDE refactoring pipeline is required, and the helper now rejects blank method names and invalid line ranges before PSI work begins.

### `InlineMethodProcessor`
Performs a PSI-based inline for simple Java/Kotlin methods and functions. The current implementation is intentionally conservative: it supports zero-parameter methods/functions with a single return expression or expression body, then replaces usage sites and removes the original declaration.

### `IntroduceVariableProcessor`
Introduces a local variable from a precisely selected Java or Kotlin expression. The current prototype is selection-based and conservative: it requires a precise line/column range, inserts the declaration immediately before the containing statement, and replaces the selected expression with the new variable name.

### `MoveClassProcessor`
Uses IntelliJ's move-class refactoring engine to move Java or Kotlin classes/objects to another package. The processor resolves or creates the target package directory, then lets IntelliJ update imports and references across the project.

### `PsiCodeSearcher`
Uses `PsiShortNamesCache` and `MethodReferencesSearch` for fast, index-backed searches that understand the code's type system (not just string matching).

### `PsiChangeVisualizer`
Generates before/after snapshots of the affected PSI nodes and formats them as a human-readable diff and a JSON tree — useful for an AI agent to understand exactly what changed.

## AI Agent Integration

The plugin exposes PSI operations to external AI agents via two approaches:

### Approach 1: MCP Server (runs inside IntelliJ)

When you open a project in the sandbox IDE (`./gradlew runIde`), an HTTP server starts automatically on `http://127.0.0.1:9742`. Any agent can call it.

> On first start the server creates a bearer token at `~/.psi-agent/token`. The CLI scripts and the MCP bridge read this file automatically. If you call the HTTP API manually with `curl`, add `Authorization: Bearer <token>`.

#### Native MCP Tool Discovery (Claude Code, Cursor)

For the best experience with Claude Code or Cursor, register the MCP bridge so the tools are discoverable as native tools:

**Windows:**
```bash
.\scripts\setup-claude-code.ps1
```

**Linux/macOS:**
```bash
./scripts/setup-claude-code.sh
```

Then:
1. Restart Claude Code or Cursor
2. Run `./gradlew runHeadless` (or `./gradlew runIde`)
3. Open a Java/Kotlin project in the IDE
4. The `psi_search`, `psi_rename`, and `psi_find_usages` tools will be available natively — no manual curl or approval prompts

`psi_extract_method` is wired through the same MCP/HTTP path, but it should still be treated as experimental until the Java/Kotlin test coverage is expanded.

If you prefer manual HTTP calls:

```bash
# Check the server is running
curl -H "Authorization: Bearer <token>" http://127.0.0.1:9742/api/health

# Search for methods
curl -X POST http://127.0.0.1:9742/api/search \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"query": "get*", "type": "method"}'

# Rename a method (updates all usages via PSI)
curl -X POST http://127.0.0.1:9742/api/rename \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"file": "src/main/java/Foo.java", "old_name": "calculate", "new_name": "compute"}'

# Find all usages of a method
curl -X POST http://127.0.0.1:9742/api/find-usages \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"method_name": "processOrder"}'

# MCP tool discovery (for MCP-compatible agents)
curl -H "Authorization: Bearer <token>" http://127.0.0.1:9742/mcp/tools/list
```

### Approach 2: CLI Scripts (wrapper around the MCP server)

The CLI scripts automatically read `~/.psi-agent/token` or the `PSI_AGENT_TOKEN` / `PSI_AGENT_TOKEN_FILE` environment variables.

```bash
# Bash (Linux/macOS/Git Bash)
./scripts/psi-agent.sh search "getUserById" --type method
./scripts/psi-agent.sh rename src/Foo.java calculate compute
./scripts/psi-agent.sh inline-method src/Foo.java calculate
./scripts/psi-agent.sh find-usages processOrder --class OrderService
./scripts/psi-agent.sh move-class src/Foo.java com.example.moved
./scripts/psi-agent.sh extract-method src/Foo.java calculateTotal 10 25
./scripts/psi-agent.sh health

# PowerShell (Windows)
.\scripts\psi-agent.ps1 search "getUserById" -Type method
.\scripts\psi-agent.ps1 rename src/Foo.java calculate compute
.\scripts\psi-agent.ps1 inline-method src/Foo.java calculate
.\scripts\psi-agent.ps1 find-usages processOrder -Class OrderService
.\scripts\psi-agent.ps1 extract-method src/Foo.java calculateTotal 10 25
```
