#!/bin/bash
# Common Functions Library for AWS Deployment Scripts
# This library provides shared functionality across all bash scripts

# Global variables
readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
readonly CONFIG_DIR="$SCRIPT_DIR/../config"

# Color codes for output
readonly RED='\033[0;31m'
readonly GREEN='\033[0;32m'
readonly YELLOW='\033[1;33m'
readonly BLUE='\033[0;34m'
readonly PURPLE='\033[0;35m'
readonly CYAN='\033[0;36m'
readonly NC='\033[0m' # No Color

# Log levels
readonly LOG_LEVEL_DEBUG=0
readonly LOG_LEVEL_INFO=1
readonly LOG_LEVEL_WARN=2
readonly LOG_LEVEL_ERROR=3

# Default log level
LOG_LEVEL=${LOG_LEVEL:-$LOG_LEVEL_INFO}

# Error tracking
ERROR_OCCURRED=false
ROLLBACK_STACK=()

# =============================================================================
# LOGGING FUNCTIONS
# =============================================================================

log() {
    local level=$1
    local message=$2
    local timestamp=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
    
    # Determine if we should log this level
    local current_level_num
    case $level in
        "DEBUG") current_level_num=$LOG_LEVEL_DEBUG ;;
        "INFO") current_level_num=$LOG_LEVEL_INFO ;;
        "WARN") current_level_num=$LOG_LEVEL_WARN ;;
        "ERROR") current_level_num=$LOG_LEVEL_ERROR ;;
        *) current_level_num=$LOG_LEVEL_INFO ;;
    esac
    
    if [[ $current_level_num -ge $LOG_LEVEL ]]; then
        case $level in
            "DEBUG") echo -e "${CYAN}[DEBUG]${NC} $message" ;;
            "INFO") echo -e "${BLUE}[INFO]${NC} $message" ;;
            "WARN") echo -e "${YELLOW}[WARN]${NC} $message" ;;
            "ERROR") echo -e "${RED}[ERROR]${NC} $message" ;;
            *) echo -e "[$level] $message" ;;
        esac
        
        # Also output structured JSON for automation
        if [[ "$LOG_JSON" == "true" ]]; then
            echo "{\"timestamp\":\"$timestamp\",\"level\":\"$level\",\"message\":\"$message\",\"environment\":\"${ENVIRONMENT:-unknown}\",\"action\":\"${ACTION:-unknown}\"}" >&2
        fi
    fi
}

log_debug() { log "DEBUG" "$1"; }
log_info() { log "INFO" "$1"; }
log_warn() { log "WARN" "$1"; }
log_error() { log "ERROR" "$1"; }

# =============================================================================
# ERROR HANDLING FUNCTIONS
# =============================================================================

handle_error() {
    local exit_code=$?
    local line_number=$1
    local command=$2
    
    ERROR_OCCURRED=true
    
    log_error "Error on line $line_number: Command '$command' failed with exit code $exit_code"
    
    # Implement rollback logic
    if [[ ${#ROLLBACK_STACK[@]} -gt 0 ]]; then
        log_warn "Initiating rollback of ${#ROLLBACK_STACK[@]} operations..."
        rollback_operations
    fi
    
    # Show error context
    log_error "Stack trace:"
    local frame=0
    while caller $frame; do
        ((frame++))
    done
    
    exit $exit_code
}

rollback_operations() {
    log_info "Starting rollback operations..."
    
    # Execute rollback operations in reverse order
    for ((i=${#ROLLBACK_STACK[@]}-1; i>=0; i--)); do
        local rollback_func="${ROLLBACK_STACK[$i]}"
        log_info "Rolling back: $rollback_func"
        
        if command -v "$rollback_func" >/dev/null 2>&1; then
            if ! $rollback_func; then
                log_error "Rollback operation '$rollback_func' failed"
            fi
        else
            log_warn "Rollback function '$rollback_func' not found"
        fi
    done
    
    # Clear rollback stack
    ROLLBACK_STACK=()
    log_info "Rollback operations completed"
}

add_rollback_operation() {
    local operation=$1
    ROLLBACK_STACK+=("$operation")
    log_debug "Added rollback operation: $operation"
}

# =============================================================================
# VALIDATION FUNCTIONS
# =============================================================================

validate_environment() {
    local env=$1
    case $env in
        dev|staging|prod) return 0 ;;
        *) 
            log_error "Invalid environment: $env. Must be dev, staging, or prod."
            return 1
            ;;
    esac
}

validate_action() {
    local action=$1
    case $action in
        install|start|status|halt|restart|stop|remove|delete|health) return 0 ;;
        *) 
            log_error "Invalid action: $action. Must be one of: install, start, status, halt, restart, stop, remove, delete, health"
            return 1
            ;;
    esac
}

validate_resource() {
    local resource=$1
    case $resource in
        all|vpc|storage|security|compute|network|apps) return 0 ;;
        *) 
            log_error "Invalid resource: $resource. Must be one of: all, vpc, storage, security, compute, network, apps"
            return 1
            ;;
    esac
}

