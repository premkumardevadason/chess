# Common Functions Library for AWS Deployment Scripts (PowerShell)
# This library provides shared functionality across all PowerShell scripts

# Global variables
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $ScriptDir)
$ConfigDir = Join-Path $ScriptDir "..\config"

# Color codes for output
$Host.UI.RawUI.ForegroundColor = "White" # Reset to default

# Log levels
enum LogLevel {
    Debug = 0
    Info = 1
    Warn = 2
    Error = 3
}

# Default log level
$Global:LogLevel = [LogLevel]::Info

# Error tracking
$Global:ErrorOccurred = $false
$Global:RollbackStack = @()

# =============================================================================
# LOGGING FUNCTIONS
# =============================================================================

function Write-Log {
    param(
        [Parameter(Mandatory=$true)]
        [LogLevel]$Level,
        
        [Parameter(Mandatory=$true)]
        [string]$Message
    )
    
    # Determine if we should log this level
    if ($Level -ge $Global:LogLevel) {
        $timestamp = Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ" -AsUTC
        
        # Set color based on level
        switch ($Level) {
            "Debug" { $color = "Cyan" }
            "Info" { $color = "Blue" }
            "Warn" { $color = "Yellow" }
            "Error" { $color = "Red" }
            default { $color = "White" }
        }
        
        # Format the log message
        $logMessage = "[$($Level.ToString().ToUpper())] $Message"
        
        # Output with color
        Write-Host $logMessage -ForegroundColor $color
        
        # Also output structured JSON for automation
        if ($Global:LogJson -eq $true) {
            $jsonLog = @{
                timestamp = $timestamp
                level = $Level.ToString()
                message = $Message
                environment = $Global:Environment
                action = $Global:Action
            } | ConvertTo-Json -Compress
            
            Write-Host $jsonLog -ForegroundColor Gray
        }
    }
}

function Write-LogDebug { param([string]$Message) Write-Log -Level Debug -Message $Message }
function Write-LogInfo { param([string]$Message) Write-Log -Level Info -Message $Message }
function Write-LogWarn { param([string]$Message) Write-Log -Level Warn -Message $Message }
function Write-LogError { param([string]$Message) Write-Log -Level Error -Message $Message }

# =============================================================================
# ERROR HANDLING FUNCTIONS
# =============================================================================

function Handle-Error {
    param(
        [string]$ErrorMessage,
        [string]$Command = "Unknown"
    )
    
    $Global:ErrorOccurred = $true
    
    Write-LogError "Error: $ErrorMessage"
    Write-LogError "Command: $Command"
    
    # Implement rollback logic
    if ($Global:RollbackStack.Count -gt 0) {
        Write-LogWarn "Initiating rollback of $($Global:RollbackStack.Count) operations..."
        Invoke-RollbackOperations
    }
    
    # Show error context
    Write-LogError "Stack trace:"
    Get-PSCallStack | ForEach-Object { Write-LogError "  $($_.Command) at line $($_.ScriptLineNumber)" }
    
    throw $ErrorMessage
}

function Invoke-RollbackOperations {
    Write-LogInfo "Starting rollback operations..."
    
    # Execute rollback operations in reverse order
    for ($i = $Global:RollbackStack.Count - 1; $i -ge 0; $i--) {
        $rollbackFunc = $Global:RollbackStack[$i]
        Write-LogInfo "Rolling back: $rollbackFunc"
        
        try {
            if (Get-Command $rollbackFunc -ErrorAction SilentlyContinue) {
                & $rollbackFunc
                if ($LASTEXITCODE -ne 0) {
                    Write-LogError "Rollback operation '$rollbackFunc' failed"
                }
            } else {
                Write-LogWarn "Rollback function '$rollbackFunc' not found"
            }
        } catch {
            Write-LogError "Rollback operation '$rollbackFunc' failed with error: $($_.Exception.Message)"
        }
    }
    
    # Clear rollback stack
    $Global:RollbackStack = @()
    Write-LogInfo "Rollback operations completed"
}

function Add-RollbackOperation {
    param([string]$Operation)
    
    $Global:RollbackStack += $Operation
    Write-LogDebug "Added rollback operation: $Operation"
}

# =============================================================================
# VALIDATION FUNCTIONS
# =============================================================================

