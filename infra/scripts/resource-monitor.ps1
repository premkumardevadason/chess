# PowerShell AWS Resource Lifecycle Monitor
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("dev", "staging", "prod")]
    [string]$Environment,
    
    [Parameter(Mandatory=$true)]
    [ValidateSet("start", "status", "halt", "restart", "stop", "remove", "health", "cost")]
    [string]$Action
)

$ProjectName = "chess-app"
$ClusterName = "$ProjectName-$Environment-cluster"

function Write-Log {
    param([string]$Message)
    Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] $Message" -ForegroundColor Blue
}

function Write-Success {
    param([string]$Message)
    Write-Host "[SUCCESS] $Message" -ForegroundColor Green
}

function Write-Error {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

function Test-Prerequisites {
    Write-Log "Checking prerequisites..."
    
    $tools = @("aws", "terraform", "kubectl", "helm")
    foreach ($tool in $tools) {
        if (!(Get-Command $tool -ErrorAction SilentlyContinue)) {
            Write-Error "$tool not found. Please install $tool."
            exit 1
        }
    }
    Write-Success "All prerequisites met"
}

function Start-Resources {
    Write-Log "Starting AWS resources for environment: $Environment"
    
    Set-Location "../terraform"
    
    terraform init
    terraform apply -var-file="environments/$Environment/terraform.tfvars" -auto-approve
    
    $clusterName = terraform output -raw eks_cluster_name
    aws eks update-kubeconfig --region us-west-2 --name $clusterName
    
    Scale-NodeGroups -Direction "up"
    Write-Success "Resources started successfully"
}

function Get-ResourceStatus {
    Write-Log "Checking status of AWS resources for environment: $Environment"
    
    # EKS Cluster Status
    try {
        $clusterStatus = aws eks describe-cluster --name $ClusterName --query 'cluster.status' --output text 2>$null
        Write-Host "EKS Cluster Status: $clusterStatus"
    }
    catch {
        Write-Host "EKS Cluster: Not found"
    }
    
    # Node Groups Status
    try {
        $nodeGroups = aws eks list-nodegroups --cluster-name $ClusterName --query 'nodegroups[]' --output text 2>$null
        Write-Host "Node Groups: $nodeGroups"
    }
    catch {
        Write-Host "Node Groups: Not found"
    }
    
    # Kubernetes Resources
    try {
        kubectl cluster-info | Out-Null
        Write-Host "`nKubernetes Pods:"
        kubectl get pods -A --field-selector=status.phase!=Succeeded
        
        Write-Host "`nHelm Releases:"
        helm list -A
    }
    catch {
        Write-Host "Kubernetes cluster not accessible"
    }
    
    # CloudFront Status
    try {
        $distributions = aws cloudfront list-distributions --query "DistributionList.Items[?Comment=='$ProjectName $Environment distribution'].{Id:Id,Status:Status}" --output table 2>$null
        Write-Host "`nCloudFront Distributions:"
        Write-Host $distributions
    }
    catch {
        Write-Host "CloudFront: No distributions found"
    }
}

function Stop-Resources {
    Write-Log "Temporarily halting AWS resources for environment: $Environment"
    
    Scale-NodeGroups -Direction "down"
    
    try {
        kubectl cluster-info | Out-Null
        Write-Log "Scaling down deployments..."
        kubectl scale deployment --all --replicas=0 -n chess-app 2>$null
        kubectl scale deployment --all --replicas=0 -n monitoring 2>$null
    }
    catch {
        Write-Host "Could not scale down deployments"
    }
    
    Write-Success "Resources halted (scaled down)"
}

function Restart-Resources {
    Write-Log "Restarting AWS resources for environment: $Environment"
    
    Scale-NodeGroups -Direction "up"
    
    Write-Log "Waiting for nodes to be ready..."
    kubectl wait --for=condition=Ready nodes --all --timeout=300s
    
    try {
        kubectl scale deployment chess-app --replicas=3 -n chess-app 2>$null
        kubectl wait --for=condition=available --timeout=300s deployment/chess-app -n chess-app 2>$null
    }
    catch {
        Write-Host "Could not scale up deployments"
    }
    
    Write-Success "Resources restarted successfully"
}

function Remove-AllResources {
    Write-Log "Removing ALL AWS resources for environment: $Environment"
    
    $confirmation = Read-Host "This will DELETE all resources. Are you sure? (y/N)"
    if ($confirmation -ne "y" -and $confirmation -ne "Y") {
        Write-Log "Operation cancelled"
        return
    }
    
    Set-Location "../terraform"
    terraform destroy -var-file="environments/$Environment/terraform.tfvars" -auto-approve
    
    Write-Success "All resources removed"
}

function Scale-NodeGroups {
    param([string]$Direction)
    
    if ($Direction -eq "up") {
        Write-Log "Scaling node groups up..."
        aws eks update-nodegroup-config --cluster-name $ClusterName --nodegroup-name "$ProjectName-$Environment-general" --scaling-config minSize=2,maxSize=6,desiredSize=3 2>$null
        aws eks update-nodegroup-config --cluster-name $ClusterName --nodegroup-name "$ProjectName-$Environment-gpu" --scaling-config minSize=0,maxSize=3,desiredSize=1 2>$null
    }
    else {
        Write-Log "Scaling node groups down..."
        $nodeGroups = aws eks list-nodegroups --cluster-name $ClusterName --query 'nodegroups[]' --output text 2>$null
        foreach ($nodeGroup in $nodeGroups.Split()) {
            if ($nodeGroup) {
                aws eks update-nodegroup-config --cluster-name $ClusterName --nodegroup-name $nodeGroup --scaling-config minSize=0,maxSize=0,desiredSize=0
            }
        }
    }
}

function Test-Health {
    Write-Log "Performing health check for environment: $Environment"
    
    # EKS Cluster Health
    try {
        $clusterStatus = aws eks describe-cluster --name $ClusterName --query 'cluster.status' --output text 2>$null
        if ($clusterStatus -eq "ACTIVE") {
            Write-Success "EKS Cluster is healthy"
        }
        else {
            Write-Error "EKS Cluster status: $clusterStatus"
        }
    }
    catch {
        Write-Error "EKS Cluster not found"
    }
    
    # Application Health
    try {
        kubectl cluster-info | Out-Null
        $appReady = kubectl get deployment chess-app -n chess-app -o jsonpath='{.status.readyReplicas}' 2>$null
        $appDesired = kubectl get deployment chess-app -n chess-app -o jsonpath='{.spec.replicas}' 2>$null
        
        if ($appReady -eq $appDesired -and [int]$appReady -gt 0) {
            Write-Success "Application is healthy ($appReady/$appDesired replicas ready)"
        }
        else {
            Write-Error "Application health issue ($appReady/$appDesired replicas ready)"
        }
    }
    catch {
        Write-Host "Could not check application health"
    }
}

function Get-CostEstimate {
    Write-Log "Estimating costs for environment: $Environment"
    
    if ($Environment -eq "prod") {
        Write-Host "Production Environment Estimated Costs:"
        Write-Host "  - EKS Cluster: ~`$73/month"
        Write-Host "  - EC2 Instances: ~`$505/month"
        Write-Host "  - Load Balancers: ~`$50/month"
        Write-Host "  - CloudFront + WAF: ~`$35/month"
        Write-Host "  - Storage: ~`$20/month"
        Write-Host "  - Total: ~`$683/month"
    }
    else {
        Write-Host "Development Environment Estimated Costs:"
        Write-Host "  - EKS Cluster: ~`$73/month"
        Write-Host "  - EC2 Instances: ~`$60/month"
        Write-Host "  - Load Balancers: ~`$33/month"
        Write-Host "  - CloudFront + WAF: ~`$10/month"
        Write-Host "  - Storage: ~`$11/month"
        Write-Host "  - Total: ~`$187/month"
    }
}

# Main execution
Test-Prerequisites

switch ($Action) {
    "start" { Start-Resources }
    "status" { Get-ResourceStatus }
    "halt" { Stop-Resources }
    "restart" { Restart-Resources }
    "stop" { Stop-Resources }
    "remove" { Remove-AllResources }
    "health" { Test-Health }
    "cost" { Get-CostEstimate }
    default {
        Write-Host "Invalid action. Use: start, status, halt, restart, stop, remove, health, cost"
        exit 1
    }
}