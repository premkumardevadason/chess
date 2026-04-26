#!/bin/bash
# AWS Resource Lifecycle Management Script - REFACTORED VERSION
# This script provides simplified lifecycle management using shared libraries

set -euo pipefail

# Source shared libraries
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib/common-functions.sh"
source "$SCRIPT_DIR/lib/aws-utils.sh"

# Parse command line arguments
ENVIRONMENT=${1:-}
ACTION=${2:-}

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

show_usage() {
    cat << EOF
Usage: $0 <environment> <action> [options]

Environment: dev, staging, prod
Actions: start, status, halt, restart, stop, remove, health

Options:
  --dry-run    - Show what would be executed without making changes
  --auto-approve - Skip confirmation prompts
  --log-level  - Set log level (DEBUG, INFO, WARN, ERROR)
  --log-json   - Enable structured JSON logging

Examples:
  $0 dev start              # Start all resources
  $0 prod status            # Check resource status
  $0 dev halt               # Temporarily halt resources
  $0 prod restart           # Restart halted resources
  $0 dev stop               # Stop compute resources
  $0 prod remove            # Remove all resources
  $0 dev health             # Health check
  $0 dev start --dry-run    # Dry run of startup

Lifecycle Actions Explained:

🚀 START
  - Create and start all AWS resources
  - Runs Terraform apply to create infrastructure
  - Updates kubeconfig for EKS access
  - Scales node groups to desired capacity
  - Deploys applications via Helm

📊 STATUS
  - Check current status of all resources
  - Shows Terraform resource states
  - Displays EKS cluster status
  - Lists node groups and their status
  - Shows Kubernetes pods and Helm releases

⏸️ HALT
  - Temporarily halt resources to save costs
  - Scales node groups to 0 instances
  - Scales application deployments to 0 replicas
  - Keeps infrastructure intact
  - Cost savings: ~80% reduction

🔄 RESTART
  - Restart previously halted resources
  - Scales node groups back to desired capacity
  - Waits for nodes to be ready
  - Scales applications back to desired replicas
  - Waits for applications to be healthy

🛑 STOP
  - Stop compute resources but keep infrastructure
  - Terminates all EC2 instances in node groups
  - Keeps EKS cluster, VPCs, and other infrastructure
  - Applications become unavailable

🗑️ REMOVE/DELETE
  - Completely remove all AWS resources
  - Runs Terraform destroy to delete everything
  - Requires confirmation to prevent accidental deletion
  - Cannot be undone without full redeployment

🏥 HEALTH
  - Perform comprehensive health checks
  - Checks EKS cluster health
  - Verifies application pod readiness
  - Tests application endpoints
  - Reports overall system health

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
    
    case $ACTION in
        start|status|halt|restart|stop|remove|delete|health) ;;
        *) 
            log_error "Invalid action: $ACTION. Must be one of: start, status, halt, restart, stop, remove, delete, health"
            exit 1
            ;;
    esac
}

# =============================================================================
# LIFECYCLE FUNCTIONS
# =============================================================================

start_resources() {
    log_info "Starting AWS resources for environment: $ENVIRONMENT"
    
    # Add rollback operations
    add_rollback_operation "halt_resources $ENVIRONMENT"
    
    # Install all resources if they don't exist
    if ! run_terraform_operation "plan" "$ENVIRONMENT" "all" "false"; then
        log_info "Resources don't exist, installing them first..."
        if ! run_terraform_operation "apply" "$ENVIRONMENT" "all" "true"; then
            log_error "Failed to install resources"
            return 1
        fi
    fi
    
    # Update kubeconfig
    if ! update_kubeconfig "$ENVIRONMENT"; then
        log_error "Failed to update kubeconfig"
        return 1
    fi
    
    # Scale up node groups
    if ! scale_node_groups "up" "$ENVIRONMENT"; then
        log_error "Failed to scale up node groups"
        return 1
    fi
    
    # Wait for cluster to be ready
    if ! wait_for_cluster_ready "$ENVIRONMENT"; then
        log_error "Failed to wait for cluster readiness"
        return 1
    fi
    
    # Scale up applications
    if kubectl cluster-info &>/dev/null; then
        if ! scale_deployments "chess-app" 3; then
            log_error "Failed to scale up chess-app deployments"
            return 1
        fi
        
        if ! scale_deployments "monitoring" 1; then
            log_error "Failed to scale up monitoring deployments"
            return 1
        fi
        
        # Wait for deployments to be ready
        if ! wait_for_deployment_ready "chess-app" "chess-app"; then
            log_error "Failed to wait for chess-app deployment"
            return 1
        fi
    fi
    
    log_info "All resources started successfully"
    return 0
}

