#!/bin/bash
# Granular AWS Resource Management Script - REFACTORED VERSION
# This script now uses shared libraries for better maintainability and error handling

set -euo pipefail

# Source shared libraries
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common-functions.sh"
source "$SCRIPT_DIR/lib/aws-utils.sh"

# Parse command line arguments
ENVIRONMENT=${1:-}
ACTION=${2:-}
RESOURCE=${3:-all}

# Global variables
DRY_RUN=${DRY_RUN:-false}
AUTO_APPROVE=${AUTO_APPROVE:-false}
LOG_LEVEL=${LOG_LEVEL:-INFO}
LOG_JSON=${LOG_JSON:-false}

# Set log level from environment variable
case ${LOG_LEVEL^^} in
    "DEBUG") LOG_LEVEL=$LOG_LEVEL_DEBUG ;;
    "INFO") LOG_LEVEL=$LOG_LEVEL_INFO ;;
    "WARN") LOG_LEVEL=$LOG_LEVEL_WARN ;;
    "ERROR") LOG_LEVEL=$LOG_LEVEL_ERROR ;;
    *) LOG_LEVEL=$LOG_LEVEL_INFO ;;
esac

# Resource definitions
declare -A RESOURCES=(
    ["vpc"]="internet-vpc private-vpc transit-gateway"
    ["storage"]="s3 ecr"
    ["security"]="secrets-manager waf"
    ["compute"]="eks"
    ["network"]="api-gateway cloudfront"
    ["apps"]="helm-istio helm-monitoring helm-chess-app"
)

show_usage() {
    cat << EOF
Usage: $0 <environment> <action> [resource] [options]

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
  --dry-run    - Show what would be executed without making changes
  --auto-approve - Skip confirmation prompts
  --log-level  - Set log level (DEBUG, INFO, WARN, ERROR)
  --log-json   - Enable structured JSON logging

Examples:
  $0 dev install vpc                    # Install VPC first
  $0 dev install storage                # Then storage
  $0 dev install security               # Then security
  $0 dev install compute                # Then EKS
  $0 dev install network                # Then networking
  $0 dev install apps                   # Finally applications
  $0 dev status eks                     # Check EKS status only
  $0 dev halt apps                      # Halt only applications
  $0 dev install all --dry-run         # Dry run of full installation
  $0 prod status all --log-level DEBUG # Debug logging for production status

Environment Configuration:
  Configuration files are loaded from: $CONFIG_DIR/
  - dev.conf      - Development environment settings
  - staging.conf  - Staging environment settings  
  - prod.conf     - Production environment settings

Error Handling:
  - Automatic rollback on failures
  - Comprehensive error logging
  - Progress tracking for long operations
  - Timeout protection for hanging operations

EOF
}

parse_options() {
    while [[ $# -gt 0 ]]; do
        case $1 in
            --dry-run)
                DRY_RUN=true
                shift
                ;;
            --auto-approve)
                AUTO_APPROVE=true
                shift
                ;;
            --log-level)
                LOG_LEVEL="$2"
                shift 2
                ;;
            --log-json)
                LOG_JSON=true
                shift
                ;;
            --help|-h)
                show_usage
                exit 0
                ;;
            -*)
                log_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
            *)
                # Positional arguments
                if [[ -z "$ENVIRONMENT" ]]; then
                    ENVIRONMENT="$1"
                elif [[ -z "$ACTION" ]]; then
                    ACTION="$1"
                elif [[ -z "$RESOURCE" ]]; then
                    RESOURCE="$1"
                else
                    log_error "Too many arguments"
                    show_usage
                    exit 1
                fi
                shift
                ;;
        esac
    done
}

validate_inputs() {
    # Validate environment
    if [[ -z "$ENVIRONMENT" ]]; then
        log_error "Environment is required"
        show_usage
        exit 1
    fi
    
    if ! validate_environment "$ENVIRONMENT"; then
        exit 1
    fi
    
    # Validate action
    if [[ -z "$ACTION" ]]; then
        log_error "Action is required"
        show_usage
        exit 1
    fi
    
    if ! validate_action "$ACTION"; then
        exit 1
    fi
    
    # Validate resource
    if ! validate_resource "$RESOURCE"; then
        exit 1
    fi
}

# =============================================================================
# RESOURCE INSTALLATION FUNCTIONS
# =============================================================================

install_resource() {
    local resource_type=$1
    log_info "Installing $resource_type for environment: $ENVIRONMENT"
    
    # Add rollback operation
    case $resource_type in
        "vpc") add_rollback_operation "rollback_vpc_installation $ENVIRONMENT" ;;
        "storage") add_rollback_operation "rollback_storage_installation $ENVIRONMENT" ;;
        "security") add_rollback_operation "rollback_security_installation $ENVIRONMENT" ;;
        "compute") add_rollback_operation "rollback_compute_installation $ENVIRONMENT" ;;
        "network") add_rollback_operation "rollback_network_installation $ENVIRONMENT" ;;
        "apps") add_rollback_operation "rollback_apps_installation $ENVIRONMENT" ;;
    esac
    
    # Run Terraform operation
    if ! run_terraform_operation "apply" "$ENVIRONMENT" "$resource_type" "true"; then
        log_error "Failed to install $resource_type"
        return 1
    fi
    
    # Post-install actions
    if [[ "$resource_type" == "compute" || "$resource_type" == "all" ]]; then
        if ! update_kubeconfig "$ENVIRONMENT"; then
            log_error "Failed to update kubeconfig"
            return 1
        fi
    fi
    
    log_info "$resource_type installation completed successfully"
    return 0
}

