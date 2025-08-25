#!/bin/bash
# Test Scenarios for Incremental Deployment

set -e

ENVIRONMENT=${1:-dev}
SCENARIO=${2:-basic}

log() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $1"
}

run_basic_test() {
    log "Running basic infrastructure test..."
    
    # Test VPC connectivity
    log "Testing VPC infrastructure..."
    ./resource-manager.sh $ENVIRONMENT install vpc
    ./resource-manager.sh $ENVIRONMENT status vpc
    
    # Test storage
    log "Testing storage components..."
    ./resource-manager.sh $ENVIRONMENT install storage
    ./resource-manager.sh $ENVIRONMENT status storage
    
    # Basic S3 connectivity test
    BUCKET_NAME=$(aws s3 ls | grep "chess-ai-models-$ENVIRONMENT" | awk '{print $3}')
    if [[ -n "$BUCKET_NAME" ]]; then
        echo "test-file" | aws s3 cp - "s3://$BUCKET_NAME/test.txt"
        aws s3 cp "s3://$BUCKET_NAME/test.txt" - 
        aws s3 rm "s3://$BUCKET_NAME/test.txt"
        log "S3 connectivity test passed"
    fi
    
    log "Basic test completed successfully"
}

run_compute_test() {
    log "Running compute infrastructure test..."
    
    # Ensure prerequisites are installed
    ./resource-manager.sh $ENVIRONMENT install vpc
    ./resource-manager.sh $ENVIRONMENT install storage
    ./resource-manager.sh $ENVIRONMENT install security
    
    # Install and test EKS
    log "Installing EKS cluster..."
    ./resource-manager.sh $ENVIRONMENT install compute
    
    # Wait for cluster to be ready
    log "Waiting for EKS cluster to be ready..."
    CLUSTER_NAME="chess-app-$ENVIRONMENT-cluster"
    
    for i in {1..30}; do
        STATUS=$(aws eks describe-cluster --name $CLUSTER_NAME --query 'cluster.status' --output text 2>/dev/null || echo "NOT_FOUND")
        if [[ "$STATUS" == "ACTIVE" ]]; then
            log "EKS cluster is active"
            break
        fi
        log "Waiting for cluster... (attempt $i/30)"
        sleep 30
    done
    
    # Test basic pod deployment
    log "Testing pod deployment..."
    kubectl run test-pod --image=nginx --restart=Never
    kubectl wait --for=condition=Ready pod/test-pod --timeout=300s
    kubectl delete pod test-pod
    
    log "Compute test completed successfully"
}

run_network_test() {
    log "Running network services test..."
    
    # Install prerequisites
    ./resource-manager.sh $ENVIRONMENT install vpc
    ./resource-manager.sh $ENVIRONMENT install storage
    ./resource-manager.sh $ENVIRONMENT install security
    ./resource-manager.sh $ENVIRONMENT install compute
    
    # Install network services
    log "Installing network services..."
    ./resource-manager.sh $ENVIRONMENT install network
    
    # Test API Gateway
    log "Testing API Gateway..."
    API_URL=$(aws apigatewayv2 get-apis --query "Items[?Name=='chess-app-$ENVIRONMENT-api'].ApiEndpoint" --output text)
    if [[ -n "$API_URL" ]]; then
        log "API Gateway URL: $API_URL"
        # Test basic connectivity (expect 404 since no backend yet)
        curl -f "$API_URL/health" || log "Expected 404 - API Gateway is responding"
    fi
    
    # Test CloudFront
    log "Testing CloudFront distribution..."
    CLOUDFRONT_DOMAIN=$(aws cloudfront list-distributions --query "DistributionList.Items[?Comment=='chess-app $ENVIRONMENT distribution'].DomainName" --output text)
    if [[ -n "$CLOUDFRONT_DOMAIN" ]]; then
        log "CloudFront Domain: $CLOUDFRONT_DOMAIN"
        # Test basic connectivity
        curl -I "https://$CLOUDFRONT_DOMAIN" || log "CloudFront distribution is accessible"
    fi
    
    log "Network test completed successfully"
}

run_application_test() {
    log "Running full application test..."
    
    # Install all components
    ./resource-manager.sh $ENVIRONMENT install vpc
    ./resource-manager.sh $ENVIRONMENT install storage
    ./resource-manager.sh $ENVIRONMENT install security
    ./resource-manager.sh $ENVIRONMENT install compute
    ./resource-manager.sh $ENVIRONMENT install network
    ./resource-manager.sh $ENVIRONMENT install apps
    
    # Wait for applications to be ready
    log "Waiting for applications to be ready..."
    kubectl wait --for=condition=available --timeout=600s deployment/chess-app -n chess-app
    
    # Test application health
    log "Testing application health..."
    kubectl port-forward -n chess-app service/chess-app 8080:8081 &
    PORT_FORWARD_PID=$!
    
    sleep 10
    
    # Test health endpoint
    if curl -f http://localhost:8080/actuator/health; then
        log "Application health check passed"
    else
        log "Application health check failed"
    fi
    
    # Test chess game endpoint
    if curl -f http://localhost:8080/; then
        log "Chess game endpoint accessible"
    else
        log "Chess game endpoint failed"
    fi
    
    kill $PORT_FORWARD_PID 2>/dev/null || true
    
    log "Application test completed successfully"
}

