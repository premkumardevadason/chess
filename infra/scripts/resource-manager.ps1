# PowerShell Granular AWS Resource Management Script
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("dev", "staging", "prod")]
    [string]$Environment,
    
    [Parameter(Mandatory=$true)]
    [ValidateSet("install", "start", "status", "halt", "restart", "stop", "remove", "health")]
    [string]$Action,
    
    [Parameter(Mandatory=$false)]
    [ValidateSet("all", "vpc", "storage", "security", "compute", "network", "apps")]
    [string]$Resource = "all"
)

$ProjectName = "chess-app"
$ClusterName = "$ProjectName-$Environment-cluster"

$ResourceMap = @{
    "vpc" = @("internet-vpc", "private-vpc", "transit-gateway")
    "storage" = @("s3", "ecr")
    "security" = @("secrets-manager", "waf")
    "compute" = @("eks")
    "network" = @("api-gateway", "cloudfront")
    "apps" = @("helm-istio", "helm-monitoring", "helm-chess-app")
}

function Write-Log {
    param([string]$Message)
    Write-Host "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] $Message" -ForegroundColor Blue
}

function Show-Usage {
    Write-Host @"
Usage: .\resource-manager.ps1 -Environment <env> -Action <action> [-Resource <resource>]

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

Examples:
  .\resource-manager.ps1 -Environment dev -Action install -Resource vpc
  .\resource-manager.ps1 -Environment dev -Action status -Resource compute
  .\resource-manager.ps1 -Environment dev -Action halt -Resource apps
"@
}

function Test-Prerequisites {
    $tools = @("aws", "terraform")
    if ($Resource -eq "apps" -or $Resource -eq "all") {
        $tools += @("kubectl", "helm")
    }
    
    foreach ($tool in $tools) {
        if (!(Get-Command $tool -ErrorAction SilentlyContinue)) {
            Write-Error "$tool not found. Please install $tool."
            exit 1
        }
    }
}

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

function Install-Resource {
    param([string]$ResourceType)
    Write-Log "Installing $ResourceType for environment: $Environment"
    
    Set-Location "../terraform"
    
    terraform init
    
    $targets = Get-TerraformTargets -ResourceType $ResourceType
    
    if ($targets) {
        terraform plan -var-file="environments/$Environment/terraform.tfvars" $targets.Split(' ')
        terraform apply -var-file="environments/$Environment/terraform.tfvars" $targets.Split(' ') -auto-approve
    } else {
        terraform plan -var-file="environments/$Environment/terraform.tfvars"
        terraform apply -var-file="environments/$Environment/terraform.tfvars" -auto-approve
    }
    
    # Post-install actions
    if ($ResourceType -eq "compute" -or $ResourceType -eq "all") {
        try {
            $clusterName = terraform output -raw eks_cluster_name
            if ($clusterName) {
                aws eks update-kubeconfig --region us-west-2 --name $clusterName
            }
        } catch {
            Write-Host "Could not update kubeconfig"
        }
    }
    
    Write-Log "$ResourceType installation completed"
}

function Start-Resource {
    param([string]$ResourceType)
    Write-Log "Starting $ResourceType for environment: $Environment"
    
    switch ($ResourceType) {
        { $_ -eq "compute" -or $_ -eq "all" } {
            Scale-NodeGroups -Direction "up"
        }
        "apps" {
            try {
                kubectl cluster-info | Out-Null
                kubectl scale deployment --all --replicas=3 -n chess-app 2>$null
                kubectl scale deployment --all --replicas=1 -n monitoring 2>$null
            } catch {
                Write-Host "Could not scale applications"
            }
        }
    }
    
    Write-Log "$ResourceType started"
}

function Get-ResourceStatus {
    param([string]$ResourceType)
    Write-Log "Checking status of $ResourceType for environment: $Environment"
    
    switch ($ResourceType) {
        "vpc" { Get-VpcStatus }
        "storage" { Get-StorageStatus }
        "security" { Get-SecurityStatus }
        "compute" { Get-ComputeStatus }
        "network" { Get-NetworkStatus }
        "apps" { Get-AppsStatus }
        "all" {
            Get-VpcStatus
            Get-StorageStatus
            Get-SecurityStatus
            Get-ComputeStatus
            Get-NetworkStatus
            Get-AppsStatus
        }
    }
}

