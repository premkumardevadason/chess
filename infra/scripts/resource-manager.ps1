# PowerShell Granular AWS Resource Management Script - REFACTORED VERSION
# This script now uses shared libraries for better maintainability and error handling

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet("dev", "staging", "prod")]
    [string]$Environment,
    
    [Parameter(Mandatory=$false)]
    [ValidateSet("install", "start", "status", "halt", "restart", "stop", "remove", "health")]
    [string]$Action,
    
    [Parameter(Mandatory=$false)]
    [ValidateSet("all", "vpc", "storage", "security", "compute", "network", "apps")]
    [string]$Resource = "all",
    
    [Parameter(Mandatory=$false)]
    [switch]$DryRun,
    
    [Parameter(Mandatory=$false)]
    [switch]$AutoApprove,
    
    [Parameter(Mandatory=$false)]
    [ValidateSet("DEBUG", "INFO", "WARN", "ERROR")]
    [string]$LogLevel = "INFO",
    
    [Parameter(Mandatory=$false)]
    [switch]$LogJson,
    
    [Parameter(Mandatory=$false)]
    [switch]$Help
)

# Source shared libraries
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. "$ScriptDir\lib\common-functions.ps1"

# Global variables
$Global:Environment = $Environment
$Global:Action = $Action
$Global:Resource = $Resource
$Global:DryRun = $DryRun
$Global:AutoApprove = $AutoApprove
$Global:LogLevel = [LogLevel]::$LogLevel
$Global:LogJson = $LogJson

# Resource definitions
$ResourceMap = @{
    "vpc" = @("internet-vpc", "private-vpc", "transit-gateway")
    "storage" = @("s3", "ecr")
    "security" = @("secrets-manager", "waf")
    "compute" = @("eks")
    "network" = @("api-gateway", "cloudfront")
    "apps" = @("helm-istio", "helm-monitoring", "helm-chess-app")
}

function Show-Usage {
    Write-Host @"
Usage: .\resource-manager.ps1 -Environment <env> -Action <action> [-Resource <resource>] [options]

Environment: dev, staging, prod
Actions: install, start, status, halt, restart, stop, remove, health
Resources:
  all          - All resources (default)
  vpc          - VPCs and networking foundation
  storage      - S3 buckets and ECR
  security     - Secrets Manager and WAF
  compute      - EKS cluster and nodes
  network      - API Gateway and CloudFront
  apps         - Istio, monitoring, and chess app

Options:
  -DryRun      - Show what would be executed without making changes
  -AutoApprove - Skip confirmation prompts
  -LogLevel    - Set log level (DEBUG, INFO, WARN, ERROR)
  -LogJson     - Enable structured JSON logging
  -Help        - Show this help message

Examples:
  .\resource-manager.ps1 -Environment dev -Action install -Resource vpc
  .\resource-manager.ps1 -Environment dev -Action status -Resource compute
  .\resource-manager.ps1 -Environment dev -Action halt -Resource apps
  .\resource-manager.ps1 -Environment dev -Action install -Resource all -DryRun
  .\resource-manager.ps1 -Environment prod -Action status -Resource all -LogLevel DEBUG

Environment Configuration:
  Configuration files are loaded from: $ScriptDir\config\
  - dev.conf      - Development environment settings
  - staging.conf  - Staging environment settings  
  - prod.conf     - Production environment settings

Error Handling:
  - Automatic rollback on failures
  - Comprehensive error logging
  - Progress tracking for long operations
  - Timeout protection for hanging operations

"@
}

function Test-Inputs {
    # Validate environment
    if ([string]::IsNullOrEmpty($Environment)) {
        Write-LogError "Environment is required"
        Show-Usage
        exit 1
    }
    
    if (-not (Test-Environment $Environment)) {
        exit 1
    }
    
    # Validate action
    if ([string]::IsNullOrEmpty($Action)) {
        Write-LogError "Action is required"
        Show-Usage
        exit 1
    }
    
    if (-not (Test-Action $Action)) {
        exit 1
    }
    
    # Validate resource
    if (-not (Test-Resource $Resource)) {
        exit 1
    }
}

# =============================================================================
# RESOURCE INSTALLATION FUNCTIONS
# =============================================================================

