<#
.SYNOPSIS
    PSI Agent — CLI interface for the PSI Agent MCP server (Windows PowerShell)

.DESCRIPTION
    Calls the HTTP server running inside IntelliJ to perform PSI-powered
    code search and refactoring.

    Prerequisites:
      1. Run the plugin IDE: .\gradlew.bat runIde
      2. Open a project in the sandbox IDE
      3. The MCP server auto-starts on http://127.0.0.1:9742

.EXAMPLE
    .\scripts\psi-agent.ps1 search "getUserById"
    .\scripts\psi-agent.ps1 search "get*" -Type method
    .\scripts\psi-agent.ps1 rename src/main/java/Foo.java calculate compute
    .\scripts\psi-agent.ps1 find-usages processOrder
    .\scripts\psi-agent.ps1 health
    .\scripts\psi-agent.ps1 tools
#>

param(
    [Parameter(Position=0)]
    [string]$Command,

    [Parameter(Position=1, ValueFromRemainingArguments=$true)]
    [string[]]$Args
)

$Port = if ($env:PSI_AGENT_PORT) { $env:PSI_AGENT_PORT } else { "9742" }
$BaseUrl = "http://127.0.0.1:$Port"

function Test-Server {
    try {
        $null = Invoke-RestMethod -Uri "$BaseUrl/api/health" -TimeoutSec 2
        return $true
    } catch {
        Write-Error "PSI Agent MCP server is not running on port $Port.`n`nTo start it:`n  1. Run: .\gradlew.bat runIde`n  2. Open a project in the sandbox IDE`n  3. The server starts automatically"
        exit 1
    }
}

function Invoke-PsiPost {
    param([string]$Endpoint, [hashtable]$Body)
    $json = $Body | ConvertTo-Json -Depth 10
    $response = Invoke-RestMethod -Uri "$BaseUrl$Endpoint" -Method Post -ContentType "application/json" -Body $json
    $response | ConvertTo-Json -Depth 10
}

function Show-Usage {
    Write-Host @"
PSI Agent — AI-friendly CLI for IntelliJ PSI operations

Usage: psi-agent.ps1 <command> [arguments]

Commands:
  search <query> [-Type method|class|all]     Search code elements
  rename <file> <old-name> <new-name>         Rename and update usages
  find-usages <method-name> [-Class name]     Find all references
  health                                       Check server status
  tools                                        List MCP tool schemas
"@
}

if (-not $Command -or $Command -eq "-h" -or $Command -eq "--help") {
    Show-Usage
    exit 0
}

Test-Server | Out-Null

switch ($Command) {
    "health" {
        Invoke-RestMethod -Uri "$BaseUrl/api/health" | ConvertTo-Json -Depth 5
    }
    "tools" {
        Invoke-RestMethod -Uri "$BaseUrl/mcp/tools/list" | ConvertTo-Json -Depth 10
    }
    "search" {
        $query = $Args[0]
        $type = "all"
        for ($i = 1; $i -lt $Args.Count; $i++) {
            if ($Args[$i] -eq "--type" -or $Args[$i] -eq "-Type") {
                $type = $Args[$i + 1]
                $i++
            }
        }
        if (-not $query) { Write-Error "search requires a query"; exit 1 }
        Invoke-PsiPost "/api/search" @{ query = $query; type = $type }
    }
    "rename" {
        if ($Args.Count -lt 3) { Write-Error "rename requires <file> <old-name> <new-name>"; exit 1 }
        Invoke-PsiPost "/api/rename" @{ file = $Args[0]; old_name = $Args[1]; new_name = $Args[2] }
    }
    "find-usages" {
        $methodName = $Args[0]
        $className = $null
        for ($i = 1; $i -lt $Args.Count; $i++) {
            if ($Args[$i] -eq "--class" -or $Args[$i] -eq "-Class") {
                $className = $Args[$i + 1]
                $i++
            }
        }
        if (-not $methodName) { Write-Error "find-usages requires a method name"; exit 1 }
        $body = @{ method_name = $methodName }
        if ($className) { $body["class_name"] = $className }
        Invoke-PsiPost "/api/find-usages" $body
    }
    default {
        Write-Error "Unknown command: $Command"
        Show-Usage
        exit 1
    }
}