function Get-VpcStatus {
    Write-Host "=== VPC Status ===" -ForegroundColor Yellow
    try {
        aws ec2 describe-vpcs --filters "Name=tag:Name,Values=*$ProjectName-$Environment*" --query 'Vpcs[].{Name:Tags[?Key==`Name`].Value|[0],State:State,VpcId:VpcId}' --output table
    } catch {
        Write-Host "No VPCs found"
    }
}

function Get-StorageStatus {
    Write-Host "=== Storage Status ===" -ForegroundColor Yellow
    try {
        $s3Buckets = aws s3 ls | Select-String "$ProjectName.*$Environment"
        if ($s3Buckets) { $s3Buckets } else { "No S3 buckets found" }
        
        $ecrRepo = aws ecr describe-repositories --repository-names "$ProjectName-$Environment" --query 'repositories[].repositoryName' --output text 2>$null
        if ($ecrRepo) { "ECR: $ecrRepo" } else { "No ECR repositories found" }
    } catch {
        Write-Host "Storage resources not found"
    }
}

function Get-SecurityStatus {
    Write-Host "=== Security Status ===" -ForegroundColor Yellow
    try {
        $secrets = aws secretsmanager list-secrets --filters Key=name,Values="$ProjectName-$Environment-secrets" --query 'SecretList[].Name' --output text 2>$null
        if ($secrets) { "Secrets: $secrets" } else { "No secrets found" }
        
        $waf = aws wafv2 list-web-acls --scope=CLOUDFRONT --query "WebACLs[?Name=='$ProjectName-$Environment-waf'].Name" --output text 2>$null
        if ($waf) { "WAF: $waf" } else { "No WAF found" }
    } catch {
        Write-Host "Security resources not found"
    }
}

function Get-ComputeStatus {
    Write-Host "=== Compute Status ===" -ForegroundColor Yellow
    try {
        $clusterStatus = aws eks describe-cluster --name $ClusterName --query 'cluster.status' --output text 2>$null
        Write-Host "EKS Cluster Status: $clusterStatus"
        
        $nodeGroups = aws eks list-nodegroups --cluster-name $ClusterName --query 'nodegroups[]' --output table 2>$null
        if ($nodeGroups) { $nodeGroups } else { "No node groups found" }
    } catch {
        Write-Host "EKS cluster not found"
    }
}

function Get-NetworkStatus {
    Write-Host "=== Network Status ===" -ForegroundColor Yellow
    try {
        aws apigatewayv2 get-apis --query "Items[?Name=='$ProjectName-$Environment-api'].{Name:Name,ApiEndpoint:ApiEndpoint}" --output table 2>$null
        aws cloudfront list-distributions --query "DistributionList.Items[?Comment=='$ProjectName $Environment distribution'].{Id:Id,Status:Status}" --output table 2>$null
    } catch {
        Write-Host "Network resources not found"
    }
}

function Get-AppsStatus {
    Write-Host "=== Applications Status ===" -ForegroundColor Yellow
    try {
        kubectl cluster-info | Out-Null
        Write-Host "Kubernetes Pods:"
        kubectl get pods -A --field-selector=status.phase!=Succeeded
        Write-Host "`nHelm Releases:"
        helm list -A
    } catch {
        Write-Host "Kubernetes cluster not accessible"
    }
}

function Stop-Resource {
    param([string]$ResourceType)
    Write-Log "Halting $ResourceType for environment: $Environment"
    
    switch ($ResourceType) {
        "compute" {
            Scale-NodeGroups -Direction "down"
        }
        "apps" {
            try {
                kubectl cluster-info | Out-Null
                kubectl scale deployment --all --replicas=0 -n chess-app 2>$null
                kubectl scale deployment --all --replicas=0 -n monitoring 2>$null
            } catch {
                Write-Host "Could not scale down applications"
            }
        }
        "all" {
            Stop-Resource -ResourceType "apps"
            Stop-Resource -ResourceType "compute"
        }
    }
    
    Write-Log "$ResourceType halted"
}