function Install-Resource {
    param([string]$ResourceType)
    
    Write-LogInfo "Installing $ResourceType for environment: $Environment"
    
    # Add rollback operation
    switch ($ResourceType) {
        "vpc" { Add-RollbackOperation "Rollback-VpcInstallation $Environment" }
        "storage" { Add-RollbackOperation "Rollback-StorageInstallation $Environment" }
        "security" { Add-RollbackOperation "Rollback-SecurityInstallation $Environment" }
        "compute" { Add-RollbackOperation "Rollback-ComputeInstallation $Environment" }
        "network" { Add-RollbackOperation "Rollback-NetworkInstallation $Environment" }
        "apps" { Add-RollbackOperation "Rollback-AppsInstallation $Environment" }
    }
    
    # Run Terraform operation
    if (-not (Invoke-TerraformOperation "apply" $Environment $ResourceType $true)) {
        Write-LogError "Failed to install $ResourceType"
        return $false
    }
    
    # Post-install actions
    if ($ResourceType -eq "compute" -or $ResourceType -eq "all") {
        if (-not (Update-Kubeconfig $Environment)) {
            Write-LogError "Failed to update kubeconfig"
            return $false
        }
    }
    
    Write-LogInfo "$ResourceType installation completed successfully"
    return $true
}

function Install-AllResources {
    Write-LogInfo "Installing all resources for environment: $Environment"
    
    $resources = @("vpc", "storage", "security", "compute", "network", "apps")
    $total = $resources.Count
    $current = 0
    
    # Install resources sequentially (dependencies matter)
    foreach ($resource in $resources) {
        $current++
        Show-Progress "Installing resources" $current $total
        
        Write-LogInfo "Installing $resource ($current/$total)..."
        
        if (-not (Install-Resource $resource)) {
            Write-LogError "Failed to install $resource, stopping installation"
            return $false
        }
        
        # Wait between installations to ensure stability
        if ($current -lt $total) {
            Write-LogInfo "Waiting for $resource to stabilize..."
            Start-Sleep -Seconds 30
        }
    }
    
    Write-LogInfo "All resources installed successfully"
    return $true
}

# =============================================================================
# RESOURCE START/STOP FUNCTIONS
# =============================================================================

function Start-Resource {
    param([string]$ResourceType)
    
    Write-LogInfo "Starting $ResourceType for environment: $Environment"
    
    switch ($ResourceType) {
        "compute" {
            if (-not (Scale-NodeGroups "up" $Environment)) {
                Write-LogError "Failed to scale up node groups"
                return $false
            }
            
            # Wait for cluster to be ready
            if (-not (Wait-ForClusterReady $Environment)) {
                Write-LogError "Failed to wait for cluster readiness"
                return $false
            }
        }
        "apps" {
            try {
                $null = kubectl cluster-info 2>$null
                
                if (-not (Scale-Deployments "chess-app" 3)) {
                    Write-LogError "Failed to scale chess-app deployments"
                    return $false
                }
                
                if (-not (Scale-Deployments "monitoring" 1)) {
                    Write-LogError "Failed to scale monitoring deployments"
                    return $false
                }
                
                # Wait for deployments to be ready
                if (-not (Wait-ForDeploymentReady "chess-app" "chess-app")) {
                    Write-LogError "Failed to wait for chess-app deployment"
                    return $false
                }
            } catch {
                Write-LogError "Kubernetes cluster not accessible"
                return $false
            }
        }
        "all" {
            if (-not (Start-Resource "compute")) {
                return $false
            }
            if (-not (Start-Resource "apps")) {
                return $false
            }
        }
    }
    
    Write-LogInfo "$ResourceType started successfully"
    return $true
}

function Halt-Resource {
    param([string]$ResourceType)
    
    Write-LogInfo "Halting $ResourceType for environment: $Environment"
    
    switch ($ResourceType) {
        "compute" {
            if (-not (Scale-NodeGroups "down" $Environment)) {
                Write-LogError "Failed to scale down node groups"
                return $false
            }
        }
        "apps" {
            try {
                $null = kubectl cluster-info 2>$null
                
                if (-not (Scale-Deployments "chess-app" 0)) {
                    Write-LogError "Failed to scale down chess-app deployments"
                    return $false
                }
                
                if (-not (Scale-Deployments "monitoring" 0)) {
                    Write-LogError "Failed to scale down monitoring deployments"
                    return $false
                }
            } catch {
                Write-LogError "Kubernetes cluster not accessible"
                return $false
            }
        }
        "all" {
            if (-not (Halt-Resource "apps")) {
                return $false
            }
            if (-not (Halt-Resource "compute")) {
                return $false
            }
        }
    }
    
    Write-LogInfo "$ResourceType halted successfully"
    return $true
}

