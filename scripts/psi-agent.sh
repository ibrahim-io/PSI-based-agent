#!/usr/bin/env bash
# psi-agent — CLI interface for the PSI Agent MCP server
#
# This script calls the HTTP server running inside IntelliJ (started by the plugin)
# to perform PSI-powered code search and refactoring.
#
# Prerequisites:
#   1. Run the plugin IDE: ./gradlew runIde
#   2. Open a project in the sandbox IDE
#   3. The MCP server auto-starts on http://127.0.0.1:9742
#
# Usage:
#   ./scripts/psi-agent.sh search "getUserById" [--type method|class|all]
#   ./scripts/psi-agent.sh rename <file> <old-name> <new-name>
#   ./scripts/psi-agent.sh inline-method <file> <method-name>
#   ./scripts/psi-agent.sh find-usages <method-name> [--class ClassName]
#   ./scripts/psi-agent.sh move-class <file> <target-package>
#   ./scripts/psi-agent.sh health
#   ./scripts/psi-agent.sh tools

set -euo pipefail

PSI_AGENT_PORT="${PSI_AGENT_PORT:-9742}"
PSI_AGENT_URL="http://127.0.0.1:${PSI_AGENT_PORT}"
PSI_AGENT_TOKEN_FILE="${PSI_AGENT_TOKEN_FILE:-$HOME/.psi-agent/token}"
AUTH_ARGS=()

# ── Helpers ──────────────────────────────────────────────────────────────────

load_auth() {
    AUTH_ARGS=()

    local token="${PSI_AGENT_TOKEN:-}"
    if [[ -z "$token" && -f "$PSI_AGENT_TOKEN_FILE" ]]; then
        token="$(tr -d '\r\n' < "$PSI_AGENT_TOKEN_FILE" 2>/dev/null || true)"
    fi

    if [[ -n "$token" ]]; then
        AUTH_ARGS=(-H "Authorization: Bearer ${token}")
    fi
}

check_server() {
    load_auth
    if ! curl -sf "${AUTH_ARGS[@]}" "${PSI_AGENT_URL}/api/health" > /dev/null 2>&1; then
        echo "ERROR: PSI Agent MCP server is not running on port ${PSI_AGENT_PORT}." >&2
        echo "" >&2
        echo "To start it:" >&2
        echo "  1. Run: ./gradlew runIde" >&2
        echo "  2. Open a project in the sandbox IDE" >&2
        echo "  3. The server starts automatically and writes ~/.psi-agent/token" >&2
        exit 1
    fi
}

post_json() {
    local endpoint="$1"
    local json_body="$2"
    curl -sf -X POST \
        "${AUTH_ARGS[@]}" \
        -H "Content-Type: application/json" \
        -d "$json_body" \
        "${PSI_AGENT_URL}${endpoint}"
}

print_usage() {
    cat <<'EOF'
PSI Agent — AI-friendly CLI for IntelliJ PSI operations

Usage: psi-agent.sh <command> [arguments]

Commands:
  search <query> [--type method|class|all]
      Search for code elements using the PSI index.
      Examples:
        psi-agent.sh search "getUserById"
        psi-agent.sh search "get*" --type method
        psi-agent.sh search "Service" --type class

  rename <file> <old-name> <new-name>
      Rename a method/function and update all usages.
      File can be absolute or relative to the project root.
      Examples:
        psi-agent.sh rename src/main/java/Foo.java calculate compute
        psi-agent.sh rename src/main/kotlin/Bar.kt oldFun newFun

  inline-method <file> <method-name>
      Inline a simple method/function at its usage sites using PSI.
      Examples:
        psi-agent.sh inline-method src/main/java/Foo.java calculate
        psi-agent.sh inline-method src/main/kotlin/Bar.kt greet

  find-usages <method-name> [--class ClassName]
      Find all call sites / references to a method.
      Examples:
        psi-agent.sh find-usages processOrder
        psi-agent.sh find-usages getData --class UserService

  move-class <file> <target-package>
      Move a class/object to another package using PSI.
      Examples:
        psi-agent.sh move-class src/main/java/Foo.java com.example.moved
        psi-agent.sh move-class src/main/kotlin/Bar.kt com.example.shared

  extract-method <file> <new-method-name> <start-line> <end-line>
      Extract a block of code into a new method.
      Examples:
        psi-agent.sh extract-method src/main/java/Foo.java calculateTotal 10 25
        psi-agent.sh extract-method src/main/kotlin/Bar.kt doWork 5 12

  health
      Check if the MCP server is running and show project info.

  tools
      List available MCP tools and their schemas (for agent discovery).

Options:
  -h, --help    Show this help message

Environment:
  PSI_AGENT_PORT    Server port (default: 9742)
  PSI_AGENT_TOKEN   Override the bearer token used for HTTP requests
  PSI_AGENT_TOKEN_FILE  Path to the token file (default: ~/.psi-agent/token)
EOF
}

# ── Commands ─────────────────────────────────────────────────────────────────

cmd_health() {
    check_server
    curl -sf "${AUTH_ARGS[@]}" "${PSI_AGENT_URL}/api/health" | python3 -m json.tool 2>/dev/null || \
        curl -sf "${AUTH_ARGS[@]}" "${PSI_AGENT_URL}/api/health"
}

