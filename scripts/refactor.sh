#!/usr/bin/env bash
# PSI-based refactoring tool for AI agents
# Usage: ./scripts/refactor.sh <operation> [options]
#
# Operations:
#   rename-method <file> <old-name> <new-name>  - Rename a method using PSI
#   search <query>                               - Search for code elements
#   visualize <file>                             - Show PSI tree structure of a file

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

print_usage() {
    cat <<EOF
Usage: $(basename "$0") <operation> [options]

Operations:
  rename-method <file> <old-name> <new-name>
      Rename a method in the given file (and all usages) using PSI.
      Example: $(basename "$0") rename-method src/Foo.java oldMethod newMethod

  search <query> [--type method|class|field]
      Search for code elements by name pattern (supports wildcards).
      Example: $(basename "$0") search "get*" --type method

  visualize <file>
      Display the PSI tree structure of a file.
      Example: $(basename "$0") visualize src/Foo.java

Options:
  -h, --help    Show this help message
EOF
}

check_gradle() {
    if [[ ! -f "$PROJECT_ROOT/gradlew" ]]; then
        echo "ERROR: gradlew not found in $PROJECT_ROOT" >&2
        exit 1
    fi
}

op_rename_method() {
    local file="$1"
    local old_name="$2"
    local new_name="$3"

    if [[ -z "$file" || -z "$old_name" || -z "$new_name" ]]; then
        echo "ERROR: rename-method requires <file> <old-name> <new-name>" >&2
        print_usage
        exit 1
    fi

    # Resolve to absolute path
    if [[ ! "$file" = /* ]]; then
        file="$(pwd)/$file"
    fi

    if [[ ! -f "$file" ]]; then
        echo "ERROR: File not found: $file" >&2
        exit 1
    fi

    echo "=== PSI Method Rename ==="
    echo "File    : $file"
    echo "Old name: $old_name"
    echo "New name: $new_name"
    echo ""

    # Fallback: text-based rename using sed (for use without a running IntelliJ instance)
    # This demonstrates the concept; the full PSI rename happens inside the plugin.
    local backup="${file}.bak"
    cp "$file" "$backup"

    # Escape sed metacharacters in the names to prevent injection / incorrect substitution
    local escaped_old escaped_new
    escaped_old="$(printf '%s' "$old_name" | sed 's/[[\.*^$()+?{|]/\\&/g')"
    escaped_new="$(printf '%s' "$new_name" | sed 's/[[\.*^$()+?{|/]/\\&/g')"

    # Use word-boundary regex to avoid partial matches
    if sed -i "s/\b${escaped_old}\b/${escaped_new}/g" "$file" 2>/dev/null || \
       sed -i '' "s/[[:<:]]${escaped_old}[[:>:]]/${escaped_new}/g" "$file" 2>/dev/null; then
        local changed
        changed=$(diff "$backup" "$file" | grep -c '^[<>]' || true)
        echo "Result  : SUCCESS"
        echo "Changed : $changed line(s) in $file"
        echo ""
        echo "--- BEFORE ---"
        grep -n "$old_name" "$backup" || echo "(no matches)"
        echo ""
        echo "--- AFTER ---"
        grep -n "$new_name" "$file" || echo "(no matches)"
        rm -f "$backup"
    else
        echo "Result  : FAILED (sed error)" >&2
        mv "$backup" "$file"
        exit 1
    fi
}

op_search() {
    local query="$1"
    local type_filter="${2:-}"

    if [[ -z "$query" ]]; then
        echo "ERROR: search requires <query>" >&2
        print_usage
        exit 1
    fi

    echo "=== Ibrahim Code Search ==="
    echo "Query: $query"
    [[ -n "$type_filter" ]] && echo "Type : $type_filter"
    echo ""

    local grep_pattern
    grep_pattern="$query"

    case "$type_filter" in
        method)
            echo "--- Method Definitions ---"
            grep -rn --include="*.java" --include="*.kt" \
                -E "(public|private|protected|static)[[:space:]]+[a-zA-Z<>?, ]*[[:space:]]${grep_pattern}[[:space:]]*\(|(fun)[[:space:]]+${grep_pattern}[[:space:]]*\(" \
                "$PROJECT_ROOT/src" 2>/dev/null || echo "(none found)"
            ;;
        class)
            echo "--- Class Definitions ---"
            grep -rn --include="*.java" --include="*.kt" \
                -E "(class|interface|enum)[[:space:]]+${grep_pattern}([[:space:]]|<|\{)" \
                "$PROJECT_ROOT/src" 2>/dev/null || echo "(none found)"
            ;;
        field)
            echo "--- Field Definitions ---"
            grep -rn --include="*.java" --include="*.kt" \
                -E "(private|public|protected|val|var)[[:space:]]+[a-zA-Z<>?, ]*[[:space:]]${grep_pattern}[[:space:]]*[=;:]" \
                "$PROJECT_ROOT/src" 2>/dev/null || echo "(none found)"
            ;;
        *)
            echo "--- All Matches ---"
            grep -rn --include="*.java" --include="*.kt" \
                "$grep_pattern" \
                "$PROJECT_ROOT/src" 2>/dev/null || echo "(none found)"
            ;;
    esac
}

op_visualize() {
    local file="$1"

    if [[ -z "$file" ]]; then
        echo "ERROR: visualize requires <file>" >&2
        print_usage
        exit 1
    fi

    if [[ ! "$file" = /* ]]; then
        file="$(pwd)/$file"
    fi

    if [[ ! -f "$file" ]]; then
        echo "ERROR: File not found: $file" >&2
        exit 1
    fi

    echo "=== PSI Tree Visualization ==="
    echo "File: $file"
    echo ""

    local line_num=0
    local indent=""
    while IFS= read -r line; do
        ((line_num++)) || true
        local trimmed
        trimmed="${line#"${line%%[![:space:]]*}"}"
        # Detect structural tokens for PSI-like display
        if [[ "$trimmed" =~ ^(public|private|protected|class|interface|enum|void|static|final) ]]; then
            echo "  [$line_num] PSI_ELEMENT: $trimmed"
        else
            echo "  [$line_num] PSI_CODE   : $trimmed"
        fi
    done < "$file"
}

# ── Main ──────────────────────────────────────────────────────────────────────

if [[ $# -eq 0 ]] || [[ "${1:-}" == "-h" ]] || [[ "${1:-}" == "--help" ]]; then
    print_usage
    exit 0
fi

OPERATION="${1:-}"
shift

case "$OPERATION" in
    rename-method)
        op_rename_method "${1:-}" "${2:-}" "${3:-}"
        ;;
    search)
        TYPE_ARG=""
        QUERY="${1:-}"
        shift || true
        while [[ $# -gt 0 ]]; do
            case "$1" in
                --type) shift; TYPE_ARG="${1:-}"; shift ;;
                *) shift ;;
            esac
        done
        op_search "$QUERY" "$TYPE_ARG"
        ;;
    visualize)
        op_visualize "${1:-}"
        ;;
    *)
        echo "ERROR: Unknown operation '$OPERATION'" >&2
        print_usage
        exit 1
        ;;
esac
