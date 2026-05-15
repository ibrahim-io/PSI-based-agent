# PSI-based Agent: IntelliJ Code Refactoring via Program Structure Interface

A working prototype that uses IntelliJ IDEA's **Program Structure Interface (PSI)** for code refactoring, instead of fragile text-based editing. Java rename/search are working; Kotlin parity and extract-method are being hardened.

---

## ✅ Completed Work (Phase 1 & 2)

### Phase 1: Core PSI-Based Refactoring Engine

- **PSI-aware Code Search** (`PsiCodeSearcher`): Index-backed search using `PsiShortNamesCache` and `MethodReferencesSearch` for methods, classes, fields, properties, and variables. Understands type systems and cross-file references.
- **Universal Rename Engine** (`MethodRenamer`): Supports renaming any renamable PSI node — methods, functions, classes, interfaces, fields, properties, variables, parameters, and type aliases across Java and Kotlin. Uses IntelliJ's `RenameProcessor` for atomic, safe refactoring with full undo/redo support.
- **Extract Method** (`MethodExtractor`): Extracts code blocks into new methods/functions with proper parameter inference. Java path uses `ExtractMethodHandler`; Kotlin uses `ExtractKotlinFunctionHandler`.
- **Inline Method** (`InlineMethodProcessor`): Collapses simple methods/functions into their call sites (currently supports zero-parameter methods with single return expressions).
- **Introduce Variable** (`IntroduceVariableProcessor`): Lifts precise Java/Kotlin expressions into new local variables with correct scoping.
- **Move Class/Package** (`MoveClassProcessor`): Moves classes and Kotlin objects to different packages with automatic import/reference updates.
- **Method Target Resolver** (`MethodTargetResolver`): Resolves symbols from both declaration and usage sites, enabling context-aware refactoring actions from right-click menus and editor context.

### Phase 2: MCP Server & HTTP Bridge

- **HTTP MCP Server** (`McpServer`, `McpServerService`): Runs on `127.0.0.1:9742` with bearer token authentication. Exposes PSI operations via HTTP endpoints. Auto-starts when a project is open in the IDE.
- **Tool Discovery** (`McpToolDefinitions`): Implements MCP (`Model Context Protocol`) tool manifest for native integration with AI agents (Claude Code, Cursor, etc.).
- **CLI Wrappers** (`psi-agent.ps1`, `psi-agent.sh`): PowerShell and Bash scripts to call the HTTP server without needing a running IDE for certain operations.
- **Bearer Token File** (`~/.psi-agent/token`): Auto-generated security token; read by CLI and MCP bridge for authentication.
- **MCP Stdio Bridge** (`mcp-stdio-bridge.js`): Node.js bridge that connects Claude Code / Cursor to the local HTTP server via stdio protocol.

### Phase 3: Visual Tree Diagram & GUI Enhancement

**New this session:**

- **PsiTreeDiagram** (`visualization/PsiTreeDiagram.kt`): Custom Swing JComponent that renders a top-down tree diagram with:
  - **Nodes**: Rounded rectangles showing PSI tree node labels with truncation for long names.
  - **Edges**: Connecting lines showing parent-child relationships.
  - **Centered Tree Layout**: Automatically computes horizontal centering of parent nodes above children, with configurable level and sibling gaps.
  - **Color-coded by Status**: Nodes can be colored by change status (unchanged, changed, added, removed) for visualization of before/after diffs.

- **PSI Change Preview Tool Window Enhancement**: Replaced the static Swing `JTree` with the visual `PsiTreeDiagram` component. The diagram now:
  - Renders the PSI tree from `PsiChangeRecord` as a visual graph instead of text.
  - Shows Overview, Before, After, and Affected Files views with proper node layout.
  - Maintains the ASCII tree view on the bottom for text-based reference.

- **PSI Agent Tool Window** (new): Secondary tool window for browsing and searching the PSI agent endpoints directly from the IDE:
  - **PsiAgentToolWindowFactory**: Registers the tool window in the IDE.
  - **PsiAgentDiagramPanel**: Includes:
    - Status button to check if the PSI agent HTTP server is online.
    - Search field + Search button to query PSI via HTTP.
    - Canvas that renders results as draggable, interactive nodes in a grid layout.
    - **Tree button**: Arranges nodes as a top-down binary tree diagram.
    - **Reset layout button**: Restores the original grid layout after dragging/rearranging.
  - **PsiAgentClient**: HTTP client that:
    - Automatically reads bearer token from `~/.psi-agent/token` for authentication.
    - Calls `/api/search` and `/api/health` endpoints.
    - Handles lenient JSON parsing to work with various response formats.
    - Runs network operations on background threads to avoid blocking the UI.

- **Auto-Show Tool Window**: Modified `PsiDiffService.publish()` to automatically display the "PSI Change Preview" tool window when a refactoring completes and publishes a change record. No need to manually open View → Tool Windows anymore.