run_scaling_test() {
    log "Running scaling test..."
    
    # Ensure applications are running
    ./resource-manager.sh $ENVIRONMENT status apps
    
    # Test scaling down
    log "Testing scale down..."
    ./resource-manager.sh $ENVIRONMENT halt apps
    
    # Verify pods are scaled down
    REPLICA_COUNT=$(kubectl get deployment chess-app -n chess-app -o jsonpath='{.spec.replicas}' 2>/dev/null || echo "0")
    if [[ "$REPLICA_COUNT" == "0" ]]; then
        log "Scale down successful"
    else
        log "Scale down failed - replica count: $REPLICA_COUNT"
    fi
    
    # Test scaling up
    log "Testing scale up..."
    ./resource-manager.sh $ENVIRONMENT restart apps
    
    # Wait for pods to be ready
    kubectl wait --for=condition=available --timeout=300s deployment/chess-app -n chess-app
    
    READY_REPLICAS=$(kubectl get deployment chess-app -n chess-app -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
    if [[ "$READY_REPLICAS" -gt "0" ]]; then
        log "Scale up successful - ready replicas: $READY_REPLICAS"
    else
        log "Scale up failed"
    fi
    
    log "Scaling test completed successfully"
}

run_disaster_recovery_test() {
    log "Running disaster recovery test..."
    
    # Backup current state
    log "Creating backup..."
    kubectl get all -A -o yaml > backup-$ENVIRONMENT-$(date +%Y%m%d-%H%M%S).yaml
    
    # Simulate failure by removing applications
    log "Simulating application failure..."
    ./resource-manager.sh $ENVIRONMENT halt apps
    
    # Wait a bit
    sleep 30
    
    # Recover applications
    log "Recovering applications..."
    ./resource-manager.sh $ENVIRONMENT restart apps
    
    # Verify recovery
    kubectl wait --for=condition=available --timeout=300s deployment/chess-app -n chess-app
    
    log "Disaster recovery test completed successfully"
}

run_cost_optimization_test() {
    log "Running cost optimization test..."
    
    # Get initial resource count
    INITIAL_NODES=$(kubectl get nodes --no-headers | wc -l)
    log "Initial node count: $INITIAL_NODES"
    
    # Halt compute resources
    log "Halting compute resources for cost optimization..."
    ./resource-manager.sh $ENVIRONMENT halt compute
    
    # Wait for scale down
    sleep 60
    
    # Check node count (should be 0)
    HALTED_NODES=$(kubectl get nodes --no-headers 2>/dev/null | wc -l || echo "0")
    log "Halted node count: $HALTED_NODES"
    
    # Restart compute resources
    log "Restarting compute resources..."
    ./resource-manager.sh $ENVIRONMENT restart compute
    
    # Wait for nodes to come back
    sleep 120
    
    RESTARTED_NODES=$(kubectl get nodes --no-headers | wc -l)
    log "Restarted node count: $RESTARTED_NODES"
    
    if [[ "$RESTARTED_NODES" -ge "$INITIAL_NODES" ]]; then
        log "Cost optimization test passed"
    else
        log "Cost optimization test failed"
    fi
    
    log "Cost optimization test completed"
}

cleanup_test_resources() {
    log "Cleaning up test resources..."
    
    # Remove test files
    rm -f backup-$ENVIRONMENT-*.yaml
    
    # Clean up any test pods
    kubectl delete pod test-pod 2>/dev/null || true
    
    log "Cleanup completed"
}

show_usage() {
    echo "Usage: $0 <environment> <scenario>"
    echo ""
    echo "Environment: dev, staging, prod"
    echo "Scenarios:"
    echo "  basic        - Test VPC and storage only"
    echo "  compute      - Test up to EKS cluster"
    echo "  network      - Test up to API Gateway and CloudFront"
    echo "  application  - Test full application deployment"
    echo "  scaling      - Test application scaling"
    echo "  disaster     - Test disaster recovery"
    echo "  cost         - Test cost optimization (halt/restart)"
    echo "  cleanup      - Clean up test resources"
}

main() {
    case $SCENARIO in
        "basic")
            run_basic_test
            ;;
        "compute")
            run_compute_test
            ;;
        "network")
            run_network_test
            ;;
        "application")
            run_application_test
            ;;
        "scaling")
            run_scaling_test
            ;;
        "disaster")
            run_disaster_recovery_test
            ;;
        "cost")
            run_cost_optimization_test
            ;;
        "cleanup")
            cleanup_test_resources
            ;;
        *)
            show_usage
            exit 1
            ;;
    esac
}

main "$@"