function Restart-Resource {
    param([string]$ResourceType)
    
    Write-LogInfo "Restarting $ResourceType for environment: $Environment"
    
    switch ($ResourceType) {
        "compute" {
            if (-not (Scale-NodeGroups "up" $Environment)) {
                Write-LogError "Failed to scale up node groups"
                return $false
            }
            
            if (-not (Wait-ForClusterReady $Environment)) {
                Write-LogError "Failed to wait for cluster readiness"
                return $false
            }
            
            if (-not (Wait-ForNodesReady)) {
                Write-LogError "Failed to wait for nodes readiness"
                return $false
            }
        }
        "apps" {
            try {
                $null = kubectl cluster-info 2>$null
                
                if (-not (Scale-Deployments "chess-app" 3)) {
                    Write-LogError "Failed to scale up chess-app deployments"
                    return $false
                }
                
                if (-not (Wait-ForDeploymentReady "chess-app" "chess-app")) {
                    Write-LogError "Failed to wait for chess-app deployment"
                    return $false
                }
            } catch {
                Write-LogError "Kubernetes cluster not accessible"
                return $false
            }
        }
        "all" {
            if (-not (Restart-Resource "compute")) {
                return $false
            }
            if (-not (Restart-Resource "apps")) {
                return $false
            }
        }
    }
    
    Write-LogInfo "$ResourceType restarted successfully"
    return $true
}

# =============================================================================
# RESOURCE STATUS FUNCTIONS
# =============================================================================

function Get-ResourceStatus {
    param([string]$ResourceType)
    
    Write-LogInfo "Checking status of $ResourceType for environment: $Environment"
    
    switch ($ResourceType) {
        "vpc" { Get-VpcStatus $Environment }
        "storage" { Get-StorageStatus $Environment }
        "security" { Get-SecurityStatus $Environment }
        "compute" { Get-ComputeStatus $Environment }
        "network" { Get-NetworkStatus $Environment }
        "apps" { Get-AppsStatus $Environment }
        "all" {
            Get-VpcStatus $Environment
            Get-StorageStatus $Environment
            Get-SecurityStatus $Environment
            Get-ComputeStatus $Environment
            Get-NetworkStatus $Environment
            Get-AppsStatus $Environment
        }
    }
}

# =============================================================================
# RESOURCE REMOVAL FUNCTIONS
# =============================================================================

function Remove-Resource {
    param([string]$ResourceType)
    
    Write-LogInfo "Removing $ResourceType for environment: $Environment"
    
    # Confirm destructive operation
    $message = "This will DELETE $ResourceType resources for environment $Environment. Are you sure?"
    if (-not (Confirm-Action $message "N")) {
        Write-LogInfo "Operation cancelled by user"
        return $true
    }
    
    # Run Terraform destroy
    if (-not (Invoke-TerraformOperation "destroy" $Environment $ResourceType $true)) {
        Write-LogError "Failed to remove $ResourceType"
        return $false
    }
    
    Write-LogInfo "$ResourceType removed successfully"
    return $true
}

# =============================================================================
# HEALTH CHECK FUNCTIONS
# =============================================================================

function Test-ResourceHealth {
    param([string]$ResourceType)
    
    Write-LogInfo "Health check for $ResourceType in environment: $Environment"
    
    switch ($ResourceType) {
        "compute" {
            $clusterName = Get-ClusterName $Environment
            try {
                $clusterStatus = aws eks describe-cluster --name $clusterName --query 'cluster.status' --output text 2>$null
                Write-Host "EKS Cluster status: $clusterStatus"
                
                if ($clusterStatus -eq "ACTIVE") {
                    Write-LogInfo "EKS cluster is healthy"
                } else {
                    Write-LogError "EKS cluster is not healthy: $clusterStatus"
                    return $false
                }
            } catch {
                Write-LogError "Failed to get cluster status"
                return $false
            }
        }
        "apps" {
            try {
                $null = kubectl cluster-info 2>$null
                
                $appReady = kubectl get deployment chess-app -n chess-app -o jsonpath='{.status.readyReplicas}' 2>$null
                $appDesired = kubectl get deployment chess-app -n chess-app -o jsonpath='{.spec.replicas}' 2>$null
                
                if ([string]::IsNullOrEmpty($appReady)) { $appReady = "0" }
                if ([string]::IsNullOrEmpty($appDesired)) { $appDesired = "0" }
                
                Write-Host "Application health: $appReady/$appDesired replicas ready"
                
                if ($appReady -eq $appDesired -and $appReady -ne "0") {
                    Write-LogInfo "Applications are healthy"
                } else {
                    Write-LogError "Applications are not healthy"
                    return $false
                }
            } catch {
                Write-LogError "Kubernetes cluster not accessible"
                return $false
            }
        }
        "all" {
            if (-not (Test-ResourceHealth "compute")) {
                return $false
            }
            if (-not (Test-ResourceHealth "apps")) {
                return $false
            }
        }
    }
    
    return $true
}