function Test-Environment {
    param([string]$Environment)
    
    switch ($Environment) {
        "dev" { return $true }
        "staging" { return $true }
        "prod" { return $true }
        default { 
            Write-LogError "Invalid environment: $Environment. Must be dev, staging, or prod."
            return $false
        }
    }
}

function Test-Action {
    param([string]$Action)
    
    $validActions = @("install", "start", "status", "halt", "restart", "stop", "remove", "delete", "health")
    
    if ($Action -in $validActions) {
        return $true
    } else {
        Write-LogError "Invalid action: $Action. Must be one of: $($validActions -join ', ')"
        return $false
    }
}

function Test-Resource {
    param([string]$Resource)
    
    $validResources = @("all", "vpc", "storage", "security", "compute", "network", "apps")
    
    if ($Resource -in $validResources) {
        return $true
    } else {
        Write-LogError "Invalid resource: $Resource. Must be one of: $($validResources -join ', ')"
        return $false
    }
}

# =============================================================================
# PREREQUISITE CHECKING
# =============================================================================

function Test-Prerequisites {
    Write-LogInfo "Checking prerequisites..."
    
    $missingTools = @()
    
    # Check required tools
    $requiredTools = @("aws", "terraform")
    if ($Global:Resource -eq "apps" -or $Global:Resource -eq "all") {
        $requiredTools += @("kubectl", "helm")
    }
    
    foreach ($tool in $requiredTools) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
            $missingTools += $tool
        }
    }
    
    if ($missingTools.Count -gt 0) {
        Write-LogError "Missing required tools: $($missingTools -join ', ')"
        Write-LogError "Please install the missing tools and try again."
        return $false
    }
    
    # Check AWS CLI configuration
    try {
        $null = aws sts get-caller-identity 2>$null
    } catch {
        Write-LogError "AWS CLI not configured or insufficient permissions"
        Write-LogError "Please run 'aws configure' and ensure you have appropriate permissions"
        return $false
    }
    
    Write-LogInfo "All prerequisites met"
    return $true
}

function Test-AwsPermissions {
    Write-LogInfo "Checking AWS permissions..."
    
    $requiredPermissions = @("eks:*", "ec2:*", "s3:*", "iam:*", "apigateway:*", "cloudfront:*", "wafv2:*", "secretsmanager:*")
    $missingPermissions = @()
    
    foreach ($permission in $requiredPermissions) {
        try {
            $null = aws iam get-user 2>$null
        } catch {
            $missingPermissions += $permission
        }
    }
    
    if ($missingPermissions.Count -gt 0) {
        Write-LogWarn "Some AWS permissions may be insufficient: $($missingPermissions -join ', ')"
        Write-LogWarn "This may cause operations to fail"
    } else {
        Write-LogInfo "AWS permissions verified"
    }
}

# =============================================================================
# CONFIGURATION MANAGEMENT
# =============================================================================

function Load-Configuration {
    param([string]$Environment)
    
    $configFile = Join-Path $ConfigDir "$Environment.conf"
    
    if (-not (Test-Path $configFile)) {
        Write-LogWarn "Configuration file not found: $configFile"
        Write-LogInfo "Using default configuration"
        return $true
    }
    
    Write-LogInfo "Loading configuration from: $configFile"
    
    try {
        # Load the configuration file
        . $configFile
        
        # Validate configuration
        if (Test-Configuration) {
            Write-LogInfo "Configuration loaded successfully"
            return $true
        } else {
            return $false
        }
    } catch {
        Write-LogError "Failed to load configuration file: $configFile"
        Write-LogError "Error: $($_.Exception.Message)"
        return $false
    }
}

function Test-Configuration {
    $requiredVars = @("PROJECT_NAME", "AWS_REGION")
    
    foreach ($var in $requiredVars) {
        if ([string]::IsNullOrEmpty((Get-Variable $var -ErrorAction SilentlyContinue).Value)) {
            Write-LogError "Required configuration variable not set: $var"
            return $false
        }
    }
    
    # Set defaults for optional variables
    if (-not (Get-Variable "PROJECT_NAME" -ErrorAction SilentlyContinue)) {
        $Global:PROJECT_NAME = "chess-app"
    }
    if (-not (Get-Variable "AWS_REGION" -ErrorAction SilentlyContinue)) {
        $Global:AWS_REGION = "us-west-2"
    }
    if (-not (Get-Variable "EKS_NODE_GROUPS" -ErrorAction SilentlyContinue)) {
        $Global:EKS_NODE_GROUPS = "general:2-6:3,gpu:0-3:1"
    }
    
    Write-LogDebug "Configuration validated: PROJECT_NAME=$Global:PROJECT_NAME, AWS_REGION=$Global:AWS_REGION"
    return $true
}

