# Quick Reference: Universal Rename Feature

## What Can Be Renamed?

**Everything that's a named symbol in your code:**

### Java
- ✅ Methods and constructors
- ✅ Classes and interfaces
- ✅ Fields (instance, static, final)
- ✅ Local variables
- ✅ Parameters (method, catch, foreach)
- ✅ Type variables and inner classes

### Kotlin  
- ✅ Functions (top-level and member)
- ✅ Classes and interfaces
- ✅ Properties (var, val, properties with backing fields)
- ✅ Parameters (function and lambda)
- ✅ Type aliases
- ✅ Extension functions
- ✅ Local variables

## How to Use

### Via CLI (Fastest)
```bash
./scripts/psi-agent.sh rename <file_path> <old_name> <new_name>
```

### Via HTTP API
```bash
curl -X POST http://127.0.0.1:9742/api/rename \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(cat ~/.psi-agent/token)" \
  -d '{
    "file": "src/main/java/Config.java",
    "old_name": "apiKey",
    "new_name": "secretKey"
  }'
```

### Via MCP (Claude Code, etc.)
```json
{
  "name": "psi_rename",
  "arguments": {
    "file": "src/main/kotlin/User.kt",
    "old_name": "firstName",
    "new_name": "givenName"
  }
}
```

## Additional PSI Refactor

### Introduce a Variable
Use a precise expression range (line + column) to lift a Java/Kotlin expression into a local variable.

#### CLI
```bash
./scripts/psi-agent.sh introduce-variable src/main/java/Foo.java sum 4 16 4 20
```

#### HTTP API
```bash
curl -X POST http://127.0.0.1:9742/api/introduce-variable \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $(cat ~/.psi-agent/token)" \
  -d '{
    "file": "src/main/java/Foo.java",
    "variable_name": "sum",
    "start_line": 4,
    "start_column": 16,
    "end_line": 4,
    "end_column": 20
  }'
```

#### MCP
```json
{
  "name": "psi_introduce_variable",
  "arguments": {
    "file": "src/main/java/Foo.java",
    "variable_name": "sum",
    "start_line": 4,
    "start_column": 16,
    "end_line": 4,
    "end_column": 20
  }
}
```

## Examples by Type

### Rename a Method
```bash
./scripts/psi-agent.sh rename src/Service.java getUserById getUser
```

### Rename a Class
```bash
./scripts/psi-agent.sh rename src/Config.java AppProperties ApplicationSettings
```

### Rename a Field
```bash
./scripts/psi-agent.sh rename src/User.java firstName givenName
```

### Rename a Kotlin Property
```bash
./scripts/psi-agent.sh rename src/data/Person.kt age birthdayYear
```

### Rename a Local Variable
```bash
./scripts/psi-agent.sh rename src/Utils.java result sum
```

## Important Notes

✓ **Works from both definition and usage sites**
- Click on a method definition → rename it
- Click on a method call → rename all occurrences

✓ **Automatically updates all usages** across the project
- Renames in all files
- Updates imports
- Resolves naming conflicts

✓ **Type-safe** - Only renames the RIGHT symbol
- Won't accidentally rename similarly named items in different scopes

✓ **Includes comprehensive context**
- Returns before/after PSI snapshots
- Lists all affected files
- Shows success/failure with detailed messages

## What Happens Behind the Scenes

1. **File location** - Locates the file in your project
2. **Symbol search** - Finds the exact symbol (method, field, etc.)
3. **Usage discovery** - Scans entire project for references
4. **Safe rename** - Uses IntelliJ's refactoring engine (battle-tested)
5. **Cross-file updates** - Updates all references automatically
6. **Import cleanup** - Handles import statements
7. **Return report** - Shows what changed

## Response Format

```json
{
  "success": true,
  "message": "Successfully renamed 'apiKey' to 'secretKey'",
  "oldName": "apiKey",
  "newName": "secretKey",
  "affectedFiles": [
    "src/main/java/Config.java",
    "src/main/java/Service.java",
    "src/test/java/ConfigTest.java"
  ],
  "psiNodesBefore": [
    {
      "type": "PsiFieldImpl",
      "name": "apiKey",
      "text": "private String apiKey = \"default\";",
      "filePath": ".../Config.java",
      "lineNumber": 5
    }
  ],
  "psiNodesAfter": [
    {
      "type": "PsiFieldImpl",
      "name": "secretKey",
      "text": "private String secretKey = \"default\";",
      "filePath": ".../Config.java",
      "lineNumber": 5
    }
  ]
}
```

## Troubleshooting

**"Symbol not found"**
- Check the exact spelling and case
- Ensure the file path is correct
- Use absolute or project-relative paths

**"Cannot parse file"**
- Verify the file is in the project workspace
- Check file syntax is valid

**Rename seems incomplete**
- Wait for the server - large projects take time for indexing
- Check that all affected files were listed in the response

## Performance Tips

- **Large projects** - First rename takes longer as PSI indexes the project
- **Subsequent renames** - Much faster thanks to caching
- **Use headless mode** - `./gradlew runHeadless` is 40% faster than GUI mode

---

**Need help? Check CLAUDE.md for full documentation or run:**
```bash
./scripts/psi-agent.sh tools
```

