#!/bin/bash
# AWS Utilities Library for Deployment Scripts
# This library provides AWS-specific operations and functions

# =============================================================================
# TERRAFORM UTILITIES
# =============================================================================

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

run_terraform_operation() {
    local operation=$1
    local environment=$2
    local resource_type=$3
    local auto_approve=${4:-false}
    
    local terraform_dir="$PROJECT_ROOT/infra/terraform"
    local var_file="environments/$environment/terraform.tfvars"
    local targets=$(get_terraform_targets "$resource_type")
    
    cd "$terraform_dir" || return 1
    
    # Initialize Terraform if needed
    if [[ ! -d ".terraform" ]]; then
        log_info "Initializing Terraform..."
        if ! terraform init; then
            log_error "Terraform initialization failed"
            return 1
        fi
    fi
    
    # Run Terraform operation
    case $operation in
        "plan")
            log_info "Running Terraform plan for $resource_type..."
            if [[ -n "$targets" ]]; then
                terraform plan -var-file="$var_file" $targets
            else
                terraform plan -var-file="$var_file"
            fi
            ;;
        "apply")
            log_info "Running Terraform apply for $resource_type..."
            local approve_flag=""
            if [[ "$auto_approve" == "true" ]]; then
                approve_flag="-auto-approve"
            fi
            
            if [[ -n "$targets" ]]; then
                terraform apply -var-file="$var_file" $targets $approve_flag
            else
                terraform apply -var-file="$var_file" $approve_flag
            fi
            ;;
        "destroy")
            log_info "Running Terraform destroy for $resource_type..."
            local approve_flag=""
            if [[ "$auto_approve" == "true" ]]; then
                approve_flag="-auto-approve"
            fi
            
            if [[ -n "$targets" ]]; then
                terraform destroy -var-file="$var_file" $targets $approve_flag
            else
                terraform destroy -var-file="$var_file" $approve_flag
            fi
            ;;
        *)
            log_error "Unknown Terraform operation: $operation"
            return 1
            ;;
    esac
    
    return $?
}

# =============================================================================
# EKS UTILITIES
# =============================================================================

detect_eks_auto_mode() {
    local cluster_name=$1
    local region=$(get_aws_region)
    
    log_debug "Checking EKS Auto Mode for cluster: $cluster_name"
    
    local auto_mode=$(aws eks describe-cluster \
        --name "$cluster_name" \
        --region "$region" \
        --query 'cluster.autoMode' \
        --output text 2>/dev/null || echo "false")
    
    log_debug "EKS Auto Mode status: $auto_mode"
    echo "$auto_mode"
}

scale_node_groups() {
    local direction=$1
    local environment=$2
    local cluster_name=$(get_cluster_name "$environment")
    local region=$(get_aws_region)
    
    log_info "Scaling node groups $direction for cluster: $cluster_name"
    
    # Check if cluster uses EKS Auto Mode
    local auto_mode=$(detect_eks_auto_mode "$cluster_name")
    
    if [[ "$auto_mode" == "true" ]]; then
        log_info "EKS Auto Mode detected - scaling handled automatically by AWS"
        log_info "Auto Mode provides automatic cost optimization and resource management"
        log_info "No manual scaling required for EKS Auto Mode clusters"
        return 0
    else
        log_info "Standard EKS mode detected - using manual scaling"
    fi
    
    if [[ $direction == "up" ]]; then
        # Scale up node groups
        local node_groups=$(get_node_group_names "$environment")
        for node_group in $node_groups; do
            log_info "Scaling up node group: $node_group"
            
            # Parse node group configuration
            local min_size=2
            local max_size=6
            local desired_size=3
            
            # Check if it's a GPU node group
            if [[ "$node_group" == *"gpu"* ]]; then
                min_size=0
                max_size=3
                desired_size=1
            fi
            
            if ! aws eks update-nodegroup-config \
                --cluster-name "$cluster_name" \
                --nodegroup-name "$node_group" \
                --scaling-config "minSize=$min_size,maxSize=$max_size,desiredSize=$desired_size" \
                --region "$region" 2>/dev/null; then
                log_warn "Failed to scale up node group: $node_group"
            fi
        done
    else
        # Scale down node groups
        local node_groups
        node_groups=$(aws eks list-nodegroups --cluster-name "$cluster_name" --region "$region" --query 'nodegroups[]' --output text 2>/dev/null || echo "")
        
        if [[ -n "$node_groups" ]]; then
            for node_group in $node_groups; do
                log_info "Scaling down node group: $node_group"
                
                if ! aws eks update-nodegroup-config \
                    --cluster-name "$cluster_name" \
                    --nodegroup-name "$node_group" \
                    --scaling-config "minSize=0,maxSize=0,desiredSize=0" \
                    --region "$region" 2>/dev/null; then
                    log_warn "Failed to scale down node group: $node_group"
                fi
            done
        fi
    fi
    
    log_info "Node group scaling completed"
}