# =============================================================================
# TERRAFORM UTILITIES
# =============================================================================

function Get-TerraformTargets {
    param([string]$ResourceType)
    
    $targets = switch ($ResourceType) {
        "vpc" { "-target=module.internet_vpc -target=module.private_vpc -target=module.transit_gateway" }
        "storage" { "-target=module.s3 -target=module.ecr" }
        "security" { "-target=module.secrets_manager -target=module.waf" }
        "compute" { "-target=module.eks" }
        "network" { "-target=module.api_gateway -target=module.cloudfront" }
        "apps" { "-target=module.helm_istio -target=module.helm_monitoring -target=module.helm_chess_app" }
        "all" { "" }
    }
    return $targets
}

function Invoke-TerraformOperation {
    param(
        [string]$Operation,
        [string]$Environment,
        [string]$ResourceType,
        [bool]$AutoApprove
    )
    
    $terraformDir = Join-Path (Split-Path -Parent (Split-Path -Parent $ScriptDir)) "infra\terraform"
    $varFile = "environments\$Environment\terraform.tfvars"
    $targets = Get-TerraformTargets $ResourceType
    
    Set-Location $terraformDir
    
    # Initialize Terraform if needed
    if (-not (Test-Path ".terraform")) {
        Write-LogInfo "Initializing Terraform..."
        if (-not (terraform init)) {
            Write-LogError "Terraform initialization failed"
            return $false
        }
    }
    
    # Run Terraform operation
    switch ($Operation) {
        "plan" {
            Write-LogInfo "Running Terraform plan for $ResourceType..."
            if ($targets) {
                terraform plan -var-file="$varFile" $targets.Split(' ')
            } else {
                terraform plan -var-file="$varFile"
            }
        }
        "apply" {
            Write-LogInfo "Running Terraform apply for $ResourceType..."
            $approveFlag = if ($AutoApprove) { "-auto-approve" } else { "" }
            
            if ($targets) {
                terraform apply -var-file="$varFile" $targets.Split(' ') $approveFlag
            } else {
                terraform apply -var-file="$varFile" $approveFlag
            }
        }
        "destroy" {
            Write-LogInfo "Running Terraform destroy for $ResourceType..."
            $approveFlag = if ($AutoApprove) { "-auto-approve" } else { "" }
            
            if ($targets) {
                terraform destroy -var-file="$varFile" $targets.Split(' ') $approveFlag
            } else {
                terraform destroy -var-file="$varFile" $approveFlag
            }
        }
        default {
            Write-LogError "Unknown Terraform operation: $Operation"
            return $false
        }
    }
    
    return $LASTEXITCODE -eq 0
}

# =============================================================================
# EKS UTILITIES
# =============================================================================