### Testing & Validation

- Build system: Gradle-based IntelliJ Plugin build with `runIde` (GUI) and `runHeadless` (faster, no GUI) tasks.
- Test suite: Unit tests for `MethodRenamer`, `MethodExtractor`, `InlineMethodProcessor`, `IntroduceVariableProcessor`, `DeleteSymbolProcessor`, `ChangeSignatureProcessor`, and target resolver.
- CI integration ready via standard Gradle/GitHub Actions patterns.

---

## 📋 Future Roadmap (Phase 3+)

### Immediate Next Steps (High Priority)

1. **Interactive Diagram Features**
   - Double-click on a diagram node to open the file in the editor and jump to the specific line.
   - Right-click context menu on nodes: "Rename", "Find Usages", "Extract Method", "Navigate to File".
   - Tooltip on hover showing node type, name, file path, and line number.

2. **Node Visualization Enhancements**
   - Color nodes by change status (green for added, red for removed, yellow for modified, gray for unchanged).
   - Show icons or glyph indicators for node types (method, class, field, variable, etc.).
   - Enable collapsing/expanding subtrees interactively.
   - Support pan and zoom controls for large trees.

3. **Advanced Layout Algorithms**
   - Implement force-directed layout for automatic node positioning that avoids overlaps.
   - Support circular/radial layouts for call graphs.
   - Use a third-party library (e.g., JGraphX or Graphviz integration) for professional graph rendering.

4. **Settings & Customization**
   - Add IDE Settings → Tools → PSI Agent configuration panel:
     - Auto-show behavior: Always, on change only, never, or silent (no focus).
     - Diagram animation/transition preferences.
     - Node size, spacing, and color themes.
   - Save user preferences to `~/.psi-agent/settings.json`.

### Medium-Term Enhancements (Phase 4)

5. **Larger Scale Code Analysis**
   - Visualize call graphs: show who calls whom across modules.
   - Visualize class hierarchies and dependency graphs.
   - Show unused methods, fields, and classes in red.
   - Highlight circular dependencies and complexity hotspots.

6. **Multi-File Refactoring Visualization**
   - Side-by-side before/after views of multiple affected files in the diagram.
   - Highlight cross-file relationships and import changes.
   - Show a summary matrix of changes per file.

7. **Batch Refactoring Support**
   - Rename multiple methods/classes in one operation via the diagram UI.
   - Preview and apply a batch of refactorings atomically.
   - Undo/redo as a single unit.

8. **Export & Reporting**
   - Export diagrams as SVG or PNG for documentation.
   - Generate refactoring reports (HTML, Markdown) summarizing changes.
   - Export as GraphML or GexF for external analysis tools.

### Long-Term Vision (Phase 5+)

9. **AI Agent Enhancements**
   - Implement feedback loops: agent proposes refactorings and visualizes them; user approves or feeds back corrections.
   - Real-time or batch analysis by external AI systems (static analysis, code review assistants).
   - Integration with GitHub Actions / CI for automated PSI-based checks.

10. **Language & Framework Extensions**
    - Extend from Java/Kotlin to other JVM languages (Scala, Groovy, Clojure).
    - Support Python, TypeScript, Go, and other languages via LSP (Language Server Protocol) integration.
    - Kotlin Multiplatform (KMP) support for cross-platform refactoring.

11. **Real-Time Collaboration**
    - WebSocket-based live diagram sync for multiple users editing the same codebase.
    - Collaborative refactoring: see team members' refactoring proposals in real-time.

12. **Machine Learning / Code Patterns**
    - Suggest refactorings based on detected code smells and patterns.
    - Learn common refactoring patterns from project history.
    - Recommend variable/method names based on semantic analysis.

### Testing & Documentation

- Expand test suite: add integration tests for all diagram rendering scenarios.
- Benchmark diagram rendering performance with large trees (1000+ nodes).
- Document best practices for PSI-based refactoring in a developer guide.
- Create video tutorials showing diagram-based refactoring workflows.

---

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
        │   ├── toolwindow/
        │   │   ├── PsiChangeToolWindowPanel.kt     # PSI Change Preview panel (now with PsiTreeDiagram)
        │   │   ├── PsiAgentToolWindowFactory.kt    # PSI Agent tool window registration
        │   │   ├── PsiAgentDiagramPanel.kt         # Search & diagram UI for PSI Agent tool window
        │   │   ├── PsiAgentClient.kt               # HTTP client for PSI agent endpoints
        │   │   └── PsiAgentToolWindowFactory.kt    # Factory for creating tool window content
    │   └── visualization/PsiChangeVisualizer.kt  # Before/after PSI diff
        │   └── PsiTreeDiagram.kt                   # Visual tree renderer with nodes and edges
    └── test/kotlin/.../refactoring/MethodRenamerTest.kt