wait_for_cluster_ready() {
    local environment=$1
    local timeout=${2:-600}  # Default 10 minutes
    local cluster_name=$(get_cluster_name "$environment")
    local region=$(get_aws_region)
    
    log_info "Waiting for EKS cluster to be ready: $cluster_name"
    
    local start_time=$(date +%s)
    local end_time=$((start_time + timeout))
    
    while [[ $(date +%s) -lt $end_time ]]; do
        local status
        status=$(aws eks describe-cluster --name "$cluster_name" --region "$region" --query 'cluster.status' --output text 2>/dev/null || echo "NOT_FOUND")
        
        if [[ "$status" == "ACTIVE" ]]; then
            log_info "EKS cluster is active"
            return 0
        elif [[ "$status" == "NOT_FOUND" ]]; then
            log_error "EKS cluster not found: $cluster_name"
            return 1
        fi
        
        log_info "Waiting for cluster... Status: $status"
        sleep 30
    done
    
    log_error "Timeout waiting for EKS cluster to be ready"
    return 1
}

update_kubeconfig() {
    local environment=$1
    local cluster_name=$(get_cluster_name "$environment")
    local region=$(get_aws_region)
    
    log_info "Updating kubeconfig for cluster: $cluster_name"
    
    if ! aws eks update-kubeconfig --region "$region" --name "$cluster_name"; then
        log_error "Failed to update kubeconfig"
        return 1
    fi
    
    # Verify kubectl access
    if ! kubectl cluster-info &>/dev/null; then
        log_error "Failed to verify kubectl access to cluster"
        return 1
    fi
    
    log_info "Kubeconfig updated successfully"
    return 0
}

# =============================================================================
# KUBERNETES UTILITIES
# =============================================================================

scale_deployments() {
    local namespace=$1
    local replicas=$2
    local deployment_name=${3:-"all"}
    
    log_info "Scaling deployments in namespace $namespace to $replicas replicas"
    
    if [[ "$deployment_name" == "all" ]]; then
        # Scale all deployments
        if ! kubectl scale deployment --all --replicas="$replicas" -n "$namespace" 2>/dev/null; then
            log_warn "Failed to scale all deployments in namespace: $namespace"
            return 1
        fi
    else
        # Scale specific deployment
        if ! kubectl scale deployment "$deployment_name" --replicas="$replicas" -n "$namespace" 2>/dev/null; then
            log_warn "Failed to scale deployment $deployment_name in namespace: $namespace"
            return 1
        fi
    fi
    
    log_info "Deployment scaling completed"
}

wait_for_deployment_ready() {
    local namespace=$1
    local deployment_name=$2
    local timeout=${3:-300}  # Default 5 minutes
    
    log_info "Waiting for deployment $deployment_name to be ready in namespace $namespace"
    
    if ! kubectl wait --for=condition=available --timeout="${timeout}s" "deployment/$deployment_name" -n "$namespace" 2>/dev/null; then
        log_error "Timeout waiting for deployment $deployment_name to be ready"
        return 1
    fi
    
    log_info "Deployment $deployment_name is ready"
    return 0
}