function Restart-Resource {
    param([string]$ResourceType)
    Write-Log "Restarting $ResourceType for environment: $Environment"
    
    switch ($ResourceType) {
        "compute" {
            Scale-NodeGroups -Direction "up"
            kubectl wait --for=condition=Ready nodes --all --timeout=300s 2>$null
        }
        "apps" {
            try {
                kubectl cluster-info | Out-Null
                kubectl scale deployment chess-app --replicas=3 -n chess-app 2>$null
                kubectl wait --for=condition=available --timeout=300s deployment/chess-app -n chess-app 2>$null
            } catch {
                Write-Host "Could not restart applications"
            }
        }
        "all" {
            Restart-Resource -ResourceType "compute"
            Restart-Resource -ResourceType "apps"
        }
    }
    
    Write-Log "$ResourceType restarted"
}

function Remove-Resource {
    param([string]$ResourceType)
    Write-Log "Removing $ResourceType for environment: $Environment"
    
    $confirmation = Read-Host "This will DELETE $ResourceType resources. Are you sure? (y/N)"
    if ($confirmation -ne "y" -and $confirmation -ne "Y") {
        Write-Log "Operation cancelled"
        return
    }
    
    Set-Location "../terraform"
    
    $targets = Get-TerraformTargets -ResourceType $ResourceType
    
    if ($targets) {
        terraform destroy -var-file="environments/$Environment/terraform.tfvars" $targets.Split(' ') -auto-approve
    } else {
        terraform destroy -var-file="environments/$Environment/terraform.tfvars" -auto-approve
    }
    
    Write-Log "$ResourceType removed"
}

function Scale-NodeGroups {
    param([string]$Direction)
    
    if ($Direction -eq "up") {
        Write-Log "Scaling node groups up..."
        aws eks update-nodegroup-config --cluster-name $ClusterName --nodegroup-name "$ProjectName-$Environment-general" --scaling-config minSize=2,maxSize=6,desiredSize=3 2>$null
        aws eks update-nodegroup-config --cluster-name $ClusterName --nodegroup-name "$ProjectName-$Environment-gpu" --scaling-config minSize=0,maxSize=3,desiredSize=1 2>$null
    } else {
        Write-Log "Scaling node groups down..."
        $nodeGroups = aws eks list-nodegroups --cluster-name $ClusterName --query 'nodegroups[]' --output text 2>$null
        foreach ($nodeGroup in $nodeGroups.Split()) {
            if ($nodeGroup) {
                aws eks update-nodegroup-config --cluster-name $ClusterName --nodegroup-name $nodeGroup --scaling-config minSize=0,maxSize=0,desiredSize=0
            }
        }
    }
}

function Test-ResourceHealth {
    param([string]$ResourceType)
    Write-Log "Health check for $ResourceType in environment: $Environment"
    
    switch ($ResourceType) {
        "compute" {
            try {
                $clusterStatus = aws eks describe-cluster --name $ClusterName --query 'cluster.status' --output text 2>$null
                Write-Host "EKS Cluster status: $clusterStatus"
            } catch {
                Write-Host "EKS Cluster not found"
            }
        }
        "apps" {
            try {
                kubectl cluster-info | Out-Null
                $appReady = kubectl get deployment chess-app -n chess-app -o jsonpath='{.status.readyReplicas}' 2>$null
                $appDesired = kubectl get deployment chess-app -n chess-app -o jsonpath='{.spec.replicas}' 2>$null
                Write-Host "Application health: $appReady/$appDesired replicas ready"
            } catch {
                Write-Host "Could not check application health"
            }
        }
        "all" {
            Test-ResourceHealth -ResourceType "compute"
            Test-ResourceHealth -ResourceType "apps"
        }
    }
}

# Main execution
Test-Prerequisites

switch ($Action) {
    "install" { Install-Resource -ResourceType $Resource }
    "start" { Start-Resource -ResourceType $Resource }
    "status" { Get-ResourceStatus -ResourceType $Resource }
    "halt" { Stop-Resource -ResourceType $Resource }
    "restart" { Restart-Resource -ResourceType $Resource }
    "stop" { Stop-Resource -ResourceType $Resource }
    "remove" { Remove-Resource -ResourceType $Resource }
    "health" { Test-ResourceHealth -ResourceType $Resource }
    default {
        Show-Usage
        exit 1
    }
}