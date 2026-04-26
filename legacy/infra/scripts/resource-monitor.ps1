# PowerShell AWS Resource Monitoring Script - REFACTORED VERSION
# This script provides comprehensive monitoring and alerting for AWS resources

param(
    [Parameter(Mandatory=$false)]
    [ValidateSet("dev", "staging", "prod")]
    [string]$Environment,
    
    [Parameter(Mandatory=$false)]
    [ValidateSet("status", "health", "metrics", "alerts", "cost")]
    [string]$Action = "status",
    
    [Parameter(Mandatory=$false)]
    [ValidateSet("all", "vpc", "storage", "security", "compute", "network", "apps")]
    [string]$Resource = "all",
    
    [Parameter(Mandatory=$false)]
    [int]$Interval = 300,  # 5 minutes default
    
    [Parameter(Mandatory=$false)]
    [switch]$Continuous,
    
    [Parameter(Mandatory=$false)]
    [switch]$Detailed,
    
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
$Global:Detailed = $Detailed
$Global:LogLevel = [LogLevel]::$LogLevel
$Global:LogJson = $LogJson

# Monitoring thresholds
$Thresholds = @{
    CPU_HIGH = 80
    MEMORY_HIGH = 85
    DISK_HIGH = 90
    ERROR_RATE_HIGH = 5
    RESPONSE_TIME_HIGH = 2000
    COST_DAILY_HIGH = 100
}

function Show-Usage {
    Write-Host @"
Usage: .\resource-monitor.ps1 -Environment <env> [-Action <action>] [-Resource <resource>] [options]

Environment: dev, staging, prod
Actions:
  status    - Current resource status (default)
  health    - Health check with detailed diagnostics
  metrics   - Performance metrics and trends
  alerts    - Active alerts and notifications
  cost      - Cost analysis and optimization

Resources:
  all       - All resources (default)
  vpc       - VPCs and networking
  storage   - S3 buckets and ECR
  security  - Secrets Manager and WAF
  compute   - EKS cluster and nodes
  network   - API Gateway and CloudFront
  apps      - Applications and services

Options:
  -Interval    - Monitoring interval in seconds (default: 300)
  -Continuous  - Continuous monitoring mode
  -Detailed    - Detailed output with metrics
  -LogLevel    - Set log level (DEBUG, INFO, WARN, ERROR)
  -LogJson     - Enable structured JSON logging
  -Help        - Show this help message

Examples:
  .\resource-monitor.ps1 -Environment dev -Action status
  .\resource-monitor.ps1 -Environment prod -Action health -Resource compute
  .\resource-monitor.ps1 -Environment staging -Action metrics -Continuous -Interval 60
  .\resource-monitor.ps1 -Environment prod -Action cost -Detailed

Monitoring Features:
  - Real-time resource status
  - Performance metrics collection
  - Health diagnostics
  - Cost tracking and optimization
  - Alert generation
  - Continuous monitoring
  - Detailed reporting

"@
}

function Test-Inputs {
    # Validate environment
    if ([string]::IsNullOrEmpty($Environment)) {
        Write-LogError "Environment is required"
        show_usage
        exit 1
    }
    
    if (-not (Test-Environment $Environment)) {
        exit 1
    }
    
    # Validate action
    if (-not ($Action -in @("status", "health", "metrics", "alerts", "cost"))) {
        Write-LogError "Invalid action: $Action"
        show_usage
        exit 1
    }
    
    # Validate resource
    if (-not (Test-Resource $Resource)) {
        exit 1
    }
}

# =============================================================================
# MONITORING FUNCTIONS
# =============================================================================

function Get-ResourceStatus {
    param([string]$ResourceType)
    
    Write-LogInfo "Getting status for $ResourceType in environment: $Environment"
    
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

function Test-ResourceHealth {
    param([string]$ResourceType)
    
    Write-LogInfo "Performing health check for $ResourceType in environment: $Environment"
    
    $healthResults = @{}
    
    switch ($ResourceType) {
        "compute" {
            $healthResults.compute = Test-ComputeHealth $Environment
        }
        "apps" {
            $healthResults.apps = Test-AppsHealth $Environment
        }
        "network" {
            $healthResults.network = Test-NetworkHealth $Environment
        }
        "all" {
            $healthResults.compute = Test-ComputeHealth $Environment
            $healthResults.apps = Test-AppsHealth $Environment
            $healthResults.network = Test-NetworkHealth $Environment
        }
    }
    
    # Display health summary
    Write-Host "`n=== Health Summary ===" -ForegroundColor Cyan
    foreach ($resource in $healthResults.Keys) {
        $status = if ($healthResults[$resource]) { "HEALTHY" } else { "UNHEALTHY" }
        $color = if ($healthResults[$resource]) { "Green" } else { "Red" }
        Write-Host "$resource`: $status" -ForegroundColor $color
    }
    
    return $healthResults
}

function Get-ResourceMetrics {
    param([string]$ResourceType)
    
    Write-LogInfo "Collecting metrics for $ResourceType in environment: $Environment"
    
    switch ($ResourceType) {
        "compute" { Get-ComputeMetrics $Environment }
        "apps" { Get-AppsMetrics $Environment }
        "network" { Get-NetworkMetrics $Environment }
        "all" {
            Get-ComputeMetrics $Environment
            Get-AppsMetrics $Environment
            Get-NetworkMetrics $Environment
        }
    }
}

function Get-ResourceAlerts {
    param([string]$ResourceType)
    
    Write-LogInfo "Checking alerts for $ResourceType in environment: $Environment"
    
    $alerts = @()
    
    switch ($ResourceType) {
        "compute" { $alerts += Get-ComputeAlerts $Environment }
        "apps" { $alerts += Get-AppsAlerts $Environment }
        "network" { $alerts += Get-NetworkAlerts $Environment }
        "all" {
            $alerts += Get-ComputeAlerts $Environment
            $alerts += Get-AppsAlerts $Environment
            $alerts += Get-NetworkAlerts $Environment
        }
    }
    
    # Display alerts
    if ($alerts.Count -gt 0) {
        Write-Host "`n=== Active Alerts ===" -ForegroundColor Red
        foreach ($alert in $alerts) {
            Write-Host "[$($alert.Severity)] $($alert.Resource): $($alert.Message)" -ForegroundColor Red
        }
    } else {
        Write-Host "`n=== No Active Alerts ===" -ForegroundColor Green
    }
    
    return $alerts
}

function Get-CostAnalysis {
    param([string]$ResourceType)
    
    Write-LogInfo "Analyzing costs for $ResourceType in environment: $Environment"
    
    # Get cost data from AWS Cost Explorer
    $region = Get-AwsRegion
    $startDate = (Get-Date).AddDays(-30).ToString("yyyy-MM-dd")
    $endDate = (Get-Date).ToString("yyyy-MM-dd")
    
    try {
        $costData = aws ce get-cost-and-usage `
            --time-period Start=$startDate,End=$endDate `
            --granularity MONTHLY `
            --metrics BlendedCost `
            --group-by Type=DIMENSION,Key=SERVICE `
            --region $region 2>$null
        
        if ($costData) {
            Write-Host "`n=== Cost Analysis (Last 30 Days) ===" -ForegroundColor Yellow
            Write-Host $costData
        } else {
            Write-Host "No cost data available" -ForegroundColor Yellow
        }
    } catch {
        Write-LogWarn "Failed to retrieve cost data"
    }
    
    # Get resource-specific cost optimization recommendations
    Get-CostOptimizationRecommendations $ResourceType
}

# =============================================================================
# HEALTH CHECK FUNCTIONS
# =============================================================================

function Test-ComputeHealth {
    param([string]$Environment)
    
    Write-LogInfo "Testing compute health for environment: $Environment"
    
    try {
        $clusterName = Get-ClusterName $Environment
        $region = Get-AwsRegion
        
        # Check EKS cluster status
        $clusterStatus = aws eks describe-cluster --name $clusterName --region $region --query 'cluster.status' --output text 2>$null
        
        if ($clusterStatus -ne "ACTIVE") {
            Write-LogError "EKS cluster is not healthy: $clusterStatus"
            return $false
        }
        
        # Check node groups
        $nodeGroups = aws eks list-nodegroups --cluster-name $clusterName --region $region --query 'nodegroups[]' --output text 2>$null
        
        if (-not $nodeGroups) {
            Write-LogError "No node groups found"
            return $false
        }
        
        # Check node health
        if (kubectl cluster-info 2>$null) {
            $unhealthyNodes = kubectl get nodes --field-selector=status.condition[0].status=False 2>$null
            if ($unhealthyNodes -and $unhealthyNodes.Count -gt 0) {
                Write-LogError "Found unhealthy nodes: $unhealthyNodes"
                return $false
            }
        }
        
        Write-LogInfo "Compute resources are healthy"
        return $true
        
    } catch {
        Write-LogError "Failed to check compute health"
        return $false
    }
}

function Test-AppsHealth {
    param([string]$Environment)
    
    Write-LogInfo "Testing applications health for environment: $Environment"
    
    try {
        if (-not (kubectl cluster-info 2>$null)) {
            Write-LogError "Kubernetes cluster not accessible"
            return $false
        }
        
        # Check pod health
        $failedPods = kubectl get pods -A --field-selector=status.phase=Failed 2>$null
        if ($failedPods -and $failedPods.Count -gt 0) {
            Write-LogError "Found failed pods: $failedPods"
            return $false
        }
        
        # Check deployment readiness
        $deployments = kubectl get deployments -A --field-selector=status.readyReplicas!=status.replicas 2>$null
        if ($deployments -and $deployments.Count -gt 0) {
            Write-LogError "Found deployments not ready: $deployments"
            return $false
        }
        
        # Check service endpoints
        $services = kubectl get endpoints -A --field-selector=subsets[0].addresses.Count=0 2>$null
        if ($services -and $services.Count -gt 0) {
            Write-LogError "Found services without endpoints: $services"
            return $false
        }
        
        Write-LogInfo "Applications are healthy"
        return $true
        
    } catch {
        Write-LogError "Failed to check applications health"
        return $false
    }
}

function Test-NetworkHealth {
    param([string]$Environment)
    
    Write-LogInfo "Testing network health for environment: $Environment"
    
    try {
        $projectName = if ($Global:PROJECT_NAME) { $Global:PROJECT_NAME } else { "chess-app" }
        $region = Get-AwsRegion
        
        # Check API Gateway health
        $apiGateway = aws apigatewayv2 get-apis --query "Items[?Name=='$projectName-$Environment-api'].ApiEndpoint" --output text --region $region 2>$null
        if ($apiGateway) {
            try {
                $response = Invoke-WebRequest -Uri $apiGateway -Method GET -TimeoutSec 10
                if ($response.StatusCode -ne 200) {
                    Write-LogError "API Gateway health check failed: $($response.StatusCode)"
                    return $false
                }
            } catch {
                Write-LogError "API Gateway health check failed: $($_.Exception.Message)"
                return $false
            }
        }
        
        # Check CloudFront health
        $cloudFront = aws cloudfront list-distributions --query "DistributionList.Items[?Comment=='$projectName $Environment distribution'].DomainName" --output text --region $region 2>$null
        if ($cloudFront) {
            try {
                $response = Invoke-WebRequest -Uri "https://$cloudFront" -Method GET -TimeoutSec 10
                if ($response.StatusCode -ne 200) {
                    Write-LogError "CloudFront health check failed: $($response.StatusCode)"
                    return $false
                }
            } catch {
                Write-LogError "CloudFront health check failed: $($_.Exception.Message)"
                return $false
            }
        }
        
        Write-LogInfo "Network resources are healthy"
        return $true
        
    } catch {
        Write-LogError "Failed to check network health"
        return $false
    }
}

# =============================================================================
# METRICS COLLECTION FUNCTIONS
# =============================================================================

function Get-ComputeMetrics {
    param([string]$Environment)
    
    Write-LogInfo "Collecting compute metrics for environment: $Environment"
    
    Write-Host "`n=== Compute Metrics ===" -ForegroundColor Cyan
    
    try {
        if (kubectl cluster-info 2>$null) {
            # Node metrics
            Write-Host "Node Metrics:" -ForegroundColor Yellow
            $nodes = kubectl top nodes 2>$null
            if ($nodes) {
                Write-Host $nodes
            } else {
                Write-Host "No node metrics available"
            }
            
            # Pod metrics
            Write-Host "`nPod Metrics:" -ForegroundColor Yellow
            $pods = kubectl top pods -A 2>$null
            if ($pods) {
                Write-Host $pods
            } else {
                Write-Host "No pod metrics available"
            }
        }
        
        # EKS metrics
        $clusterName = Get-ClusterName $Environment
        $region = Get-AwsRegion
        
        $nodeGroups = aws eks list-nodegroups --cluster-name $clusterName --region $region --query 'nodegroups[]' --output text 2>$null
        if ($nodeGroups) {
            Write-Host "`nEKS Node Groups:" -ForegroundColor Yellow
            foreach ($nodeGroup in $nodeGroups.Split()) {
                $nodeGroupInfo = aws eks describe-nodegroup --cluster-name $clusterName --nodegroup-name $nodeGroup --region $region --query 'nodegroup.{Name:nodegroupName,Status:status,Scaling:scalingConfig}' --output table 2>$null
                if ($nodeGroupInfo) {
                    Write-Host $nodeGroupInfo
                }
            }
        }
        
    } catch {
        Write-LogError "Failed to collect compute metrics"
    }
}

function Get-AppsMetrics {
    param([string]$Environment)
    
    Write-LogInfo "Collecting applications metrics for environment: $Environment"
    
    Write-Host "`n=== Applications Metrics ===" -ForegroundColor Cyan
    
    try {
        if (kubectl cluster-info 2>$null) {
            # Deployment status
            Write-Host "Deployment Status:" -ForegroundColor Yellow
            $deployments = kubectl get deployments -A --output wide 2>$null
            if ($deployments) {
                Write-Host $deployments
            }
            
            # Service status
            Write-Host "`nService Status:" -ForegroundColor Yellow
            $services = kubectl get services -A --output wide 2>$null
            if ($services) {
                Write-Host $services
            }
            
            # Ingress status
            Write-Host "`nIngress Status:" -ForegroundColor Yellow
            $ingresses = kubectl get ingress -A --output wide 2>$null
            if ($ingresses) {
                Write-Host $ingresses
            }
        }
        
    } catch {
        Write-LogError "Failed to collect applications metrics"
    }
}

function Get-NetworkMetrics {
    param([string]$Environment)
    
    Write-LogInfo "Collecting network metrics for environment: $Environment"
    
    Write-Host "`n=== Network Metrics ===" -ForegroundColor Cyan
    
    try {
        $projectName = if ($Global:PROJECT_NAME) { $Global:PROJECT_NAME } else { "chess-app" }
        $region = Get-AwsRegion
        
        # API Gateway metrics
        Write-Host "API Gateway Metrics:" -ForegroundColor Yellow
        $apiGateway = aws apigatewayv2 get-apis --query "Items[?Name=='$projectName-$Environment-api'].{Name:Name,ApiEndpoint:ApiEndpoint}" --output table --region $region 2>$null
        if ($apiGateway) {
            Write-Host $apiGateway
        } else {
            Write-Host "No API Gateway found"
        }
        
        # CloudFront metrics
        Write-Host "`nCloudFront Metrics:" -ForegroundColor Yellow
        $cloudFront = aws cloudfront list-distributions --query "DistributionList.Items[?Comment=='$projectName $Environment distribution'].{Id:Id,Status:Status,Domain:DomainName}" --output table --region $region 2>$null
        if ($cloudFront) {
            Write-Host $cloudFront
        } else {
            Write-Host "No CloudFront found"
        }
        
    } catch {
        Write-LogError "Failed to collect network metrics"
    }
}

# =============================================================================
# ALERT FUNCTIONS
# =============================================================================

function Get-ComputeAlerts {
    param([string]$Environment)
    
    $alerts = @()
    
    try {
        $clusterName = Get-ClusterName $Environment
        $region = Get-AwsRegion
        
        # Check cluster status
        $clusterStatus = aws eks describe-cluster --name $clusterName --region $region --query 'cluster.status' --output text 2>$null
        if ($clusterStatus -ne "ACTIVE") {
            $alerts += @{
                Resource = "EKS Cluster"
                Severity = "CRITICAL"
                Message = "Cluster status: $clusterStatus"
            }
        }
        
        # Check node health
        if (kubectl cluster-info 2>$null) {
            $unhealthyNodes = kubectl get nodes --field-selector=status.condition[0].status=False 2>$null
            if ($unhealthyNodes -and $unhealthyNodes.Count -gt 0) {
                $alerts += @{
                    Resource = "EKS Nodes"
                    Severity = "HIGH"
                    Message = "Found $($unhealthyNodes.Count) unhealthy nodes"
                }
            }
        }
        
    } catch {
        $alerts += @{
            Resource = "Compute Monitoring"
            Severity = "MEDIUM"
            Message = "Failed to check compute health"
        }
    }
    
    return $alerts
}

function Get-AppsAlerts {
    param([string]$Environment)
    
    $alerts = @()
    
    try {
        if (kubectl cluster-info 2>$null) {
            # Check failed pods
            $failedPods = kubectl get pods -A --field-selector=status.phase=Failed 2>$null
            if ($failedPods -and $failedPods.Count -gt 0) {
                $alerts += @{
                    Resource = "Kubernetes Pods"
                    Severity = "HIGH"
                    Message = "Found $($failedPods.Count) failed pods"
                }
            }
            
            # Check deployment readiness
            $deployments = kubectl get deployments -A --field-selector=status.readyReplicas!=status.replicas 2>$null
            if ($deployments -and $deployments.Count -gt 0) {
                $alerts += @{
                    Resource = "Kubernetes Deployments"
                    Severity = "MEDIUM"
                    Message = "Found $($deployments.Count) deployments not ready"
                }
            }
        }
        
    } catch {
        $alerts += @{
            Resource = "Applications Monitoring"
            Severity = "MEDIUM"
            Message = "Failed to check applications health"
        }
    }
    
    return $alerts
}

function Get-NetworkAlerts {
    param([string]$Environment)
    
    $alerts = @()
    
    try {
        $projectName = if ($Global:PROJECT_NAME) { $Global:PROJECT_NAME } else { "chess-app" }
        $region = Get-AwsRegion
        
        # Check API Gateway
        $apiGateway = aws apigatewayv2 get-apis --query "Items[?Name=='$projectName-$Environment-api'].Status" --output text --region $region 2>$null
        if ($apiGateway -and $apiGateway -ne "ACTIVE") {
            $alerts += @{
                Resource = "API Gateway"
                Severity = "HIGH"
                Message = "API Gateway status: $apiGateway"
            }
        }
        
        # Check CloudFront
        $cloudFront = aws cloudfront list-distributions --query "DistributionList.Items[?Comment=='$projectName $Environment distribution'].Status" --output text --region $region 2>$null
        if ($cloudFront -and $cloudFront -ne "Deployed") {
            $alerts += @{
                Resource = "CloudFront"
                Severity = "MEDIUM"
                Message = "CloudFront status: $cloudFront"
            }
        }
        
    } catch {
        $alerts += @{
            Resource = "Network Monitoring"
            Severity = "MEDIUM"
            Message = "Failed to check network health"
        }
    }
    
    return $alerts
}

# =============================================================================
# COST OPTIMIZATION FUNCTIONS
# =============================================================================

function Get-CostOptimizationRecommendations {
    param([string]$ResourceType)
    
    Write-Host "`n=== Cost Optimization Recommendations ===" -ForegroundColor Yellow
    
    switch ($ResourceType) {
        "compute" {
            Write-Host "• Review EKS node group sizing and consider using Spot instances"
            Write-Host "• Implement auto-scaling policies to scale down during low usage"
            Write-Host "• Use Reserved Instances for predictable workloads"
            Write-Host "• Consider using Fargate for burst workloads"
        }
        "storage" {
            Write-Host "• Enable S3 lifecycle policies for old data"
            Write-Host "• Use S3 Intelligent Tiering for cost optimization"
            Write-Host "• Review ECR image retention policies"
            Write-Host "• Consider using S3 Glacier for long-term storage"
        }
        "network" {
            Write-Host "• Review CloudFront cache hit ratios"
            Write-Host "• Optimize API Gateway request patterns"
            Write-Host "• Use VPC endpoints for internal traffic"
            Write-Host "• Monitor data transfer costs"
        }
        "all" {
            Write-Host "• Implement comprehensive cost monitoring and alerting"
            Write-Host "• Use AWS Cost Explorer for detailed cost analysis"
            Write-Host "• Set up budget alerts and limits"
            Write-Host "• Regular review of unused resources"
        }
    }
}

# =============================================================================
# CONTINUOUS MONITORING
# =============================================================================

function Start-ContinuousMonitoring {
    param(
        [string]$Environment,
        [string]$Action,
        [string]$Resource,
        [int]$Interval
    )
    
    Write-LogInfo "Starting continuous monitoring for environment: $Environment"
    Write-LogInfo "Action: $Action, Resource: $Resource, Interval: $Interval seconds"
    
    try {
        while ($true) {
            $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
            Write-Host "`n[$timestamp] === Monitoring Cycle ===" -ForegroundColor Magenta
            
            switch ($Action) {
                "status" { Get-ResourceStatus $Resource }
                "health" { Test-ResourceHealth $Resource }
                "metrics" { Get-ResourceMetrics $Resource }
                "alerts" { Get-ResourceAlerts $Resource }
                "cost" { Get-CostAnalysis $Resource }
            }
            
            Write-LogInfo "Monitoring cycle completed. Next cycle in $Interval seconds..."
            Start-Sleep -Seconds $Interval
        }
    } catch {
        Write-LogError "Continuous monitoring failed: $($_.Exception.Message)"
    }
}

# =============================================================================
# MAIN FUNCTION
# =============================================================================

function Main {
    # Show help if requested
    if ($Help) {
        show_usage
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
    
    Write-LogInfo "Starting resource monitoring operation"
    Write-LogInfo "Environment: $Environment"
    Write-LogInfo "Action: $Action"
    Write-LogInfo "Resource: $Resource"
    Write-LogInfo "Detailed: $Detailed"
    Write-LogInfo "Continuous: $Continuous"
    Write-LogInfo "Interval: $Interval seconds"
    
    # Execute requested action
    $success = $false
    switch ($Action) {
        "status" { $success = Get-ResourceStatus $Resource }
        "health" { $success = Test-ResourceHealth $Resource }
        "metrics" { $success = Get-ResourceMetrics $Resource }
        "alerts" { $success = Get-ResourceAlerts $Resource }
        "cost" { $success = Get-CostAnalysis $Resource }
        default {
            Write-LogError "Unknown action: $Action"
            show_usage
            exit 1
        }
    }
    
    # Start continuous monitoring if requested
    if ($Continuous) {
        Start-ContinuousMonitoring $Environment $Action $Resource $Interval
    }
    
    if ($success -or $Action -eq "alerts") {
        Write-LogInfo "Operation completed successfully"
        exit 0
    } else {
        Write-LogError "Operation failed"
        exit 1
    }
}

# Execute main function
Main