function Scale-NodeGroups {
    param(
        [string]$Direction,
        [string]$Environment
    )
    
    Write-LogInfo "Scaling node groups $Direction for environment: $Environment"
    
    $clusterName = Get-ClusterName $Environment
    $region = Get-AwsRegion
    
    if ($Direction -eq "up") {
        # Scale up node groups
        $nodeGroups = Get-NodeGroupNames $Environment
        
        foreach ($nodeGroup in $nodeGroups) {
            Write-LogInfo "Scaling up node group: $nodeGroup"
            
            # Parse node group configuration
            $minSize = 2
            $maxSize = 6
            $desiredSize = 3
            
            # Check if it's a GPU node group
            if ($nodeGroup -like "*gpu*") {
                $minSize = 0
                $maxSize = 3
                $desiredSize = 1
            }
            
            try {
                aws eks update-nodegroup-config `
                    --cluster-name $clusterName `
                    --nodegroup-name $nodeGroup `
                    --scaling-config "minSize=$minSize,maxSize=$maxSize,desiredSize=$desiredSize" `
                    --region $region 2>$null
            } catch {
                Write-LogWarn "Failed to scale up node group: $nodeGroup"
            }
        }
    } else {
        # Scale down node groups
        try {
            $nodeGroups = aws eks list-nodegroups --cluster-name $clusterName --region $region --query 'nodegroups[]' --output text 2>$null
            
            if ($nodeGroups) {
                foreach ($nodeGroup in $nodeGroups.Split()) {
                    Write-LogInfo "Scaling down node group: $nodeGroup"
                    
                    try {
                        aws eks update-nodegroup-config `
                            --cluster-name $clusterName `
                            --nodegroup-name $nodeGroup `
                            --scaling-config "minSize=0,maxSize=0,desiredSize=0" `
                            --region $region 2>$null
                    } catch {
                        Write-LogWarn "Failed to scale down node group: $nodeGroup"
                    }
                }
            }
        } catch {
            Write-LogWarn "No node groups found"
        }
    }
    
    Write-LogInfo "Node group scaling completed"
    return $true
}

function Wait-ForClusterReady {
    param(
        [string]$Environment,
        [int]$Timeout = 600
    )
    
    Write-LogInfo "Waiting for EKS cluster to be ready: $(Get-ClusterName $Environment)"
    
    $startTime = Get-Date
    $endTime = $startTime.AddSeconds($Timeout)
    
    while ((Get-Date) -lt $endTime) {
        try {
            $clusterName = Get-ClusterName $Environment
            $region = Get-AwsRegion
            $status = aws eks describe-cluster --name $clusterName --region $region --query 'cluster.status' --output text 2>$null
            
            if ($status -eq "ACTIVE") {
                Write-LogInfo "EKS cluster is active"
                return $true
            } elseif ($status -eq "NOT_FOUND") {
                Write-LogError "EKS cluster not found: $clusterName"
                return $false
            }
            
            Write-LogInfo "Waiting for cluster... Status: $status"
            Start-Sleep -Seconds 30
        } catch {
            Write-LogWarn "Failed to get cluster status"
        }
    }
    
    Write-LogError "Timeout waiting for EKS cluster to be ready"
    return $false
}

function Update-Kubeconfig {
    param([string]$Environment)
    
    Write-LogInfo "Updating kubeconfig for cluster: $(Get-ClusterName $Environment)"
    
    try {
        $clusterName = Get-ClusterName $Environment
        $region = Get-AwsRegion
        
        aws eks update-kubeconfig --region $region --name $clusterName
        
        # Verify kubectl access
        $null = kubectl cluster-info 2>$null
        if ($LASTEXITCODE -ne 0) {
            Write-LogError "Failed to verify kubectl access to cluster"
            return $false
        }
        
        Write-LogInfo "Kubeconfig updated successfully"
        return $true
    } catch {
        Write-LogError "Failed to update kubeconfig"
        return $false
    }
}

# =============================================================================
# KUBERNETES UTILITIES
# =============================================================================

function Scale-Deployments {
    param(
        [string]$Namespace,
        [int]$Replicas,
        [string]$DeploymentName = "all"
    )
    
    Write-LogInfo "Scaling deployments in namespace $Namespace to $Replicas replicas"
    
    try {
        if ($DeploymentName -eq "all") {
            # Scale all deployments
            kubectl scale deployment --all --replicas=$Replicas -n $Namespace 2>$null
        } else {
            # Scale specific deployment
            kubectl scale deployment $DeploymentName --replicas=$Replicas -n $Namespace 2>$null
        }
        
        if ($LASTEXITCODE -ne 0) {
            Write-LogWarn "Failed to scale deployments in namespace: $Namespace"
            return $false
        }
        
        Write-LogInfo "Deployment scaling completed"
        return $true
    } catch {
        Write-LogWarn "Failed to scale deployments in namespace: $Namespace"
        return $false
    }
}

function Wait-ForDeploymentReady {
    param(
        [string]$Namespace,
        [string]$DeploymentName,
        [int]$Timeout = 300
    )
    
    Write-LogInfo "Waiting for deployment $DeploymentName to be ready in namespace $Namespace"
    
    try {
        kubectl wait --for=condition=available --timeout="${Timeout}s" "deployment/$DeploymentName" -n $Namespace 2>$null
        
        if ($LASTEXITCODE -eq 0) {
            Write-LogInfo "Deployment $DeploymentName is ready"
            return $true
        } else {
            Write-LogError "Timeout waiting for deployment $DeploymentName to be ready"
            return $false
        }
    } catch {
        Write-LogError "Failed to wait for deployment $DeploymentName"
        return $false
    }
}