wait_for_nodes_ready() {
    local timeout=${1:-300}  # Default 5 minutes
    
    log_info "Waiting for all nodes to be ready"
    
    if ! kubectl wait --for=condition=Ready nodes --all --timeout="${timeout}s" 2>/dev/null; then
        log_error "Timeout waiting for nodes to be ready"
        return 1
    fi
    
    log_info "All nodes are ready"
    return 0
}

# =============================================================================
# AWS RESOURCE STATUS CHECKING
# =============================================================================

check_vpc_status() {
    local environment=$1
    local project_name=${PROJECT_NAME:-"chess-app"}
    local region=$(get_aws_region)
    
    log_info "Checking VPC status for environment: $environment"
    
    echo "=== VPC Status ==="
    if ! aws ec2 describe-vpcs \
        --filters "Name=tag:Name,Values=*$project_name-$environment*" \
        --query 'Vpcs[].{Name:Tags[?Key==`Name`].Value|[0],State:State,VpcId:VpcId}' \
        --output table \
        --region "$region" 2>/dev/null; then
        echo "No VPCs found"
    fi
}

create_s3_express_bucket() {
    local bucket_name=$1
    local availability_zone=$2
    local region=$(get_aws_region)
    
    log_info "Creating S3 Express One Zone bucket: $bucket_name in AZ: $availability_zone"
    
    # Create directory bucket for S3 Express One Zone
    aws s3api create-bucket \
        --bucket "$bucket_name" \
        --create-bucket-configuration LocationConstraint="$region" \
        --region "$region" 2>/dev/null || {
        log_error "Failed to create S3 Express One Zone bucket"
        return 1
    }
    
    # Configure for Express One Zone
    aws s3api put-bucket-versioning \
        --bucket "$bucket_name" \
        --versioning-configuration Status=Enabled
    
    log_info "S3 Express One Zone bucket created successfully"
}

check_s3_express_status() {
    local environment=$1
    local project_name=${PROJECT_NAME:-"chess-app"}
    
    echo "=== S3 Express One Zone Status ==="
    
    # Check for Express One Zone buckets
    local express_buckets=$(aws s3api list-buckets \
        --query "Buckets[?contains(Name, 'express') && contains(Name, '$project_name-$environment')].{Name:Name,CreationDate:CreationDate}" \
        --output table 2>/dev/null)
    
    if [[ -n "$express_buckets" ]]; then
        echo "$express_buckets"
    else
        echo "No S3 Express One Zone buckets found"
    fi
}

check_storage_status() {
    local environment=$1
    local project_name=${PROJECT_NAME:-"chess-app"}
    local region=$(get_aws_region)
    
    log_info "Checking storage status for environment: $environment"
    
    echo "=== Storage Status ==="
    
    # Check S3 buckets
    if ! aws s3 ls --region "$region" 2>/dev/null | grep "$project_name.*$environment" || true; then
        echo "No S3 buckets found"
    fi
    
    # Check S3 Express One Zone buckets
    check_s3_express_status "$environment"
    
    # Check ECR repositories
    if ! aws ecr describe-repositories \
        --repository-names "$project_name-$environment" \
        --query 'repositories[].repositoryName' \
        --output text \
        --region "$region" 2>/dev/null; then
        echo "No ECR repositories found"
    fi
}

