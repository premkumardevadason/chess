#!/bin/bash
# Granular AWS Resource Management Script

set -e

ENVIRONMENT=${1:-dev}
ACTION=${2:-status}
RESOURCE=${3:-all}
PROJECT_NAME="chess-app"

# Resource definitions
declare -A RESOURCES=(
    ["vpc"]="internet-vpc private-vpc transit-gateway"
    ["storage"]="s3 ecr"
    ["security"]="secrets-manager waf"
    ["compute"]="eks"
    ["network"]="api-gateway cloudfront"
    ["apps"]="helm-istio helm-monitoring helm-chess-app"
)

log() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $1"
}

show_usage() {
    echo "Usage: $0 <environment> <action> [resource]"
    echo ""
    echo "Environment: dev, staging, prod"
    echo "Actions: install, start, status, halt, restart, stop, remove, health"
    echo "Resources:"
    echo "  all          - All resources (default)"
    echo "  vpc          - VPCs and networking foundation"
    echo "  storage      - S3 buckets and ECR"
    echo "  security     - Secrets Manager and WAF"
    echo "  compute      - EKS cluster and nodes"
    echo "  network      - API Gateway and CloudFront"
    echo "  apps         - Istio, monitoring, and chess app"
    echo ""
    echo "Examples:"
    echo "  $0 dev install vpc           # Install VPC first"
    echo "  $0 dev install storage       # Then storage"
    echo "  $0 dev install security      # Then security"
    echo "  $0 dev install compute       # Then EKS"
    echo "  $0 dev install network       # Then networking"
    echo "  $0 dev install apps          # Finally applications"
    echo "  $0 dev status eks            # Check EKS status only"
    echo "  $0 dev halt apps             # Halt only applications"
}

check_prerequisites() {
    command -v aws >/dev/null || { echo "AWS CLI required"; exit 1; }
    command -v terraform >/dev/null || { echo "Terraform required"; exit 1; }
    if [[ "$RESOURCE" == "apps" || "$RESOURCE" == "all" ]]; then
        command -v kubectl >/dev/null || { echo "kubectl required for apps"; exit 1; }
        command -v helm >/dev/null || { echo "Helm required for apps"; exit 1; }
    fi
}

get_terraform_targets() {
    local resource_type=$1
    local targets=""
    
    case $resource_type in
        "vpc")
            targets="-target=module.internet_vpc -target=module.private_vpc -target=module.transit_gateway"
            ;;
        "storage")
            targets="-target=module.s3 -target=module.ecr"
            ;;
        "security")
            targets="-target=module.secrets_manager -target=module.waf"
            ;;
        "compute")
            targets="-target=module.eks"
            ;;
        "network")
            targets="-target=module.api_gateway -target=module.cloudfront"
            ;;
        "apps")
            targets="-target=module.helm_istio -target=module.helm_monitoring -target=module.helm_chess_app"
            ;;
        "all")
            targets=""
            ;;
    esac
    echo $targets
}

install_resource() {
    local resource_type=$1
    log "Installing $resource_type for environment: $ENVIRONMENT"
    
    cd ../terraform
    terraform init
    
    local targets=$(get_terraform_targets $resource_type)
    
    if [[ -n "$targets" ]]; then
        terraform plan -var-file="environments/$ENVIRONMENT/terraform.tfvars" $targets
        terraform apply -var-file="environments/$ENVIRONMENT/terraform.tfvars" $targets -auto-approve
    else
        terraform plan -var-file="environments/$ENVIRONMENT/terraform.tfvars"
        terraform apply -var-file="environments/$ENVIRONMENT/terraform.tfvars" -auto-approve
    fi
    
    # Post-install actions
    if [[ "$resource_type" == "compute" || "$resource_type" == "all" ]]; then
        CLUSTER_NAME=$(terraform output -raw eks_cluster_name 2>/dev/null || echo "")
        if [[ -n "$CLUSTER_NAME" ]]; then
            aws eks update-kubeconfig --region us-west-2 --name $CLUSTER_NAME
        fi
    fi
    
    log "$resource_type installation completed"
}