cmd_tools() {
    check_server
    curl -sf "${AUTH_ARGS[@]}" "${PSI_AGENT_URL}/mcp/tools/list" | python3 -m json.tool 2>/dev/null || \
        curl -sf "${AUTH_ARGS[@]}" "${PSI_AGENT_URL}/mcp/tools/list"
}

cmd_search() {
    check_server
    local query="${1:-}"
    local type="all"

    shift || true
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --type) shift; type="${1:-all}"; shift ;;
            *) shift ;;
        esac
    done

    if [[ -z "$query" ]]; then
        echo "ERROR: search requires a query" >&2
        exit 1
    fi

    post_json "/api/search" "{\"query\": \"${query}\", \"type\": \"${type}\"}" | \
        python3 -m json.tool 2>/dev/null || \
        post_json "/api/search" "{\"query\": \"${query}\", \"type\": \"${type}\"}"
}

cmd_rename() {
    check_server
    local file="${1:-}"
    local old_name="${2:-}"
    local new_name="${3:-}"

    if [[ -z "$file" || -z "$old_name" || -z "$new_name" ]]; then
        echo "ERROR: rename requires <file> <old-name> <new-name>" >&2
        exit 1
    fi

    post_json "/api/rename" "{\"file\": \"${file}\", \"old_name\": \"${old_name}\", \"new_name\": \"${new_name}\"}" | \
        python3 -m json.tool 2>/dev/null || \
        post_json "/api/rename" "{\"file\": \"${file}\", \"old_name\": \"${old_name}\", \"new_name\": \"${new_name}\"}"
}

cmd_inline_method() {
    check_server
    local file="${1:-}"
    local method_name="${2:-}"

    if [[ -z "$file" || -z "$method_name" ]]; then
        echo "ERROR: inline-method requires <file> <method-name>" >&2
        exit 1
    fi

    post_json "/api/inline-method" "{\"file\": \"${file}\", \"method_name\": \"${method_name}\"}" | \
        python3 -m json.tool 2>/dev/null || \
        post_json "/api/inline-method" "{\"file\": \"${file}\", \"method_name\": \"${method_name}\"}"
}

cmd_find_usages() {
    check_server
    local method_name="${1:-}"
    local class_name=""

    shift || true
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --class) shift; class_name="${1:-}"; shift ;;
            *) shift ;;
        esac
    done

    if [[ -z "$method_name" ]]; then
        echo "ERROR: find-usages requires a method name" >&2
        exit 1
    fi

    local json="{\"method_name\": \"${method_name}\""
    if [[ -n "$class_name" ]]; then
        json="${json}, \"class_name\": \"${class_name}\""
    fi
    json="${json}}"

    post_json "/api/find-usages" "$json" | \
        python3 -m json.tool 2>/dev/null || \
        post_json "/api/find-usages" "$json"
}

cmd_move_class() {
    check_server
    local file="${1:-}"
    local target_package="${2:-}"

    if [[ -z "$file" || -z "$target_package" ]]; then
        echo "ERROR: move-class requires <file> <target-package>" >&2
        exit 1
    fi

    post_json "/api/move-class" "{\"file\": \"${file}\", \"target_package\": \"${target_package}\"}" | \
        python3 -m json.tool 2>/dev/null || \
        post_json "/api/move-class" "{\"file\": \"${file}\", \"target_package\": \"${target_package}\"}"
}

cmd_extract_method() {
    check_server
    local file="${1:-}"
    local new_method_name="${2:-}"
    local start_line="${3:-}"
    local end_line="${4:-}"

    if [[ -z "$file" || -z "$new_method_name" || -z "$start_line" || -z "$end_line" ]]; then
        echo "ERROR: extract-method requires <file> <new-method-name> <start-line> <end-line>" >&2
        exit 1
    fi

    post_json "/api/extract-method" "{\"file\": \"${file}\", \"new_method_name\": \"${new_method_name}\", \"start_line\": ${start_line}, \"end_line\": ${end_line}}" | \
        python3 -m json.tool 2>/dev/null || \
        post_json "/api/extract-method" "{\"file\": \"${file}\", \"new_method_name\": \"${new_method_name}\", \"start_line\": ${start_line}, \"end_line\": ${end_line}}"
}

# ── Main ─────────────────────────────────────────────────────────────────────

if [[ $# -eq 0 ]] || [[ "${1:-}" == "-h" ]] || [[ "${1:-}" == "--help" ]]; then
    print_usage
    exit 0
fi

COMMAND="$1"
shift

case "$COMMAND" in
    search)       cmd_search "$@" ;;
    rename)       cmd_rename "$@" ;;
    inline-method) cmd_inline_method "$@" ;;
    find-usages)  cmd_find_usages "$@" ;;
    move-class)   cmd_move_class "$@" ;;
    extract-method) cmd_extract_method "$@" ;;
    health)       cmd_health ;;
    tools)        cmd_tools ;;
    *)
        echo "ERROR: Unknown command '$COMMAND'" >&2
        print_usage
        exit 1
        ;;
esac

