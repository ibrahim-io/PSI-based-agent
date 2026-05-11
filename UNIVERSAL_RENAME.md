# Universal PSI Node Renaming — Complete Implementation

## Goal Achieved ✅

**Every single type of PSI node in your codebase is now renamable.**

The `psi_rename` tool now supports comprehensive renaming of ALL symbolic elements, not just methods and classes.

## What Changed

### 1. **MethodRenamer.kt** — Expanded Symbol Search

**Old behavior:** Only searched for `PsiMethod`, `PsiClass`, and `KtNamedFunction`

**New behavior:** Searches for:
- ✅ Methods and functions (Java/Kotlin)
- ✅ Classes and interfaces (Java/Kotlin)
- ✅ Fields (Java)
- ✅ Variables, parameters, properties, type aliases (all kinds)
- ✅ **Any other `PsiNamedElement` type**

**Key changes:**
```kotlin
fun findSymbolInFile(psiFile: PsiFile, symbolName: String): PsiNamedElement? {
    // Search Java/Kotlin methods and functions
    // Search Java/Kotlin classes
    // Search Java fields
    // Search all other named elements (variables, parameters, properties, aliases, etc.)
    // This fallback catches ANY renamable symbol
}
```

The implementation now uses two-phase search:
1. **Specific types first** (methods, classes, functions, fields) — for efficiency
2. **Generic `PsiNamedElement` fallback** — catches everything else

### 2. **MethodTargetResolver.kt** — Universal Symbol Resolution

**Old behavior:** Only resolved `PsiMethod`, `PsiClass`, and `KtNamedFunction` at the caret

**New behavior:** Resolves to ANY `PsiNamedElement` at the cursor position

**Key improvements:**
```kotlin
private fun resolveFromReferences(start: PsiElement?): PsiNamedElement? {
    // Resolves across 8 parent levels in the PSI tree
    // Catches-all with final `is PsiNamedElement` check
}

private fun resolveFromTree(elementAtCaret: PsiElement?): PsiNamedElement? {
    // Tries specific parent types first
    // Falls back to generic PsiNamedElement
}
```

This means you can rename **from either a usage site OR the declaration site**, for any symbol type.

### 3. **MCP Tool Definitions Updated**

Updated documentation in `McpToolDefinitions.kt` to reflect the new capabilities:

```kotlin
"psi_rename" → "Rename ANY PSI node (methods, classes, variables, parameters, 
               fields, properties, type aliases, etc.)"
```

### 4. **Public Documentation (CLAUDE.md)**

Updated with:
- Examples of renaming fields, properties, variables
- Clarification that ANY symbol can be renamed
- Expanded "When to use these tools" section

## How It Works

### The Universal Rename Pipeline

```
Agent → psi_rename(file, old_name, new_name)
         ↓
    File location resolution
         ↓
    Symbol search (methods → classes → fields → generic named elements)
         ↓
    RenameProcessor.findUsages() [built-in IntelliJ magic]
         ↓
    Automatic cross-file updates
         ↓
    PSI snapshots (before/after) returned
```

### Why This Works

- **IntelliJ's `RenameProcessor` is already universal** — it handles any `PsiElement`,  not just methods. We just needed to broaden what we search for.
- **`PsiNamedElement` is the base interface** for all renamable symbols.
- **The reference resolver is already generic** — it uses PSI tree traversal and reference resolution, which work for all named elements.

## Examples

### Rename a field:
```bash
./scripts/psi-agent.sh rename src/main/java/Config.java apiKey secretKey
```

### Rename a local variable (via JSON):
```bash
curl -X POST http://127.0.0.1:9742/api/rename \
  -H "Content-Type: application/json" \
  -d '{
    "file": "src/main/java/Utils.java", 
    "old_name": "temp",
    "new_name": "buffer"
  }'
```

### Rename a Kotlin property:
```bash
./scripts/psi-agent.sh rename src/main/kotlin/User.kt firstName givenName
```

## Testing

All new tests pass:
- ✅ Field renaming
- ✅ Multi-type symbol lookup
- ✅ Generic `PsiNamedElement` fallback
- ✅ Comprehensive resolver coverage

## Architecture Benefits

1. **Minimal code changes** — No complex reflection or custom handlers needed
2. **Leverages IntelliJ's built-in refactoring** — Rock-solid, well-tested
3. **Performance** — Specific type checks first, generic fallback only when needed
4. **Extensibility** — New symbol types automatically supported as long as they implement `PsiNamedElement`
5. **Safety** — `RenameProcessor` handles all edge cases (scope conflicts, imports, etc.)

## What's Next

With universal renaming in place, the next improvements could be:

1. **Context-aware suggest** — Recommend rename targets based on usage statistics
2. **Batch rename** — Rename multiple symbols in one operation
3. **Type-safe rename** — Suggest renames that respect type signatures
4. **Rename all occurrences in scope** — Advanced filtering options
5. **Integration with code analysis** — Suggest renames based on code metrics

## Files Modified

- `src/main/kotlin/.../refactoring/MethodRenamer.kt`
- `src/main/kotlin/.../actions/MethodTargetResolver.kt`
- `src/main/kotlin/.../mcp/McpToolDefinitions.kt`
- `src/test/kotlin/.../refactoring/MethodRenamerTest.kt`
- `CLAUDE.md` (documentation)
- `NEXT_STEPS.md` (roadmap update pending)

## Verification

✅ All tests pass  
✅ No compilation errors  
✅ Backward compatible (existing method/class renames still work)  
✅ Universal fallback mechanism in place  

---

**The aim is achieved: Every single type of node on the PSI is now renamable.** 🎉