function Wait-ForNodesReady {
    param([int]$Timeout = 300)
    
    Write-LogInfo "Waiting for all nodes to be ready"
    
    try {
        kubectl wait --for=condition=Ready nodes --all --timeout="${Timeout}s" 2>$null
        
        if ($LASTEXITCODE -eq 0) {
            Write-LogInfo "All nodes are ready"
            return $true
        } else {
            Write-LogError "Timeout waiting for nodes to be ready"
            return $false
        }
    } catch {
        Write-LogError "Failed to wait for nodes"
        return $false
    }
}

# =============================================================================
# AWS RESOURCE STATUS CHECKING
# =============================================================================

function Get-VpcStatus {
    param([string]$Environment)
    
    Write-LogInfo "Checking VPC status for environment: $Environment"
    
    Write-Host "=== VPC Status ==="
    try {
        $projectName = if ($Global:PROJECT_NAME) { $Global:PROJECT_NAME } else { "chess-app" }
        $region = Get-AwsRegion
        
        aws ec2 describe-vpcs `
            --filters "Name=tag:Name,Values=*$projectName-$Environment*" `
            --query 'Vpcs[].{Name:Tags[?Key==`Name`].Value|[0],State:State,VpcId:VpcId}' `
            --output table `
            --region $region 2>$null
    } catch {
        Write-Host "No VPCs found"
    }
}

function Get-StorageStatus {
    param([string]$Environment)
    
    Write-LogInfo "Checking storage status for environment: $Environment"
    
    Write-Host "=== Storage Status ==="
    
    try {
        $projectName = if ($Global:PROJECT_NAME) { $Global:PROJECT_NAME } else { "chess-app" }
        $region = Get-AwsRegion
        
        # Check S3 buckets
        $buckets = aws s3 ls --region $region 2>$null
        if ($buckets -match "$projectName.*$Environment") {
            Write-Host $buckets
        } else {
            Write-Host "No S3 buckets found"
        }
        
        # Check ECR repositories
        $repos = aws ecr describe-repositories `
            --repository-names "$projectName-$Environment" `
            --query 'repositories[].repositoryName' `
            --output text `
            --region $region 2>$null
        
        if ($repos) {
            Write-Host "ECR Repository: $repos"
        } else {
            Write-Host "No ECR repositories found"
        }
    } catch {
        Write-Host "Failed to check storage status"
    }
}

function Get-SecurityStatus {
    param([string]$Environment)
    
    Write-LogInfo "Checking security status for environment: $Environment"
    
    Write-Host "=== Security Status ==="
    
    try {
        $projectName = if ($Global:PROJECT_NAME) { $Global:PROJECT_NAME } else { "chess-app" }
        $region = Get-AwsRegion
        
        # Check Secrets Manager
        $secrets = aws secretsmanager list-secrets `
            --filters Key=name,Values="$projectName-$Environment-secrets" `
            --query 'SecretList[].Name' `
            --output text `
            --region $region 2>$null
        
        if ($secrets) {
            Write-Host "Secrets: $secrets"
        } else {
            Write-Host "No secrets found"
        }
        
        # Check WAF
        $wafs = aws wafv2 list-web-acls `
            --scope=CLOUDFRONT `
            --query "WebACLs[?Name=='$projectName-$Environment-waf'].Name" `
            --output text `
            --region $region 2>$null
        
        if ($wafs) {
            Write-Host "WAF: $wafs"
        } else {
            Write-Host "No WAF found"
        }
    } catch {
        Write-Host "Failed to check security status"
    }
}

