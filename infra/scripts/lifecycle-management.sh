#!/bin/bash
# AWS Resource Lifecycle Management Script

set -e

ENVIRONMENT=${1:-dev}
ACTION=${2:-status}
PROJECT_NAME="chess-app"

log() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $1"
}

check_prerequisites() {
    log "Checking prerequisites..."
    command -v aws >/dev/null || { echo "AWS CLI required"; exit 1; }
    command -v terraform >/dev/null || { echo "Terraform required"; exit 1; }
    command -v kubectl >/dev/null || { echo "kubectl required"; exit 1; }
    command -v helm >/dev/null || { echo "Helm required"; exit 1; }
}

start_resources() {
    log "Starting AWS resources for environment: $ENVIRONMENT"
    cd ../terraform
    terraform init
    terraform apply -var-file="environments/$ENVIRONMENT/terraform.tfvars" -auto-approve
    
    CLUSTER_NAME=$(terraform output -raw eks_cluster_name)
    aws eks update-kubeconfig --region us-west-2 --name $CLUSTER_NAME
    scale_node_groups "up"
}

status_check() {
    log "Checking status of AWS resources for environment: $ENVIRONMENT"
    cd ../terraform
    
    CLUSTER_NAME="${PROJECT_NAME}-${ENVIRONMENT}-cluster"
    aws eks describe-cluster --name $CLUSTER_NAME --query 'cluster.status' --output text 2>/dev/null || echo "Cluster not found"
    
    if kubectl cluster-info &>/dev/null; then
        kubectl get pods -A --field-selector=status.phase!=Succeeded
        helm list -A
    fi
}

halt_resources() {
    log "Temporarily halting AWS resources for environment: $ENVIRONMENT"
    scale_node_groups "down"
    
    if kubectl cluster-info &>/dev/null; then
        kubectl scale deployment --all --replicas=0 -n chess-app 2>/dev/null || true
        kubectl scale deployment --all --replicas=0 -n monitoring 2>/dev/null || true
    fi
}

restart_resources() {
    log "Restarting AWS resources for environment: $ENVIRONMENT"
    scale_node_groups "up"
    kubectl wait --for=condition=Ready nodes --all --timeout=300s
    kubectl scale deployment chess-app --replicas=3 -n chess-app 2>/dev/null || true
}

stop_resources() {
    log "Stopping compute resources for environment: $ENVIRONMENT"
    halt_resources
    
    CLUSTER_NAME="${PROJECT_NAME}-${ENVIRONMENT}-cluster"
    NODE_GROUPS=$(aws eks list-nodegroups --cluster-name $CLUSTER_NAME --query 'nodegroups[]' --output text 2>/dev/null || echo "")
    
    for NODE_GROUP in $NODE_GROUPS; do
        aws eks update-nodegroup-config --cluster-name $CLUSTER_NAME --nodegroup-name $NODE_GROUP --scaling-config minSize=0,maxSize=0,desiredSize=0
    done
}

remove_resources() {
    log "Removing ALL AWS resources for environment: $ENVIRONMENT"
    echo "This will DELETE all resources. Are you sure? (y/N)"
    read -r confirmation
    [[ $confirmation =~ ^[Yy]$ ]] || exit 0
    
    cd ../terraform
    terraform destroy -var-file="environments/$ENVIRONMENT/terraform.tfvars" -auto-approve
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

health_check() {
    log "Performing health check for environment: $ENVIRONMENT"
    CLUSTER_NAME="${PROJECT_NAME}-${ENVIRONMENT}-cluster"
    CLUSTER_STATUS=$(aws eks describe-cluster --name $CLUSTER_NAME --query 'cluster.status' --output text 2>/dev/null || echo "NOT_FOUND")
    echo "EKS Cluster status: $CLUSTER_STATUS"
    
    if kubectl cluster-info &>/dev/null; then
        APP_READY=$(kubectl get deployment chess-app -n chess-app -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
        APP_DESIRED=$(kubectl get deployment chess-app -n chess-app -o jsonpath='{.spec.replicas}' 2>/dev/null || echo "0")
        echo "Application health: $APP_READY/$APP_DESIRED replicas ready"
    fi
}

main() {
    check_prerequisites
    
    case $ACTION in
        "start") start_resources ;;
        "status") status_check ;;
        "halt") halt_resources ;;
        "restart") restart_resources ;;
        "stop") stop_resources ;;
        "remove"|"delete") remove_resources ;;
        "health") health_check ;;
        *)
            echo "Usage: $0 <environment> <action>"
            echo "Actions: start, status, halt, restart, stop, remove, health"
            exit 1
            ;;
    esac
}

main "$@"