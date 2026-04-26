ace# AWS Deployment Scripts - Enhanced Version

This directory contains enhanced AWS deployment and management scripts that provide comprehensive infrastructure automation with enterprise-grade features.

## 🚀 **What's New in This Version**

### **Major Improvements**
- ✅ **Shared Libraries**: Eliminated code duplication with reusable function libraries
- ✅ **Enhanced Error Handling**: Comprehensive error handling with automatic rollback
- ✅ **Configuration Management**: Environment-specific configuration files
- ✅ **Structured Logging**: Multi-level logging with JSON output support
- ✅ **Input Validation**: Robust parameter validation and security checks
- ✅ **Progress Tracking**: Visual progress indicators for long operations
- ✅ **Parallel Processing**: Support for parallel resource deployment
- ✅ **Timeout Protection**: Built-in timeout handling for hanging operations
- ✅ **Dry-Run Mode**: Safe testing without making changes
- ✅ **Auto-Approval**: Skip confirmation prompts for automation

## 📁 **Script Structure**

```
infra/scripts/
├── lib/                          # Shared function libraries
│   ├── common-functions.sh      # Common bash functions
│   ├── common-functions.ps1     # Common PowerShell functions
│   └── aws-utils.sh            # AWS-specific utilities
├── config/                       # Environment configurations
│   ├── dev.conf                # Development environment
│   ├── staging.conf            # Staging environment
│   └── prod.conf               # Production environment
├── resource-manager.sh          # Granular resource management
├── resource-manager.ps1         # PowerShell resource manager
├── lifecycle-management.sh      # Simplified lifecycle management
├── resource-monitor.ps1         # Resource monitoring and alerting
├── test-scenarios.sh            # Comprehensive testing framework
├── deployment-order.md          # Deployment sequence guide
└── README.md                    # This documentation
```

## 🔧 **Core Scripts**

### **1. Resource Manager (resource-manager.sh/.ps1)**
**Purpose**: Granular AWS resource management with full lifecycle control

**Features**:
- Install, start, stop, remove individual resources
- Dependency-aware deployment (vpc → storage → security → compute → network → apps)
- Automatic rollback on failures
- Progress tracking and timeout protection
- Cross-platform support (Bash + PowerShell)

**Usage Examples**:
```bash
# Install resources incrementally
./resource-manager.sh dev install vpc
./resource-manager.sh dev install storage
./resource-manager.sh dev install security
./resource-manager.sh dev install compute
./resource-manager.sh dev install network
./resource-manager.sh dev install apps

# Check status
./resource-manager.sh dev status all

# Halt resources to save costs
./resource-manager.sh dev halt apps

# Remove resources
./resource-manager.sh dev remove all --auto-approve
```

### **2. Lifecycle Management (lifecycle-management.sh)**
**Purpose**: Simplified high-level lifecycle operations

**Features**:
- One-command operations (start, stop, restart, health)
- Automatic resource discovery and management
- Cost optimization with auto-halt features
- Comprehensive health monitoring

**Usage Examples**:
```bash
# Start all resources
./lifecycle-management.sh dev start

# Check health
./lifecycle-management.sh prod health

# Halt for cost savings
./lifecycle-management.sh dev halt

# Restart halted resources
./lifecycle-management.sh dev restart
```

### **3. Resource Monitor (resource-monitor.ps1)**
**Purpose**: Comprehensive monitoring, alerting, and cost analysis

**Features**:
- Real-time resource status monitoring
- Health diagnostics and alerting
- Performance metrics collection
- Cost analysis and optimization recommendations
- Continuous monitoring mode

**Usage Examples**:
```powershell
# Check resource status
.\resource-monitor.ps1 -Environment dev -Action status

# Health check
.\resource-monitor.ps1 -Environment prod -Action health -Resource compute

# Continuous monitoring
.\resource-monitor.ps1 -Environment staging -Action metrics -Continuous -Interval 60

# Cost analysis
.\resource-monitor.ps1 -Environment prod -Action cost -Detailed
```

### **4. Test Scenarios (test-scenarios.sh)**
**Purpose**: Comprehensive testing framework for deployments

**Features**:
- Infrastructure validation tests
- Application functionality tests
- Security and compliance tests
- Performance and scalability tests
- Disaster recovery testing
- Cost optimization validation