install_all_resources() {
    log_info "Installing all resources for environment: $ENVIRONMENT"
    
    local resources=("vpc" "storage" "security" "compute" "network" "apps")
    local total=${#resources[@]}
    local current=0
    
    # Install resources sequentially (dependencies matter)
    for resource in "${resources[@]}"; do
        ((current++))
        show_progress "Installing resources" "$current" "$total"
        
        log_info "Installing $resource ($current/$total)..."
        
        if ! install_resource "$resource"; then
            log_error "Failed to install $resource, stopping installation"
            return 1
        fi
        
        # Wait between installations to ensure stability
        if [[ $current -lt $total ]]; then
            log_info "Waiting for $resource to stabilize..."
            sleep 30
        fi
    done
    
    log_info "All resources installed successfully"
    return 0
}

# =============================================================================
# RESOURCE START/STOP FUNCTIONS
# =============================================================================

start_resource() {
    local resource_type=$1
    log_info "Starting $resource_type for environment: $ENVIRONMENT"
    
    case $resource_type in
        "compute"|"all")
            if ! scale_node_groups "up" "$ENVIRONMENT"; then
                log_error "Failed to scale up node groups"
                return 1
            fi
            
            # Wait for cluster to be ready
            if ! wait_for_cluster_ready "$ENVIRONMENT"; then
                log_error "Failed to wait for cluster readiness"
                return 1
            fi
            ;;
        "apps")
            if kubectl cluster-info &>/dev/null; then
                if ! scale_deployments "chess-app" 3; then
                    log_error "Failed to scale chess-app deployments"
                    return 1
                fi
                
                if ! scale_deployments "monitoring" 1; then
                    log_error "Failed to scale monitoring deployments"
                    return 1
                fi
                
                # Wait for deployments to be ready
                if ! wait_for_deployment_ready "chess-app" "chess-app"; then
                    log_error "Failed to wait for chess-app deployment"
                    return 1
                fi
            else
                log_error "Kubernetes cluster not accessible"
                return 1
            fi
            ;;
    esac
    
    log_info "$resource_type started successfully"
    return 0
}

halt_resource() {
    local resource_type=$1
    log_info "Halting $resource_type for environment: $ENVIRONMENT"
    
    case $resource_type in
        "compute")
            if ! scale_node_groups "down" "$ENVIRONMENT"; then
                log_error "Failed to scale down node groups"
                return 1
            fi
            ;;
        "apps")
            if kubectl cluster-info &>/dev/null; then
                if ! scale_deployments "chess-app" 0; then
                    log_error "Failed to scale down chess-app deployments"
                    return 1
                fi
                
                if ! scale_deployments "monitoring" 0; then
                    log_error "Failed to scale down monitoring deployments"
                    return 1
                fi
            else
                log_error "Kubernetes cluster not accessible"
                return 1
            fi
            ;;
        "all")
            if ! halt_resource "apps"; then
                return 1
            fi
            if ! halt_resource "compute"; then
                return 1
            fi
            ;;
    esac
    
    log_info "$resource_type halted successfully"
    return 0
}

restart_resource() {
    local resource_type=$1
    log_info "Restarting $resource_type for environment: $ENVIRONMENT"
    
    case $resource_type in
        "compute")
            if ! scale_node_groups "up" "$ENVIRONMENT"; then
                log_error "Failed to scale up node groups"
                return 1
            fi
            
            if ! wait_for_cluster_ready "$ENVIRONMENT"; then
                log_error "Failed to wait for cluster readiness"
                return 1
            fi
            
            if ! wait_for_nodes_ready; then
                log_error "Failed to wait for nodes readiness"
                return 1
            fi
            ;;
        "apps")
            if kubectl cluster-info &>/dev/null; then
                if ! scale_deployments "chess-app" 3; then
                    log_error "Failed to scale up chess-app deployments"
                    return 1
                fi
                
                if ! wait_for_deployment_ready "chess-app" "chess-app"; then
                    log_error "Failed to wait for chess-app deployment"
                    return 1
                fi
            else
                log_error "Kubernetes cluster not accessible"
                return 1
            fi
            ;;
        "all")
            if ! restart_resource "compute"; then
                return 1
            fi
            if ! restart_resource "apps"; then
                return 1
            fi
            ;;
    esac
    
    log_info "$resource_type restarted successfully"
    return 0
}

# =============================================================================
# RESOURCE STATUS FUNCTIONS
# =============================================================================

