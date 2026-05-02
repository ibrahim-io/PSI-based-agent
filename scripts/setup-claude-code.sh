#!/usr/bin/env bash
# Register the PSI Agent MCP bridge with Claude Code
#
# This script copies the MCP config to Claude's configuration directory
# so the bridge is available as a native MCP tool without manual setup.
#
# Usage:
#   ./scripts/setup-claude-code.sh
#
# After running, restart Claude Code and the psi_search, psi_rename, and psi_find_usages
# tools will be available as native MCP tools.

set -euo pipefail

CLAUDE_CODE_CONFIG="${HOME}/.config/claude-code"
PSI_AGENT_CONFIG="${HOME}/.config/claude-code/mcp-servers.json"

mkdir -p "${CLAUDE_CODE_CONFIG}"

# Read the current PSI Agent config
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PSI_CONFIG="${REPO_ROOT}/.mcp.json"

if [[ ! -f "${PSI_CONFIG}" ]]; then
    echo "ERROR: .mcp.json not found at ${PSI_CONFIG}" >&2
    exit 1
fi

# Merge or create the Claude Code MCP servers config
if [[ -f "${PSI_AGENT_CONFIG}" ]]; then
    # Merge: use jq to add/update the psi-agent server
    if command -v jq &> /dev/null; then
        jq '.mcpServers.["psi-agent"] = '"$(cat "${PSI_CONFIG}" | jq '.mcpServers.["psi-agent"]')" \
            "${PSI_AGENT_CONFIG}" > "${PSI_AGENT_CONFIG}.tmp" && \
            mv "${PSI_AGENT_CONFIG}.tmp" "${PSI_AGENT_CONFIG}"
    else
        echo "WARNING: jq not found; skipping merge. Please manually add the psi-agent config from .mcp.json to ${PSI_AGENT_CONFIG}" >&2
    fi
else
    # Create: copy the full config
    cp "${PSI_CONFIG}" "${PSI_AGENT_CONFIG}"
fi

echo "✓ PSI Agent MCP bridge registered in Claude Code at ${PSI_AGENT_CONFIG}"
echo ""
echo "Next steps:"
echo "  1. Restart Claude Code"
echo "  2. Run: ./gradlew runHeadless (or ./gradlew runIde)"
echo "  3. Open a Java/Kotlin project in the IDE"
echo "  4. The psi_search, psi_rename, and psi_find_usages tools will be available in Claude Code"