```

## Building the Plugin

### Quick Start: See the Visual Tree Diagram in Action

1. **Build and run the IDE with the plugin:**
```bash
# Install Node dependencies (one-time)
npm ci

# Run the IDE with the plugin loaded
./gradlew runIde
```

2. **Open or create a Java/Kotlin project in the IDE.**

3. **Trigger a PSI refactoring:**
   - Right-click on a method/class name and select "Rename Symbol (PSI Agent)"
   - Or go to the PSI Agent tool window (View → Tool Windows → PSI Agent) to search for code and see the diagram

4. **Observe the automatic UI:**
   - The "PSI Change Preview" tool window will automatically appear on the right side.
   - A tree diagram with nodes and connecting lines will render, showing the PSI structure that changed.
   - Use View → Tree button to arrange nodes as a hierarchical tree; use Reset layout to restore the original layout.

5. **Interact with the diagram:**
   - Hover over nodes to see tooltips.
   - Drag nodes to reposition them (layout will be preserved on refresh).
   - The ASCII tree view at the bottom provides an alternative text representation.

### Detailed Build Instructions

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

---

## Visual Tree Diagram Features

### PSI Change Preview Tool Window

When a refactoring is executed (success or failure), the **PSI Change Preview** tool window automatically appears with a visual tree diagram showing:

- **Nodes**: Each node in the PSI tree is drawn as a rounded rectangle with the node name.
- **Edges**: Lines connecting parent nodes to their children show the tree structure.
- **Centered Layout**: Parents are automatically centered above their children for readability.
- **Multiple Views**: Use the View selector to switch between:
  - **Overview**: Summary of the refactoring (tool name, status, affected files).
  - **Before PSI**: Tree of the code before refactoring.
  - **After PSI**: Tree of the code after refactoring.
  - **Affected Files**: List of files modified by the refactoring.
- **ASCII Fallback**: A text-based tree view below the diagram for accessibility.

### PSI Agent Tool Window

The **PSI Agent** tool window (View → Tool Windows → PSI Agent) provides direct access to PSI search:

- **Status Button**: Check if the HTTP agent server is online.
- **Search Field**: Query the PSI database (methods, classes, fields, etc.).
- **Diagram Canvas**: Results appear as interactive nodes that can be:
  - **Dragged**: Reposition nodes with the mouse.
  - **Tree Arranged**: Click "Tree" button to auto-layout as a binary tree.
  - **Reset**: Click "Reset layout" to restore the original grid layout.
- **Future**: Double-click to open files in the editor; right-click for context menu (rename, find usages, extract method).

---

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

### `PsiTreeDiagram`
Renders a `PsiTreePresentationNode` as an interactive, visual tree diagram with nodes and connecting edges. Features include:
- **Node Layout**: Computes a top-down layout with parent nodes centered above their children.
- **Drawing**: Rounded rectangular nodes with text labels, edges as lines.
- **Status Colors**: Nodes can be colored by their change status for visualization of before/after diffs (not yet fully implemented, but framework is in place).

### `PsiAgentToolWindowFactory` & `PsiAgentDiagramPanel`
Creates a secondary tool window ("PSI Agent") in the IDE sidebar that allows direct interaction with the PSI agent HTTP server:
- Search field to query PSI endpoints directly.
- Canvas that renders search results as draggable nodes.
- Tree and Reset layout buttons to switch between layout modes.
- Status indicator showing if the agent is online.

### `PsiAgentClient`
HTTP client that communicates with the PSI agent server (`http://127.0.0.1:9742`). Reads the bearer token from `~/.psi-agent/token` and handles lenient JSON parsing of responses.

### `PsiCodeSearcher`
Uses `PsiShortNamesCache` and `MethodReferencesSearch` for fast, index-backed searches that understand the code's type system (not just string matching).

### `PsiChangeVisualizer`
Generates before/after snapshots of the affected PSI nodes and formats them as a human-readable diff and a JSON tree — useful for an AI agent to understand exactly what changed.

### `PsiDiffService`
Project-level service that publishes `PsiChangeRecord` events when refactorings complete. Now automatically shows the "PSI Change Preview" tool window when a change record is published, providing immediate visual feedback.

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

# Create a new method in a class
curl -X POST http://127.0.0.1:9742/api/create-method \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"file": "src/main/java/Calculator.java", "class_name": "Calculator", "method_name": "add", "return_type": "int", "parameters": "int a, int b", "visibility": "public"}'

# Create a new class
curl -X POST http://127.0.0.1:9742/api/create-class \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"file": "src/main/java/NewClass.java", "package_name": "com.example", "class_name": "MyService", "extends": ""}'

# Create a new field in a class
curl -X POST http://127.0.0.1:9742/api/create-field \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"file": "src/main/java/Calculator.java", "class_name": "Calculator", "field_name": "precision", "field_type": "int", "initial_value": "2", "visibility": "private"}'

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
