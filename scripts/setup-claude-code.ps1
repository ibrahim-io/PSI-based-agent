<#
.SYNOPSIS
    Register the PSI Agent MCP bridge with Claude Code (Windows)

.DESCRIPTION
    Copies the MCP config to Claude's configuration directory
    so the bridge is available as a native MCP tool without manual setup.

.EXAMPLE
    .\scripts\setup-claude-code.ps1

.NOTES
    After running, restart Claude Code and the psi_search, psi_rename, and psi_find_usages
    tools will be available as native MCP tools.
#>

param()

$ClaudeCodeConfig = Join-Path $HOME ".config" "claude-code"
$PsiAgentConfigPath = Join-Path $ClaudeCodeConfig "mcp-servers.json"

# Ensure the config directory exists
if (-not (Test-Path $ClaudeCodeConfig)) {
    New-Item -ItemType Directory -Force -Path $ClaudeCodeConfig | Out-Null
}

# Get the repo root
$RepoRoot = Split-Path -Parent $PSScriptRoot
$PsiConfig = Join-Path $RepoRoot ".mcp.json"

if (-not (Test-Path $PsiConfig)) {
    Write-Error "ERROR: .mcp.json not found at ${PsiConfig}"
    exit 1
}

# Read the PSI Agent config
$PsiConfigContent = Get-Content -Raw $PsiConfig | ConvertFrom-Json

# Merge or create the Claude Code MCP servers config
if (Test-Path $PsiAgentConfigPath) {
    # Merge: add/update the psi-agent server
    try {
        $ClaudeConfig = Get-Content -Raw $PsiAgentConfigPath | ConvertFrom-Json
        $ClaudeConfig.mcpServers.PSObject.Properties.Add(
            (New-Object PSNoteProperty -Name 'psi-agent' -Value $PsiConfigContent.mcpServers.'psi-agent'),
            $true
        )
        $ClaudeConfig | ConvertTo-Json -Depth 10 | Set-Content $PsiAgentConfigPath
    }
    catch {
        Write-Warning "Could not merge configs: $_"
    }
} else {
    # Create: copy the full config
    $PsiConfigContent | ConvertTo-Json -Depth 10 | Set-Content $PsiAgentConfigPath
}

Write-Host "✓ PSI Agent MCP bridge registered in Claude Code at ${PsiAgentConfigPath}"
Write-Host ""
Write-Host "Next steps:"
Write-Host "  1. Restart Claude Code"
Write-Host "  2. Run: .\gradlew.bat runHeadless (or .\gradlew.bat runIde)"
Write-Host "  3. Open a Java/Kotlin project in the IDE"
Write-Host "  4. The psi_search, psi_rename, and psi_find_usages tools will be available in Claude Code"

