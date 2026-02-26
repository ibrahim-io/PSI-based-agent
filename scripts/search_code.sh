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
glob_to_ere() {
    local pattern="$1"
    # Escape regex metacharacters except * and ?
    pattern="${pattern//./\\.}"
    pattern="${pattern//\*/.*}"
    pattern="${pattern//\?/.}"
    echo "$pattern"
}

search_methods() {
    local pattern="$1"
    local search_dir="$2"
    local ere
    ere="$(glob_to_ere "$pattern")"

    echo "--- Method Definitions ---"
    grep -rn --include="*.java" --include="*.kt" -E \
        "(public|private|protected|internal|static|override|suspend|\s)+(fun\s+${ere}|[a-zA-Z<>\[\]?,\s]+\s+${ere})\s*\(" \
        "$search_dir" 2>/dev/null \
        | sed 's/^/  /' \
        || echo "  (none found)"
}

search_classes() {
    local pattern="$1"
    local search_dir="$2"
    local ere
    ere="$(glob_to_ere "$pattern")"

    echo "--- Class / Interface / Object Definitions ---"
    grep -rn --include="*.java" --include="*.kt" -E \
        "(class|interface|enum|object|abstract class|data class)\s+${ere}(\s|<|\{|:)" \
        "$search_dir" 2>/dev/null \
        | sed 's/^/  /' \
        || echo "  (none found)"
}

search_fields() {
    local pattern="$1"
    local search_dir="$2"
    local ere
    ere="$(glob_to_ere "$pattern")"

    echo "--- Field / Property Definitions ---"
    grep -rn --include="*.java" --include="*.kt" -E \
        "(private|public|protected|internal|static|final|val|var)\s+.*\s+${ere}\s*[=;:]" \
        "$search_dir" 2>/dev/null \
        | sed 's/^/  /' \
        || echo "  (none found)"
}

search_usages() {
    local pattern="$1"
    local search_dir="$2"
    local ere
    ere="$(glob_to_ere "$pattern")"

    echo "--- Usages / Call Sites ---"
    # Match call expressions: pattern( or pattern . or .pattern
    grep -rn --include="*.java" --include="*.kt" -E \
        "(${ere}\s*\(|\.${ere}\s*\(|new\s+${ere}\s*\()" \
        "$search_dir" 2>/dev/null \
        | sed 's/^/  /' \
        || echo "  (none found)"
}

search_all() {
    local pattern="$1"
    local search_dir="$2"
    local ere
    ere="$(glob_to_ere "$pattern")"

    echo "--- All Occurrences ---"
    grep -rn --include="*.java" --include="*.kt" \
        "$pattern" \
        "$search_dir" 2>/dev/null \
        | sed 's/^/  /' \
        || echo "  (none found)"
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