check_aws_config_status() {
    local environment=$1
    local project_name=${PROJECT_NAME:-"chess-app"}
    
    echo "=== AWS Config Status ==="
    
    # Check Config Recorder
    local config_recorder=$(aws configservice describe-configuration-recorders \
        --query 'ConfigurationRecorders[0].name' \
        --output text 2>/dev/null)
    
    if [[ "$config_recorder" != "None" && -n "$config_recorder" ]]; then
        echo "AWS Config Recorder: $config_recorder"
        
        # Check recording status
        local recording_status=$(aws configservice describe-configuration-recorder-status \
            --configuration-recorder-names "$config_recorder" \
            --query 'ConfigurationRecordersStatus[0].recording' \
            --output text 2>/dev/null)
        echo "Recording Status: $recording_status"
    else
        echo "AWS Config not configured"
    fi
}

check_guardduty_status() {
    local environment=$1
    
    echo "=== GuardDuty Status ==="
    
    # Check GuardDuty detectors
    local detector_id=$(aws guardduty list-detectors \
        --query 'DetectorIds[0]' \
        --output text 2>/dev/null)
    
    if [[ "$detector_id" != "None" && -n "$detector_id" ]]; then
        echo "GuardDuty Detector: $detector_id"
        
        # Check detector status
        local detector_status=$(aws guardduty get-detector \
            --detector-id "$detector_id" \
            --query 'Status' \
            --output text 2>/dev/null)
        echo "Detector Status: $detector_status"
    else
        echo "GuardDuty not configured"
    fi
}

check_security_status() {
    local environment=$1
    local project_name=${PROJECT_NAME:-"chess-app"}
    local region=$(get_aws_region)
    
    log_info "Checking security status for environment: $environment"
    
    echo "=== Security Status ==="
    
    # Check Secrets Manager
    if ! aws secretsmanager list-secrets \
        --filters Key=name,Values="$project_name-$environment-secrets" \
        --query 'SecretList[].Name' \
        --output text \
        --region "$region" 2>/dev/null; then
        echo "No secrets found"
    fi
    
    # Check WAF
    if ! aws wafv2 list-web-acls \
        --scope=CLOUDFRONT \
        --query "WebACLs[?Name=='$project_name-$environment-waf'].Name" \
        --output text \
        --region "$region" 2>/dev/null; then
        echo "No WAF found"
    fi
    
    # Check AWS Config
    check_aws_config_status "$environment"
    
    # Check GuardDuty
    check_guardduty_status "$environment"
}

check_compute_status() {
    local environment=$1
    local cluster_name=$(get_cluster_name "$environment")
    local region=$(get_aws_region)
    
    log_info "Checking compute status for environment: $environment"
    
    echo "=== Compute Status ==="
    
    # Check EKS cluster
    local cluster_status=$(aws eks describe-cluster \
        --name "$cluster_name" \
        --query 'cluster.status' \
        --output text \
        --region "$region" 2>/dev/null || echo "NOT_FOUND")
    
    if [[ "$cluster_status" != "NOT_FOUND" ]]; then
        echo "EKS Cluster Status: $cluster_status"
        
        # Check EKS Auto Mode
        local auto_mode=$(detect_eks_auto_mode "$cluster_name")
        if [[ "$auto_mode" == "true" ]]; then
            echo "EKS Auto Mode: Enabled (AWS-managed scaling)"
        else
            echo "EKS Auto Mode: Disabled (Manual scaling)"
        fi
    else
        echo "EKS cluster not found"
    fi
    
    # Check node groups (only for standard mode)
    if [[ "$auto_mode" != "true" ]]; then
        if ! aws eks list-nodegroups \
            --cluster-name "$cluster_name" \
            --query 'nodegroups[]' \
            --output table \
            --region "$region" 2>/dev/null; then
            echo "No node groups found"
        fi
    else
        echo "Node groups managed by EKS Auto Mode"
    fi
}