status_check() {
    log_info "Checking status of AWS resources for environment: $ENVIRONMENT"
    
    # Check all resource types
    check_vpc_status "$ENVIRONMENT"
    check_storage_status "$ENVIRONMENT"
    check_security_status "$ENVIRONMENT"
    check_compute_status "$ENVIRONMENT"
    check_network_status "$ENVIRONMENT"
    check_apps_status "$ENVIRONMENT"
    
    log_info "Status check completed"
    return 0
}

halt_resources() {
    log_info "Temporarily halting AWS resources for environment: $ENVIRONMENT"
    
    # Scale down applications first
    if kubectl cluster-info &>/dev/null; then
        if ! scale_deployments "chess-app" 0; then
            log_error "Failed to scale down chess-app deployments"
            return 1
        fi
        
        if ! scale_deployments "monitoring" 0; then
            log_error "Failed to scale down monitoring deployments"
            return 1
        fi
    fi
    
    # Scale down node groups
    if ! scale_node_groups "down" "$ENVIRONMENT"; then
        log_error "Failed to scale down node groups"
        return 1
    fi
    
    log_info "Resources halted successfully (cost savings: ~80%)"
    return 0
}

restart_resources() {
    log_info "Restarting AWS resources for environment: $ENVIRONMENT"
    
    # Scale up node groups
    if ! scale_node_groups "up" "$ENVIRONMENT"; then
        log_error "Failed to scale up node groups"
        return 1
    fi
    
    # Wait for cluster to be ready
    if ! wait_for_cluster_ready "$ENVIRONMENT"; then
        log_error "Failed to wait for cluster readiness"
        return 1
    fi
    
    # Wait for nodes to be ready
    if ! wait_for_nodes_ready; then
        log_error "Failed to wait for nodes readiness"
        return 1
    fi
    
    # Scale up applications
    if kubectl cluster-info &>/dev/null; then
        if ! scale_deployments "chess-app" 3; then
            log_error "Failed to scale up chess-app deployments"
            return 1
        fi
        
        if ! wait_for_deployment_ready "chess-app" "chess-app"; then
            log_error "Failed to wait for chess-app deployment"
            return 1
        fi
    fi
    
    log_info "Resources restarted successfully"
    return 0
}

stop_resources() {
    log_info "Stopping compute resources for environment: $ENVIRONMENT"
    
    # Halt resources first
    if ! halt_resources; then
        return 1
    fi
    
    # Terminate all EC2 instances in node groups
    local cluster_name=$(get_cluster_name "$ENVIRONMENT")
    local region=$(get_aws_region)
    local node_groups
    node_groups=$(aws eks list-nodegroups --cluster-name "$cluster_name" --region "$region" --query 'nodegroups[]' --output text 2>/dev/null || echo "")
    
    if [[ -n "$node_groups" ]]; then
        for node_group in $node_groups; do
            log_info "Terminating node group: $node_group"
            if ! aws eks update-nodegroup-config \
                --cluster-name "$cluster_name" \
                --nodegroup-name "$node_group" \
                --scaling-config "minSize=0,maxSize=0,desiredSize=0" \
                --region "$region" 2>/dev/null; then
                log_warn "Failed to terminate node group: $node_group"
            fi
        done
    fi
    
    log_info "Compute resources stopped successfully"
    return 0
}

remove_resources() {
    log_info "Removing ALL AWS resources for environment: $ENVIRONMENT"
    
    # Confirm destructive operation
    local message="This will DELETE ALL resources for environment $ENVIRONMENT. This action cannot be undone. Are you sure?"
    if ! confirm_action "$message" "N"; then
        log_info "Operation cancelled by user"
        return 0
    fi
    
    # Add rollback operations (though they may not be useful for complete removal)
    add_rollback_operation "start_resources $ENVIRONMENT"
    
    # Run Terraform destroy
    if ! run_terraform_operation "destroy" "$ENVIRONMENT" "all" "true"; then
        log_error "Failed to remove resources"
        return 1
    fi
    
    log_info "All resources removed successfully"
    return 0
}

health_check() {
    log_info "Performing health check for environment: $ENVIRONMENT"
    
    # Check compute health
    if ! health_check_resource "compute"; then
        return 1
    fi
    
    # Check applications health
    if ! health_check_resource "apps"; then
        return 1
    fi
    
    log_info "Health check completed successfully"
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
    export ENVIRONMENT ACTION
    
    log_info "Starting lifecycle management operation"
    log_info "Environment: $ENVIRONMENT"
    log_info "Action: $ACTION"
    log_info "Dry run: $DRY_RUN"
    log_info "Auto approve: $AUTO_APPROVE"
    
    # Execute requested action
    case $ACTION in
        "start")
            start_resources
            ;;
        "status")
            status_check
            ;;
        "halt")
            halt_resources
            ;;
        "restart")
            restart_resources
            ;;
        "stop")
            stop_resources
            ;;
        "remove"|"delete")
            remove_resources
            ;;
        "health")
            health_check
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