status_resource() {
    local resource_type=$1
    log_info "Checking status of $resource_type for environment: $ENVIRONMENT"
    
    case $resource_type in
        "vpc")
            check_vpc_status "$ENVIRONMENT"
            ;;
        "storage")
            check_storage_status "$ENVIRONMENT"
            ;;
        "security")
            check_security_status "$ENVIRONMENT"
            ;;
        "compute")
            check_compute_status "$ENVIRONMENT"
            ;;
        "network")
            check_network_status "$ENVIRONMENT"
            ;;
        "apps")
            check_apps_status "$ENVIRONMENT"
            ;;
        "all")
            check_vpc_status "$ENVIRONMENT"
            check_storage_status "$ENVIRONMENT"
            check_security_status "$ENVIRONMENT"
            check_compute_status "$ENVIRONMENT"
            check_network_status "$ENVIRONMENT"
            check_apps_status "$ENVIRONMENT"
            ;;
    esac
}

# =============================================================================
# RESOURCE REMOVAL FUNCTIONS
# =============================================================================

remove_resource() {
    local resource_type=$1
    log_info "Removing $resource_type for environment: $ENVIRONMENT"
    
    # Confirm destructive operation
    local message="This will DELETE $resource_type resources for environment $ENVIRONMENT. Are you sure?"
    if ! confirm_action "$message" "N"; then
        log_info "Operation cancelled by user"
        return 0
    fi
    
    # Run Terraform destroy
    if ! run_terraform_operation "destroy" "$ENVIRONMENT" "$resource_type" "true"; then
        log_error "Failed to remove $resource_type"
        return 1
    fi
    
    log_info "$resource_type removed successfully"
    return 0
}

# =============================================================================
# HEALTH CHECK FUNCTIONS
# =============================================================================

health_check_resource() {
    local resource_type=$1
    log_info "Health check for $resource_type in environment: $ENVIRONMENT"
    
    case $resource_type in
        "compute")
            local cluster_name=$(get_cluster_name "$ENVIRONMENT")
            local cluster_status
            cluster_status=$(aws eks describe-cluster --name "$cluster_name" --query 'cluster.status' --output text 2>/dev/null || echo "NOT_FOUND")
            echo "EKS Cluster status: $cluster_status"
            
            if [[ "$cluster_status" == "ACTIVE" ]]; then
                log_info "EKS cluster is healthy"
            else
                log_error "EKS cluster is not healthy: $cluster_status"
                return 1
            fi
            ;;
        "apps")
            if kubectl cluster-info &>/dev/null; then
                local app_ready
                local app_desired
                app_ready=$(kubectl get deployment chess-app -n chess-app -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
                app_desired=$(kubectl get deployment chess-app -n chess-app -o jsonpath='{.spec.replicas}' 2>/dev/null || echo "0")
                echo "Application health: $app_ready/$app_desired replicas ready"
                
                if [[ "$app_ready" == "$app_desired" && "$app_ready" != "0" ]]; then
                    log_info "Applications are healthy"
                else
                    log_error "Applications are not healthy"
                    return 1
                fi
            else
                log_error "Kubernetes cluster not accessible"
                return 1
            fi
            ;;
        "all")
            if ! health_check_resource "compute"; then
                return 1
            fi
            if ! health_check_resource "apps"; then
                return 1
            fi
            ;;
    esac
    
    return 0
}

# =============================================================================
# MAIN FUNCTION
# =============================================================================

main() {
    # Parse command line options
    parse_options "$@"
    
    # Validate inputs
    validate_inputs
    
    # Load configuration
    if ! load_configuration "$ENVIRONMENT"; then
        log_error "Failed to load configuration for environment: $ENVIRONMENT"
        exit 1
    fi
    
    # Check prerequisites
    if ! check_prerequisites; then
        exit 1
    fi
    
    # Check AWS permissions
    check_aws_permissions
    
    # Set global variables for shared functions
    export ENVIRONMENT ACTION RESOURCE
    
    log_info "Starting resource management operation"
    log_info "Environment: $ENVIRONMENT"
    log_info "Action: $ACTION"
    log_info "Resource: $RESOURCE"
    log_info "Dry run: $DRY_RUN"
    log_info "Auto approve: $AUTO_APPROVE"
    
    # Execute requested action
    case $ACTION in
        "install")
            if [[ "$RESOURCE" == "all" ]]; then
                install_all_resources
            else
                install_resource "$RESOURCE"
            fi
            ;;
        "start")
            start_resource "$RESOURCE"
            ;;
        "status")
            status_resource "$RESOURCE"
            ;;
        "halt")
            halt_resource "$RESOURCE"
            ;;
        "restart")
            restart_resource "$RESOURCE"
            ;;
        "stop")
            halt_resource "$RESOURCE"
            ;;
        "remove"|"delete")
            remove_resource "$RESOURCE"
            ;;
        "health")
            health_check_resource "$RESOURCE"
            ;;
        *)
            log_error "Unknown action: $ACTION"
            show_usage
            exit 1
            ;;
    esac
    
    if [[ $? -eq 0 ]]; then
        log_info "Operation completed successfully"
        exit 0
    else
        log_error "Operation failed"
        exit 1
    fi
}

# Execute main function with all arguments
main "$@"