start_resource() {
    local resource_type=$1
    log "Starting $resource_type for environment: $ENVIRONMENT"
    
    case $resource_type in
        "compute"|"all")
            scale_node_groups "up"
            ;;
        "apps")
            if kubectl cluster-info &>/dev/null; then
                kubectl scale deployment --all --replicas=3 -n chess-app 2>/dev/null || true
                kubectl scale deployment --all --replicas=1 -n monitoring 2>/dev/null || true
            fi
            ;;
    esac
    
    log "$resource_type started"
}

status_resource() {
    local resource_type=$1
    log "Checking status of $resource_type for environment: $ENVIRONMENT"
    
    case $resource_type in
        "vpc")
            check_vpc_status
            ;;
        "storage")
            check_storage_status
            ;;
        "security")
            check_security_status
            ;;
        "compute")
            check_compute_status
            ;;
        "network")
            check_network_status
            ;;
        "apps")
            check_apps_status
            ;;
        "all")
            check_vpc_status
            check_storage_status
            check_security_status
            check_compute_status
            check_network_status
            check_apps_status
            ;;
    esac
}

check_vpc_status() {
    echo "=== VPC Status ==="
    aws ec2 describe-vpcs --filters "Name=tag:Name,Values=*$PROJECT_NAME-$ENVIRONMENT*" --query 'Vpcs[].{Name:Tags[?Key==`Name`].Value|[0],State:State,VpcId:VpcId}' --output table 2>/dev/null || echo "No VPCs found"
}

check_storage_status() {
    echo "=== Storage Status ==="
    aws s3 ls | grep "$PROJECT_NAME.*$ENVIRONMENT" || echo "No S3 buckets found"
    aws ecr describe-repositories --repository-names "$PROJECT_NAME-$ENVIRONMENT" --query 'repositories[].repositoryName' --output text 2>/dev/null || echo "No ECR repositories found"
}

check_security_status() {
    echo "=== Security Status ==="
    aws secretsmanager list-secrets --filters Key=name,Values="$PROJECT_NAME-$ENVIRONMENT-secrets" --query 'SecretList[].Name' --output text 2>/dev/null || echo "No secrets found"
    aws wafv2 list-web-acls --scope=CLOUDFRONT --query "WebACLs[?Name=='$PROJECT_NAME-$ENVIRONMENT-waf'].Name" --output text 2>/dev/null || echo "No WAF found"
}

check_compute_status() {
    echo "=== Compute Status ==="
    CLUSTER_NAME="${PROJECT_NAME}-${ENVIRONMENT}-cluster"
    aws eks describe-cluster --name $CLUSTER_NAME --query 'cluster.status' --output text 2>/dev/null || echo "EKS cluster not found"
    aws eks list-nodegroups --cluster-name $CLUSTER_NAME --query 'nodegroups[]' --output table 2>/dev/null || echo "No node groups found"
}

check_network_status() {
    echo "=== Network Status ==="
    aws apigatewayv2 get-apis --query "Items[?Name=='$PROJECT_NAME-$ENVIRONMENT-api'].{Name:Name,ApiEndpoint:ApiEndpoint}" --output table 2>/dev/null || echo "No API Gateway found"
    aws cloudfront list-distributions --query "DistributionList.Items[?Comment=='$PROJECT_NAME $ENVIRONMENT distribution'].{Id:Id,Status:Status}" --output table 2>/dev/null || echo "No CloudFront found"
}

check_apps_status() {
    echo "=== Applications Status ==="
    if kubectl cluster-info &>/dev/null; then
        echo "Kubernetes Pods:"
        kubectl get pods -A --field-selector=status.phase!=Succeeded
        echo -e "\nHelm Releases:"
        helm list -A
    else
        echo "Kubernetes cluster not accessible"
    fi
}

halt_resource() {
    local resource_type=$1
    log "Halting $resource_type for environment: $ENVIRONMENT"
    
    case $resource_type in
        "compute")
            scale_node_groups "down"
            ;;
        "apps")
            if kubectl cluster-info &>/dev/null; then
                kubectl scale deployment --all --replicas=0 -n chess-app 2>/dev/null || true
                kubectl scale deployment --all --replicas=0 -n monitoring 2>/dev/null || true
            fi
            ;;
        "all")
            halt_resource "apps"
            halt_resource "compute"
            ;;
    esac
    
    log "$resource_type halted"
}