# =============================================================================
# PREREQUISITE CHECKING
# =============================================================================

check_prerequisites() {
    log_info "Checking prerequisites..."
    
    local missing_tools=()
    
    # Check required tools
    local required_tools=("aws" "terraform")
    if [[ "$RESOURCE" == "apps" || "$RESOURCE" == "all" ]]; then
        required_tools+=("kubectl" "helm")
    fi
    
    for tool in "${required_tools[@]}"; do
        if ! command -v "$tool" >/dev/null 2>&1; then
            missing_tools+=("$tool")
        fi
    done
    
    if [[ ${#missing_tools[@]} -gt 0 ]]; then
        log_error "Missing required tools: ${missing_tools[*]}"
        log_error "Please install the missing tools and try again."
        return 1
    fi
    
    # Check AWS CLI configuration
    if ! aws sts get-caller-identity >/dev/null 2>&1; then
        log_error "AWS CLI not configured or insufficient permissions"
        log_error "Please run 'aws configure' and ensure you have appropriate permissions"
        return 1
    fi
    
    log_info "All prerequisites met"
    return 0
}

check_aws_permissions() {
    log_info "Checking AWS permissions..."
    
    local required_permissions=("eks:*" "ec2:*" "s3:*" "iam:*" "apigateway:*" "cloudfront:*" "wafv2:*" "secretsmanager:*")
    local missing_permissions=()
    
    for permission in "${required_permissions[@]}"; do
        # Test basic permissions
        if ! aws iam get-user >/dev/null 2>&1; then
            missing_permissions+=("$permission")
        fi
    done
    
    if [[ ${#missing_permissions[@]} -gt 0 ]]; then
        log_warn "Some AWS permissions may be insufficient: ${missing_permissions[*]}"
        log_warn "This may cause operations to fail"
    else
        log_info "AWS permissions verified"
    fi
}

# =============================================================================
# CONFIGURATION MANAGEMENT
# =============================================================================

load_configuration() {
    local environment=$1
    local config_file="$CONFIG_DIR/${environment}.conf"
    
    if [[ ! -f "$config_file" ]]; then
        log_warn "Configuration file not found: $config_file"
        log_info "Using default configuration"
        return 0
    fi
    
    log_info "Loading configuration from: $config_file"
    
    # Source the configuration file
    if ! source "$config_file"; then
        log_error "Failed to load configuration file: $config_file"
        return 1
    fi
    
    # Validate configuration
    validate_configuration
    
    log_info "Configuration loaded successfully"
    return 0
}

validate_configuration() {
    local required_vars=("PROJECT_NAME" "AWS_REGION")
    
    for var in "${required_vars[@]}"; do
        if [[ -z "${!var}" ]]; then
            log_error "Required configuration variable not set: $var"
            return 1
        fi
    done
    
    # Set defaults for optional variables
    PROJECT_NAME=${PROJECT_NAME:-"chess-app"}
    AWS_REGION=${AWS_REGION:-"us-west-2"}
    EKS_NODE_GROUPS=${EKS_NODE_GROUPS:-"general:2-6:3,gpu:0-3:1"}
    
    log_debug "Configuration validated: PROJECT_NAME=$PROJECT_NAME, AWS_REGION=$AWS_REGION"
    return 0
}

# =============================================================================
# AWS UTILITY FUNCTIONS
# =============================================================================

get_aws_region() {
    # Try to get region from configuration or AWS CLI
    local region="${AWS_REGION:-}"
    
    if [[ -z "$region" ]]; then
        region=$(aws configure get region 2>/dev/null || echo "us-west-2")
    fi
    
    echo "$region"
}

get_cluster_name() {
    local environment=$1
    local project_name=${PROJECT_NAME:-"chess-app"}
    echo "${project_name}-${environment}-cluster"
}

get_node_group_names() {
    local environment=$1
    local project_name=${PROJECT_NAME:-"chess-app"}
    echo "${project_name}-${environment}-general ${project_name}-${environment}-gpu"
}

# =============================================================================
# PROGRESS TRACKING
# =============================================================================

show_progress() {
    local message=$1
    local current=$2
    local total=$3
    
    if [[ -z "$current" || -z "$total" ]]; then
        log_info "$message"
        return
    fi
    
    local percentage=$((current * 100 / total))
    local bar_length=30
    local filled_length=$((bar_length * current / total))
    local bar=""
    
    for ((i=0; i<bar_length; i++)); do
        if [[ $i -lt $filled_length ]]; then
            bar+="█"
        else
            bar+="░"
        fi
    done
    
    printf "\r%s [%s] %d%% (%d/%d)" "$message" "$bar" "$percentage" "$current" "$total"
    
    if [[ $current -eq $total ]]; then
        echo "" # New line when complete
    fi
}

# =============================================================================
# UTILITY FUNCTIONS
# =============================================================================

confirm_action() {
    local message=$1
    local default=${2:-"N"}
    
    if [[ "$AUTO_APPROVE" == "true" ]]; then
        log_info "Auto-approve enabled, proceeding with: $message"
        return 0
    fi
    
    local prompt
    if [[ "$default" == "Y" ]]; then
        prompt="$message (Y/n): "
    else
        prompt="$message (y/N): "
    fi
    
    read -p "$prompt" -r confirmation
    
    case $confirmation in
        [Yy]|[Yy][Ee][Ss]) return 0 ;;
        [Nn]|[Nn][Oo]) return 1 ;;
        "") 
            if [[ "$default" == "Y" ]]; then
                return 0
            else
                return 1
            fi
            ;;
        *) return 1 ;;
    esac
}

is_dry_run() {
    [[ "$DRY_RUN" == "true" ]]
}

execute_command() {
    local command="$1"
    local description="${2:-"Executing command"}"
    
    if is_dry_run; then
        log_info "DRY RUN: $description"
        log_info "Would execute: $command"
        return 0
    fi
    
    log_info "$description"
    log_debug "Executing: $command"
    
    if eval "$command"; then
        log_info "Command completed successfully"
        return 0
    else
        log_error "Command failed: $command"
        return 1
    fi
}

# =============================================================================
# INITIALIZATION
# =============================================================================

# Set up error handling
trap 'handle_error ${LINENO} "$BASH_COMMAND"' ERR

# Export functions for use in other scripts
export -f log log_debug log_info log_warn log_error
export -f handle_error rollback_operations add_rollback_operation
export -f validate_environment validate_action validate_resource
export -f check_prerequisites check_aws_permissions
export -f load_configuration validate_configuration
export -f get_aws_region get_cluster_name get_node_group_names
export -f show_progress confirm_action is_dry_run execute_command