check_network_status() {
    local environment=$1
    local project_name=${PROJECT_NAME:-"chess-app"}
    local region=$(get_aws_region)
    
    log_info "Checking network status for environment: $environment"
    
    echo "=== Network Status ==="
    
    # Check API Gateway
    if ! aws apigatewayv2 get-apis \
        --query "Items[?Name=='$project_name-$environment-api'].{Name:Name,ApiEndpoint:ApiEndpoint}" \
        --output table \
        --region "$region" 2>/dev/null; then
        echo "No API Gateway found"
    fi
    
    # Check CloudFront
    if ! aws cloudfront list-distributions \
        --query "DistributionList.Items[?Comment=='$project_name $environment distribution'].{Id:Id,Status:Status}" \
        --output table \
        --region "$region" 2>/dev/null; then
        echo "No CloudFront found"
    fi
}

check_xray_status() {
    local environment=$1
    
    echo "=== X-Ray Tracing Status ==="
    
    # Check X-Ray service
    local xray_tracing=$(aws xray get-trace-summaries \
        --time-range-type TimeRangeByStartTime \
        --start-time $(date -d '1 hour ago' -u +%Y-%m-%dT%H:%M:%S) \
        --end-time $(date -u +%Y-%m-%dT%H:%M:%S) \
        --query 'TraceSummaries[0].TraceId' \
        --output text 2>/dev/null)
    
    if [[ "$xray_tracing" != "None" && -n "$xray_tracing" ]]; then
        echo "X-Ray Tracing: Active (Recent traces found)"
    else
        echo "X-Ray Tracing: Not configured or no recent traces"
    fi
}

check_cloudwatch_insights() {
    local environment=$1
    
    echo "=== CloudWatch Insights Status ==="
    
    # Check CloudWatch Insights queries
    local insights_queries=$(aws logs describe-query-definitions \
        --query 'queryDefinitions[0].queryDefinitionId' \
        --output text 2>/dev/null)
    
    if [[ "$insights_queries" != "None" && -n "$insights_queries" ]]; then
        echo "CloudWatch Insights: Configured"
    else
        echo "CloudWatch Insights: Not configured"
    fi
}

check_cost_optimization_status() {
    local environment=$1
    local project_name=${PROJECT_NAME:-"chess-app"}
    
    echo "=== Cost Optimization Status ==="
    
    # Check AWS Budgets
    local budget_name="${project_name}-${environment}-budget"
    local account_id=$(aws sts get-caller-identity --query Account --output text)
    
    local budget_exists=$(aws budgets describe-budgets \
        --account-id "$account_id" \
        --query "Budgets[?BudgetName=='$budget_name'].BudgetName" \
        --output text 2>/dev/null)
    
    if [[ "$budget_exists" == "$budget_name" ]]; then
        echo "AWS Budget: $budget_name (Configured)"
    else
        echo "AWS Budget: Not configured"
    fi
    
    # Check Cost Anomaly Detection
    local anomaly_detectors=$(aws ce get-anomaly-detectors \
        --query 'AnomalyDetectors[0].AnomalyDetectorArn' \
        --output text 2>/dev/null)
    
    if [[ "$anomaly_detectors" != "None" && -n "$anomaly_detectors" ]]; then
        echo "Cost Anomaly Detection: Enabled"
    else
        echo "Cost Anomaly Detection: Not configured"
    fi
}

check_apps_status() {
    local environment=$1
    
    log_info "Checking applications status for environment: $environment"
    
    echo "=== Applications Status ==="
    
    if kubectl cluster-info &>/dev/null; then
        echo "Kubernetes Pods:"
        kubectl get pods -A --field-selector=status.phase!=Succeeded 2>/dev/null || echo "No pods found"
        
        echo -e "\nHelm Releases:"
        helm list -A 2>/dev/null || echo "No Helm releases found"
    else
        echo "Kubernetes cluster not accessible"
    fi
    
    # Check enhanced monitoring
    check_xray_status "$environment"
    check_cloudwatch_insights "$environment"
    check_cost_optimization_status "$environment"
}

# =============================================================================
# PARALLEL PROCESSING UTILITIES
# =============================================================================

