# PSI-based Agent

A working prototype that uses IntelliJ IDEA's **Program Structure Interface (PSI)** for code refactoring, instead of fragile text-based editing.

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
├── settings.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat      # Gradle wrapper
├── scripts/
│   ├── refactor.sh            # Shell interface for AI agents
│   └── search_code.sh         # "Ibrahim search" — PSI-aware code search
└── src/
    ├── main/kotlin/io/github/ibrahimio/psiagent/
    │   ├── actions/RenameMethodAction.kt      # Right-click context menu action
    │   ├── refactoring/MethodRenamer.kt       # Core PSI rename logic
    │   ├── search/PsiCodeSearcher.kt          # Ibrahim search implementation
    │   └── visualization/PsiChangeVisualizer.kt  # Before/after PSI diff
    └── test/kotlin/.../refactoring/MethodRenamerTest.kt
```

## Building the Plugin

### Prerequisites
- JDK 17+
- Internet access (to download IntelliJ SDK ~700 MB on first build)

```bash
# Build the plugin ZIP (ready to install in IntelliJ)
./gradlew buildPlugin

# Run tests (uses a headless IntelliJ instance)
./gradlew test

# Run the plugin in a sandboxed IntelliJ instance
./gradlew runIde
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

### `PsiCodeSearcher`
Uses `PsiShortNamesCache` and `MethodReferencesSearch` for fast, index-backed searches that understand the code's type system (not just string matching).

### `PsiChangeVisualizer`
Generates before/after snapshots of the affected PSI nodes and formats them as a human-readable diff and a JSON tree — useful for an AI agent to understand exactly what changed.
