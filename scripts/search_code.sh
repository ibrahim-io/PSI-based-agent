#!/usr/bin/env bash
# Ibrahim Search — PSI-aware code search tool
# Finds method definitions, class declarations, fields, and usages.
#
# Usage:
#   ./scripts/search_code.sh <query> [--type method|class|field|all] [--usages] [--dir <path>]
#
# Examples:
#   ./scripts/search_code.sh "getUserById"
#   ./scripts/search_code.sh "get*" --type method
#   ./scripts/search_code.sh "UserService" --type class
#   ./scripts/search_code.sh "getUserById" --usages
#   ./scripts/search_code.sh "MAX_SIZE" --type field

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

print_usage() {
    cat <<EOF
Ibrahim Search — PSI-aware code search

Usage: $(basename "$0") <query> [options]

Arguments:
  query           Name pattern to search. Supports wildcards (* and ?).

Options:
  --type <type>   Filter by element type: method, class, field, all (default: all)
  --usages        Also search for call sites / usages of the symbol
  --dir <path>    Directory to search (default: project src/)
  -h, --help      Show this help

Examples:
  $(basename "$0") "calculate"               # Find anything named 'calculate'
  $(basename "$0") "get*" --type method      # Methods starting with 'get'
  $(basename "$0") "UserService" --type class
  $(basename "$0") "processOrder" --usages   # Definition + all call sites
EOF
}

# Convert a shell glob pattern to an ERE regex
# Escapes ERE metacharacters, then replaces glob wildcards (* and ?)
glob_to_ere() {
    local pattern="$1"
    # Escape ERE metacharacters using sed (avoids bash expansion issues with braces)
    # Then replace glob wildcards with their ERE equivalents
    printf '%s' "$pattern" \
        | sed 's/[\\.\[^$(){}|+]/\\&/g' \
        | sed 's/\*/.*/' \
        | sed 's/?/./'
}

search_methods() {
    local pattern="$1"
    local search_dir="$2"
    local ere
    ere="$(glob_to_ere "$pattern")"

    echo "--- Method Definitions ---"
    local output
    output="$(grep -rn --include="*.java" --include="*.kt" -E \
        "(public|private|protected|static) [a-zA-Z<>?,]+ ${ere}[[:space:]]*\(|fun[[:space:]]+${ere}[[:space:]]*\(" \
        "$search_dir" 2>/dev/null || true)"
    if [[ -n "$output" ]]; then
        echo "$output" | sed 's/^/  /'
    else
        echo "  (none found)"
    fi
}

search_classes() {
    local pattern="$1"
    local search_dir="$2"
    local ere
    ere="$(glob_to_ere "$pattern")"

    echo "--- Class / Interface / Object Definitions ---"
    local output
    output="$(grep -rn --include="*.java" --include="*.kt" -E \
        "(class|interface|enum|object)[[:space:]]+${ere}([[:space:]]|<|\{|:|,|\()" \
        "$search_dir" 2>/dev/null || true)"
    if [[ -n "$output" ]]; then
        echo "$output" | sed 's/^/  /'
    else
        echo "  (none found)"
    fi
}

search_fields() {
    local pattern="$1"
    local search_dir="$2"
    local ere
    ere="$(glob_to_ere "$pattern")"

    echo "--- Field / Property Definitions ---"
    local output
    output="$(grep -rn --include="*.java" --include="*.kt" -E \
        "(private|public|protected|static|final|val|var)[[:space:]]+[a-zA-Z<>?,]+ ${ere}[[:space:]]*[=;:]|(val|var)[[:space:]]+${ere}[[:space:]]*[=:]" \
        "$search_dir" 2>/dev/null || true)"
    if [[ -n "$output" ]]; then
        echo "$output" | sed 's/^/  /'
    else
        echo "  (none found)"
    fi
}

search_usages() {
    local pattern="$1"
    local search_dir="$2"
    local ere
    ere="$(glob_to_ere "$pattern")"

    echo "--- Usages / Call Sites ---"
    local output
    # Match call expressions: pattern( or .pattern(
    output="$(grep -rn --include="*.java" --include="*.kt" -E \
        "${ere}[[:space:]]*\(|\.${ere}[[:space:]]*\(" \
        "$search_dir" 2>/dev/null || true)"
    if [[ -n "$output" ]]; then
        echo "$output" | sed 's/^/  /'
    else
        echo "  (none found)"
    fi
}

search_all() {
    local pattern="$1"
    local search_dir="$2"

    echo "--- All Occurrences ---"
    local output
    output="$(grep -rn --include="*.java" --include="*.kt" \
        "$pattern" \
        "$search_dir" 2>/dev/null || true)"
    if [[ -n "$output" ]]; then
        echo "$output" | sed 's/^/  /'
    else
        echo "  (none found)"
    fi
}

# ── Argument parsing ──────────────────────────────────────────────────────────

if [[ $# -eq 0 ]] || [[ "${1:-}" == "-h" ]] || [[ "${1:-}" == "--help" ]]; then
    print_usage
    exit 0
fi

QUERY="${1:-}"
shift

TYPE_FILTER="all"
SHOW_USAGES=false
SEARCH_DIR="$PROJECT_ROOT/src"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --type)
            shift
            TYPE_FILTER="${1:-all}"
            shift
            ;;
        --usages)
            SHOW_USAGES=true
            shift
            ;;
        --dir)
            shift
            SEARCH_DIR="${1:-$PROJECT_ROOT/src}"
            shift
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            shift
            ;;
    esac
done

if [[ -z "$QUERY" ]]; then
    echo "ERROR: query is required" >&2
    print_usage
    exit 1
fi

if [[ ! -d "$SEARCH_DIR" ]]; then
    echo "ERROR: Search directory not found: $SEARCH_DIR" >&2
    exit 1
fi

# ── Execute search ────────────────────────────────────────────────────────────

echo "╔══════════════════════════════════════════╗"
echo "║         Ibrahim Search (PSI-aware)       ║"
echo "╚══════════════════════════════════════════╝"
echo "Query : $QUERY"
echo "Type  : $TYPE_FILTER"
echo "Dir   : $SEARCH_DIR"
echo ""

case "$TYPE_FILTER" in
    method)
        search_methods "$QUERY" "$SEARCH_DIR"
        ;;
    class)
        search_classes "$QUERY" "$SEARCH_DIR"
        ;;
    field)
        search_fields "$QUERY" "$SEARCH_DIR"
        ;;
    all|*)
        search_methods "$QUERY" "$SEARCH_DIR"
        echo ""
        search_classes "$QUERY" "$SEARCH_DIR"
        echo ""
        search_fields "$QUERY" "$SEARCH_DIR"
        ;;
esac

if $SHOW_USAGES; then
    echo ""
    search_usages "$QUERY" "$SEARCH_DIR"
fi

echo ""
echo "Search complete."