function Get-ComputeStatus {
    param([string]$Environment)
    
    Write-LogInfo "Checking compute status for environment: $Environment"
    
    Write-Host "=== Compute Status ==="
    
    try {
        $clusterName = Get-ClusterName $Environment
        $region = Get-AwsRegion
        
        # Check EKS cluster
        $clusterStatus = aws eks describe-cluster `
            --name $clusterName `
            --query 'cluster.status' `
            --output text `
            --region $region 2>$null
        
        if ($clusterStatus) {
            Write-Host "EKS Cluster: $clusterStatus"
        } else {
            Write-Host "EKS cluster not found"
        }
        
        # Check node groups
        $nodeGroups = aws eks list-nodegroups `
            --cluster-name $clusterName `
            --query 'nodegroups[]' `
            --output table `
            --region $region 2>$null
        
        if ($nodeGroups) {
            Write-Host "Node Groups:"
            Write-Host $nodeGroups
        } else {
            Write-Host "No node groups found"
        }
    } catch {
        Write-Host "Failed to check compute status"
    }
}

function Get-NetworkStatus {
    param([string]$Environment)
    
    Write-LogInfo "Checking network status for environment: $Environment"
    
    Write-Host "=== Network Status ==="
    
    try {
        $projectName = if ($Global:PROJECT_NAME) { $Global:PROJECT_NAME } else { "chess-app" }
        $region = Get-AwsRegion
        
        # Check API Gateway
        $apis = aws apigatewayv2 get-apis `
            --query "Items[?Name=='$projectName-$Environment-api'].{Name:Name,ApiEndpoint:ApiEndpoint}" `
            --output table `
            --region $region 2>$null
        
        if ($apis) {
            Write-Host "API Gateway:"
            Write-Host $apis
        } else {
            Write-Host "No API Gateway found"
        }
        
        # Check CloudFront
        $distributions = aws cloudfront list-distributions `
            --query "DistributionList.Items[?Comment=='$projectName $Environment distribution'].{Id:Id,Status:Status}" `
            --output table `
            --region $region 2>$null
        
        if ($distributions) {
            Write-Host "CloudFront:"
            Write-Host $distributions
        } else {
            Write-Host "No CloudFront found"
        }
    } catch {
        Write-Host "Failed to check network status"
    }
}

function Get-AppsStatus {
    param([string]$Environment)
    
    Write-LogInfo "Checking applications status for environment: $Environment"
    
    Write-Host "=== Applications Status ==="
    
    try {
        $null = kubectl cluster-info 2>$null
        
        Write-Host "Kubernetes Pods:"
        $pods = kubectl get pods -A --field-selector=status.phase!=Succeeded 2>$null
        if ($pods) {
            Write-Host $pods
        } else {
            Write-Host "No pods found"
        }
        
        Write-Host "`nHelm Releases:"
        $releases = helm list -A 2>$null
        if ($releases) {
            Write-Host $releases
        } else {
            Write-Host "No Helm releases found"
        }
    } catch {
        Write-Host "Kubernetes cluster not accessible"
    }
}

# =============================================================================
# MAIN FUNCTION
# =============================================================================

function Main {
    # Show help if requested
    if ($Help) {
        Show-Usage
        exit 0
    }
    
    # Validate inputs
    Test-Inputs
    
    # Load configuration
    if (-not (Load-Configuration $Environment)) {
        Write-LogError "Failed to load configuration for environment: $Environment"
        exit 1
    }
    
    # Check prerequisites
    if (-not (Test-Prerequisites)) {
        exit 1
    }
    
    # Check AWS permissions
    Test-AwsPermissions
    
    Write-LogInfo "Starting resource management operation"
    Write-LogInfo "Environment: $Environment"
    Write-LogInfo "Action: $Action"
    Write-LogInfo "Resource: $Resource"
    Write-LogInfo "Dry run: $DryRun"
    Write-LogInfo "Auto approve: $AutoApprove"
    
    # Execute requested action
    $success = $false
    switch ($Action) {
        "install" {
            if ($Resource -eq "all") {
                $success = Install-AllResources
            } else {
                $success = Install-Resource $Resource
            }
        }
        "start" { $success = Start-Resource $Resource }
        "status" { $success = Get-ResourceStatus $Resource }
        "halt" { $success = Halt-Resource $Resource }
        "restart" { $success = Restart-Resource $Resource }
        "stop" { $success = Halt-Resource $Resource }
        "remove" { $success = Remove-Resource $Resource }
        "delete" { $success = Remove-Resource $Resource }
        "health" { $success = Test-ResourceHealth $Resource }
        default {
            Write-LogError "Unknown action: $Action"
            Show-Usage
            exit 1
        }
    }
    
    if ($success) {
        Write-LogInfo "Operation completed successfully"
        exit 0
    } else {
        Write-LogError "Operation failed"
        exit 1
    }
}

# Execute main function
Main