restart_resource() {
    local resource_type=$1
    log "Restarting $resource_type for environment: $ENVIRONMENT"
    
    case $resource_type in
        "compute")
            scale_node_groups "up"
            kubectl wait --for=condition=Ready nodes --all --timeout=300s 2>/dev/null || true
            ;;
        "apps")
            if kubectl cluster-info &>/dev/null; then
                kubectl scale deployment chess-app --replicas=3 -n chess-app 2>/dev/null || true
                kubectl wait --for=condition=available --timeout=300s deployment/chess-app -n chess-app 2>/dev/null || true
            fi
            ;;
        "all")
            restart_resource "compute"
            restart_resource "apps"
            ;;
    esac
    
    log "$resource_type restarted"
}

remove_resource() {
    local resource_type=$1
    log "Removing $resource_type for environment: $ENVIRONMENT"
    
    echo "This will DELETE $resource_type resources. Are you sure? (y/N)"
    read -r confirmation
    [[ $confirmation =~ ^[Yy]$ ]] || return
    
    cd ../terraform
    
    local targets=$(get_terraform_targets $resource_type)
    
    if [[ -n "$targets" ]]; then
        terraform destroy -var-file="environments/$ENVIRONMENT/terraform.tfvars" $targets -auto-approve
    else
        terraform destroy -var-file="environments/$ENVIRONMENT/terraform.tfvars" -auto-approve
    fi
    
    log "$resource_type removed"
}

scale_node_groups() {
    local direction=$1
    CLUSTER_NAME="${PROJECT_NAME}-${ENVIRONMENT}-cluster"
    
    if [[ $direction == "up" ]]; then
        aws eks update-nodegroup-config --cluster-name $CLUSTER_NAME --nodegroup-name "${PROJECT_NAME}-${ENVIRONMENT}-general" --scaling-config minSize=2,maxSize=6,desiredSize=3 2>/dev/null || true
        aws eks update-nodegroup-config --cluster-name $CLUSTER_NAME --nodegroup-name "${PROJECT_NAME}-${ENVIRONMENT}-gpu" --scaling-config minSize=0,maxSize=3,desiredSize=1 2>/dev/null || true
    else
        NODE_GROUPS=$(aws eks list-nodegroups --cluster-name $CLUSTER_NAME --query 'nodegroups[]' --output text 2>/dev/null || echo "")
        for NODE_GROUP in $NODE_GROUPS; do
            aws eks update-nodegroup-config --cluster-name $CLUSTER_NAME --nodegroup-name $NODE_GROUP --scaling-config minSize=0,maxSize=0,desiredSize=0
        done
    fi
}

health_check_resource() {
    local resource_type=$1
    log "Health check for $resource_type in environment: $ENVIRONMENT"
    
    case $resource_type in
        "compute")
            CLUSTER_NAME="${PROJECT_NAME}-${ENVIRONMENT}-cluster"
            CLUSTER_STATUS=$(aws eks describe-cluster --name $CLUSTER_NAME --query 'cluster.status' --output text 2>/dev/null || echo "NOT_FOUND")
            echo "EKS Cluster status: $CLUSTER_STATUS"
            ;;
        "apps")
            if kubectl cluster-info &>/dev/null; then
                APP_READY=$(kubectl get deployment chess-app -n chess-app -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
                APP_DESIRED=$(kubectl get deployment chess-app -n chess-app -o jsonpath='{.spec.replicas}' 2>/dev/null || echo "0")
                echo "Application health: $APP_READY/$APP_DESIRED replicas ready"
            fi
            ;;
        "all")
            health_check_resource "compute"
            health_check_resource "apps"
            ;;
    esac
}

main() {
    if [[ "$1" == "-h" || "$1" == "--help" ]]; then
        show_usage
        exit 0
    fi
    
    check_prerequisites
    
    case $ACTION in
        "install") install_resource $RESOURCE ;;
        "start") start_resource $RESOURCE ;;
        "status") status_resource $RESOURCE ;;
        "halt") halt_resource $RESOURCE ;;
        "restart") restart_resource $RESOURCE ;;
        "stop") halt_resource $RESOURCE ;;
        "remove"|"delete") remove_resource $RESOURCE ;;
        "health") health_check_resource $RESOURCE ;;
        *)
            show_usage
            exit 1
            ;;
    esac
}

main "$@"