**Usage Examples**:
```bash
# Run all tests
./test-scenarios.sh --environment dev --test-scenarios all

# Test specific components
./test-scenarios.sh --environment staging --test-scenarios infrastructure applications

# Performance testing
./test-scenarios.sh --environment prod --test-scenarios performance --timeout 3600

# Dry run
./test-scenarios.sh --environment dev --test-scenarios all --dry-run
```

## ⚙️ **Configuration Management**

### **Environment Configuration Files**
Each environment has its own configuration file with specific settings:

**dev.conf** - Development environment
- Cost optimization enabled
- Auto-halt during off-hours
- Basic monitoring
- Fast deployment times

**staging.conf** - Staging environment
- Production-like settings
- Enhanced monitoring
- Cost controls
- Testing optimizations

**prod.conf** - Production environment
- High availability
- Advanced security
- Comprehensive monitoring
- No auto-halt (always on)

### **Configuration Variables**
```bash
# Project Configuration
PROJECT_NAME="chess-app"
PROJECT_DESCRIPTION="Chess Web Game & MCP Server"

# AWS Configuration
AWS_REGION="us-west-2"
AWS_PROFILE="production"

# EKS Configuration
EKS_CLUSTER_VERSION="1.28"
EKS_NODE_GROUPS="general:3-10:6,gpu:1-5:2"

# Performance Configuration
PARALLEL_DEPLOYMENT="true"
DEPLOYMENT_TIMEOUT="7200"
HEALTH_CHECK_TIMEOUT="900"
```

## 🛡️ **Security Features**

### **Input Validation**
- Environment validation (dev/staging/prod only)
- Action validation (install/start/status/halt/restart/stop/remove/health)
- Resource validation (vpc/storage/security/compute/network/apps)
- Parameter sanitization and validation

### **Permission Checking**
- AWS CLI configuration validation
- Required permission verification
- RBAC validation for Kubernetes operations
- Security group and IAM policy validation

### **Confirmation Prompts**
- Destructive operations require confirmation
- Auto-approval mode for automation
- Dry-run mode for safe testing
- Rollback confirmation for failed operations

## 📊 **Logging and Monitoring**

### **Log Levels**
- **DEBUG**: Detailed debugging information
- **INFO**: General information messages
- **WARN**: Warning messages
- **ERROR**: Error messages with context

### **Structured Logging**
```bash
# Enable JSON logging
export LOG_JSON=true

# Set log level
export LOG_LEVEL=DEBUG

# Example output
{"timestamp":"2024-01-15T10:30:00Z","level":"INFO","message":"Starting resource installation","environment":"dev","action":"install"}
```

### **Progress Tracking**
- Visual progress bars for long operations
- Percentage completion indicators
- Resource-by-resource status updates
- Timeout warnings and progress estimates

## 🔄 **Error Handling and Recovery**

### **Automatic Rollback**
- Failed operations trigger automatic rollback
- Rollback stack tracks operations in reverse order
- Graceful degradation on partial failures
- Comprehensive error context and stack traces

### **Retry Logic**
- Transient failure detection
- Exponential backoff for retries
- Maximum retry limits
- Success/failure tracking

### **Timeout Protection**
- Configurable timeouts for all operations
- Progress monitoring during long operations
- Automatic cancellation of hanging operations
- Resource cleanup on timeouts

## 🚀 **Performance Optimizations**

### **Parallel Processing**
- Independent resources deploy in parallel
- Configurable parallel execution
- Resource dependency management
- Progress tracking for parallel operations

### **Resource Optimization**
- Efficient Terraform targeting
- Minimal AWS API calls
- Resource state caching
- Optimized deployment sequences

## 🧪 **Testing and Validation**

### **Dry-Run Mode**
```bash
# Test without making changes
./resource-manager.sh dev install all --dry-run

# PowerShell equivalent
.\resource-manager.ps1 -Environment dev -Action install -Resource all -DryRun
```

### **Validation Framework**
- Prerequisite checking
- Configuration validation
- Resource state validation
- Health check validation

### **Test Scenarios**
- Infrastructure validation
- Application functionality
- Security compliance
- Performance testing
- Disaster recovery
- Cost optimization