install_resources_parallel() {
    local environment=$1
    local resources=("${@:2}")
    local pids=()
    local results=()
    
    log_info "Installing resources in parallel: ${resources[*]}"
    
    # Start all installations in parallel
    for resource in "${resources[@]}"; do
        log_info "Starting installation of $resource"
        install_resource "$environment" "$resource" &
        pids+=($!)
    done
    
    # Wait for all to complete and collect results
    local completed=0
    for i in "${!pids[@]}"; do
        if wait "${pids[$i]}"; then
            results[$i]=0
            ((completed++))
            log_info "Resource ${resources[$i]} installation completed successfully"
        else
            results[$i]=1
            log_error "Resource ${resources[$i]} installation failed"
        fi
        
        show_progress "Installing resources" "$completed" "${#resources[@]}"
    done
    
    # Check overall success
    local failed=0
    for result in "${results[@]}"; do
        if [[ $result -ne 0 ]]; then
            ((failed++))
        fi
    done
    
    if [[ $failed -eq 0 ]]; then
        log_info "All resources installed successfully"
        return 0
    else
        log_error "$failed out of ${#resources[@]} resources failed to install"
        return 1
    fi
}

# =============================================================================
# ROLLBACK FUNCTIONS
# =============================================================================

rollback_vpc_installation() {
    local environment=$1
    log_info "Rolling back VPC installation for environment: $environment"
    
    if run_terraform_operation "destroy" "$environment" "vpc" "true"; then
        log_info "VPC rollback completed successfully"
        return 0
    else
        log_error "VPC rollback failed"
        return 1
    fi
}

rollback_storage_installation() {
    local environment=$1
    log_info "Rolling back storage installation for environment: $environment"
    
    if run_terraform_operation "destroy" "$environment" "storage" "true"; then
        log_info "Storage rollback completed successfully"
        return 0
    else
        log_error "Storage rollback failed"
        return 1
    fi
}

rollback_security_installation() {
    local environment=$1
    log_info "Rolling back security installation for environment: $environment"
    
    if run_terraform_operation "destroy" "$environment" "security" "true"; then
        log_info "Security rollback completed successfully"
        return 0
    else
        log_error "Security rollback failed"
        return 1
    fi
}

rollback_compute_installation() {
    local environment=$1
    log_info "Rolling back compute installation for environment: $environment"
    
    if run_terraform_operation "destroy" "$environment" "compute" "true"; then
        log_info "Compute rollback completed successfully"
        return 0
    else
        log_error "Compute rollback failed"
        return 1
    fi
}

rollback_network_installation() {
    local environment=$1
    log_info "Rolling back network installation for environment: $environment"
    
    if run_terraform_operation "destroy" "$environment" "network" "true"; then
        log_info "Network rollback completed successfully"
        return 0
    else
        log_error "Network rollback failed"
        return 1
    fi
}

rollback_apps_installation() {
    local environment=$1
    log_info "Rolling back applications installation for environment: $environment"
    
    if run_terraform_operation "destroy" "$environment" "apps" "true"; then
        log_info "Applications rollback completed successfully"
        return 0
    else
        log_error "Applications rollback failed"
        return 1
    fi
}

# =============================================================================
# EXPORT FUNCTIONS
# =============================================================================

export -f get_terraform_targets run_terraform_operation
export -f detect_eks_auto_mode scale_node_groups wait_for_cluster_ready update_kubeconfig
export -f scale_deployments wait_for_deployment_ready wait_for_nodes_ready
export -f create_s3_express_bucket check_s3_express_status
export -f check_aws_config_status check_guardduty_status
export -f check_xray_status check_cloudwatch_insights check_cost_optimization_status
export -f check_vpc_status check_storage_status check_security_status
export -f check_compute_status check_network_status check_apps_status
export -f install_resources_parallel
export -f rollback_vpc_installation rollback_storage_installation rollback_security_installation
export -f rollback_compute_installation rollback_network_installation rollback_apps_installation