# =============================================================================
# AWS UTILITY FUNCTIONS
# =============================================================================

function Get-AwsRegion {
    # Try to get region from configuration or AWS CLI
    $region = $Global:AWS_REGION
    
    if ([string]::IsNullOrEmpty($region)) {
        try {
            $region = aws configure get region 2>$null
        } catch {
            $region = "us-west-2"
        }
    }
    
    return $region
}

function Get-ClusterName {
    param([string]$Environment)
    
    $projectName = if ($Global:PROJECT_NAME) { $Global:PROJECT_NAME } else { "chess-app" }
    return "$projectName-$Environment-cluster"
}

function Get-NodeGroupNames {
    param([string]$Environment)
    
    $projectName = if ($Global:PROJECT_NAME) { $Global:PROJECT_NAME } else { "chess-app" }
    return @("$projectName-$Environment-general", "$projectName-$Environment-gpu")
}

# =============================================================================
# PROGRESS TRACKING
# =============================================================================

function Show-Progress {
    param(
        [string]$Message,
        [int]$Current,
        [int]$Total
    )
    
    if ($Current -eq 0 -or $Total -eq 0) {
        Write-LogInfo $Message
        return
    }
    
    $percentage = [math]::Round(($Current * 100) / $Total)
    $barLength = 30
    $filledLength = [math]::Round(($barLength * $Current) / $Total)
    
    $bar = "█" * $filledLength + "░" * ($barLength - $filledLength)
    
    Write-Host "`r$Message [$bar] $percentage% ($Current/$Total)" -NoNewline
    
    if ($Current -eq $Total) {
        Write-Host "" # New line when complete
    }
}

# =============================================================================
# UTILITY FUNCTIONS
# =============================================================================

function Confirm-Action {
    param(
        [string]$Message,
        [string]$Default = "N"
    )
    
    if ($Global:AutoApprove -eq $true) {
        Write-LogInfo "Auto-approve enabled, proceeding with: $Message"
        return $true
    }
    
    $prompt = if ($Default -eq "Y") { "$Message (Y/n): " } else { "$Message (y/N): " }
    
    $confirmation = Read-Host $prompt
    
    switch ($confirmation) {
        "Y" { return $true }
        "y" { return $true }
        "Yes" { return $true }
        "yes" { return $true }
        "N" { return $false }
        "n" { return $false }
        "No" { return $false }
        "no" { return $false }
        "" { return $Default -eq "Y" }
        default { return $false }
    }
}

function Test-DryRun {
    return $Global:DryRun -eq $true
}

function Execute-Command {
    param(
        [string]$Command,
        [string]$Description = "Executing command"
    )
    
    if (Test-DryRun) {
        Write-LogInfo "DRY RUN: $Description"
        Write-LogInfo "Would execute: $Command"
        return $true
    }
    
    Write-LogInfo $Description
    Write-LogDebug "Executing: $Command"
    
    try {
        Invoke-Expression $Command
        if ($LASTEXITCODE -eq 0) {
            Write-LogInfo "Command completed successfully"
            return $true
        } else {
            Write-LogError "Command failed with exit code: $LASTEXITCODE"
            return $false
        }
    } catch {
        Write-LogError "Command failed: $Command"
        Write-LogError "Error: $($_.Exception.Message)"
        return $false
    }
}

# =============================================================================
# INITIALIZATION
# =============================================================================

# Set up error handling
$Global:ErrorActionPreference = "Stop"

# Export functions for use in other scripts
Export-ModuleMember -Function Write-Log, Write-LogDebug, Write-LogInfo, Write-LogWarn, Write-LogError
Export-ModuleMember -Function Handle-Error, Invoke-RollbackOperations, Add-RollbackOperation
Export-ModuleMember -Function Test-Environment, Test-Action, Test-Resource
Export-ModuleMember -Function Test-Prerequisites, Test-AwsPermissions
Export-ModuleMember -Function Load-Configuration, Test-Configuration
Export-ModuleMember -Function Get-AwsRegion, Get-ClusterName, Get-NodeGroupNames
Export-ModuleMember -Function Show-Progress, Confirm-Action, Test-DryRun, Execute-Command