## 📈 **Monitoring and Alerting**

### **Real-Time Monitoring**
- Resource status monitoring
- Health check automation
- Performance metrics collection
- Cost tracking and optimization

### **Alert Generation**
- Critical failure alerts
- Performance degradation warnings
- Cost threshold alerts
- Security violation notifications

### **Continuous Monitoring**
- Configurable monitoring intervals
- Automated health checks
- Trend analysis and reporting
- Proactive issue detection

## 🔧 **Installation and Setup**

### **Prerequisites**
```bash
# Required tools
aws-cli >= 2.0
terraform >= 1.0
kubectl >= 1.20
helm >= 3.0

# AWS Configuration
aws configure
aws sts get-caller-identity  # Verify permissions
```

### **Script Setup**
```bash
# Make scripts executable
chmod +x *.sh

# PowerShell execution policy (Windows)
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### **Configuration Setup**
```bash
# Copy and customize configuration files
cp config/dev.conf.example config/dev.conf
cp config/staging.conf.example config/staging.conf
cp config/prod.conf.example config/prod.conf

# Edit configuration files with your settings
nano config/dev.conf
```

## 📚 **Usage Examples**

### **Complete Environment Setup**
```bash
# 1. Install infrastructure
./resource-manager.sh dev install vpc
./resource-manager.sh dev install storage
./resource-manager.sh dev install security

# 2. Install compute
./resource-manager.sh dev install compute

# 3. Install networking
./resource-manager.sh dev install network

# 4. Install applications
./resource-manager.sh dev install apps

# 5. Verify deployment
./resource-manager.sh dev status all
./resource-manager.sh dev health all
```

### **Cost Optimization Workflow**
```bash
# Halt resources during off-hours
./lifecycle-management.sh dev halt

# Verify cost savings
./resource-monitor.ps1 -Environment dev -Action cost

# Restart when needed
./lifecycle-management.sh dev restart

# Monitor resource health
./resource-monitor.ps1 -Environment dev -Action health -Continuous
```

### **Production Deployment**
```bash
# Deploy to production
./resource-manager.sh prod install all --auto-approve

# Monitor deployment
./resource-monitor.ps1 -Environment prod -Action status -Continuous -Interval 30

# Run comprehensive tests
./test-scenarios.sh --environment prod --test-scenarios all --timeout 7200
```

## 🆘 **Troubleshooting**

### **Common Issues**

**Permission Denied**
```bash
# Check AWS permissions
aws sts get-caller-identity

# Verify IAM policies
aws iam list-attached-user-policies --user-name $(aws sts get-caller-identity --query 'Account' --output text)
```

**Resource Not Found**
```bash
# Check resource status
./resource-manager.sh dev status all

# Verify configuration
cat config/dev.conf

# Check AWS region
aws configure get region
```

**Deployment Timeout**
```bash
# Increase timeout
export TEST_TIMEOUT=7200

# Check resource dependencies
./resource-manager.sh dev status vpc
./resource-manager.sh dev status storage
```

### **Debug Mode**
```bash
# Enable debug logging
export LOG_LEVEL=DEBUG

# Enable verbose output
./resource-manager.sh dev install vpc --verbose

# Check script execution
bash -x ./resource-manager.sh dev install vpc
```

## 📞 **Support and Contributing**

### **Getting Help**
- Check the troubleshooting section above
- Review configuration files for errors
- Enable debug logging for detailed information
- Check AWS CloudTrail for API errors

### **Contributing**
- Follow the existing code structure
- Add comprehensive error handling
- Include input validation
- Add appropriate logging
- Update documentation

### **Reporting Issues**
- Include environment details
- Provide error logs
- Describe expected vs actual behavior
- Include configuration snippets

## 📄 **License and Acknowledgments**

This project is part of the Chess AI project and follows the same licensing terms.

**Acknowledgments**:
- AWS for infrastructure services
- HashiCorp for Terraform
- Kubernetes community for container orchestration
- Helm community for package management

---

**Last Updated**: January 2024
**Version**: 2.0 (Enhanced)
**Compatibility**: AWS CLI v2, Terraform v1.0+, Kubernetes v1.20+, Helm v3.0+