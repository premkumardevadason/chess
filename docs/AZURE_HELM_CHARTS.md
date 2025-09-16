# Azure Helm Charts Configuration

## Overview

This document provides detailed configuration for Helm charts adapted for Azure deployment. The charts maintain compatibility with the existing Kubernetes manifests while leveraging Azure-specific features and services.

## Chart Structure

```
infra/helm/chess-app/
├── Chart.yaml
├── values.yaml
├── values-azure.yaml
├── values-dev.yaml
├── values-prod.yaml
└── templates/
    ├── _helpers.tpl
    ├── deployment.yaml
    ├── service.yaml
    ├── serviceaccount.yaml
    ├── hpa.yaml
    ├── istio-gateway.yaml
    ├── lifecycle-hooks.yaml
    ├── configmap.yaml
    ├── secret.yaml
    ├── pvc.yaml
    └── networkpolicy.yaml
```

## Azure-Specific Values

### `values-azure.yaml`

```yaml
# Azure-specific configuration
global:
  cloudProvider: "azure"
  
  # Azure Container Registry
  image:
    repository: "chessappacr.azurecr.io/chess-app"
    tag: "latest"
    pullPolicy: IfNotPresent
  
  # Azure Storage
  storage:
    type: "azure-blob"
    accountName: "chessappstorage"
    containerName: "ai-models"
    accessKeySecret: "azure-storage-key"
  
  # Azure Key Vault integration
  keyVault:
    enabled: true
    vaultName: "chess-app-kv"
    tenantId: "your-tenant-id"
    clientId: "your-client-id"
  
  # Azure Monitor
  monitoring:
    enabled: true
    workspaceId: "your-workspace-id"
    workspaceKey: "your-workspace-key"
  
  # Service Account with Azure Workload Identity
  serviceAccount:
    create: true
    annotations:
      azure.workload.identity/client-id: "your-managed-identity-client-id"
      azure.workload.identity/use: "true"
  
  # Azure-specific resource requirements
  resources:
    limits:
      cpu: 2000m
      memory: 4Gi
    requests:
      cpu: 1000m
      memory: 2Gi
  
  # Azure node affinity
  nodeSelector:
    kubernetes.io/os: linux
    node.kubernetes.io/instance-type: "Standard_D4s_v3"
  
  # Azure availability zones
  affinity:
    nodeAffinity:
      preferredDuringSchedulingIgnoredDuringExecution:
      - weight: 100
        preference:
          matchExpressions:
          - key: topology.kubernetes.io/zone
            operator: In
            values: ["eastus-1", "eastus-2", "eastus-3"]

# Azure-specific environment variables
env:
  - name: AZURE_CLIENT_ID
    valueFrom:
      secretKeyRef:
        name: azure-credentials
        key: client-id
  - name: AZURE_TENANT_ID
    valueFrom:
      secretKeyRef:
        name: azure-credentials
        key: tenant-id
  - name: AZURE_FEDERATED_TOKEN_FILE
    value: "/var/run/secrets/azure/tokens/azure-identity-token"
  - name: AZURE_AUTHORITY_HOST
    value: "https://login.microsoftonline.com/"
  - name: AZURE_STORAGE_ACCOUNT
    value: "chessappstorage"
  - name: AZURE_STORAGE_CONTAINER
    value: "ai-models"

# Azure-specific volumes
volumes:
  - name: azure-identity-token
    projected:
      sources:
      - serviceAccountToken:
          path: azure-identity-token
          expirationSeconds: 3600
  - name: ai-models-storage
    csi:
      driver: blob.csi.azure.com
      volumeAttributes:
        containerName: "ai-models"
        storageAccount: "chessappstorage"
        protocol: "fuse"

# Azure-specific volume mounts
volumeMounts:
  - name: azure-identity-token
    mountPath: "/var/run/secrets/azure/tokens"
    readOnly: true
  - name: ai-models-storage
    mountPath: "/mnt/azure/ai-models"
    readOnly: false
```

## Enhanced Templates

### `templates/deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "chess-app.fullname" . }}
  labels:
    {{- include "chess-app.labels" . | nindent 4 }}
  annotations:
    azure.workload.identity/use: "true"
spec:
  {{- if not .Values.autoscaling.enabled }}
  replicas: {{ .Values.replicaCount }}
  {{- end }}
  selector:
    matchLabels:
      {{- include "chess-app.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "chess-app.selectorLabels" . | nindent 8 }}
      annotations:
        azure.workload.identity/use: "true"
    spec:
      serviceAccountName: {{ include "chess-app.serviceAccountName" . }}
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 2000
      containers:
      - name: {{ .Chart.Name }}
        image: "{{ .Values.image.repository }}:{{ .Values.image.tag }}"
        imagePullPolicy: {{ .Values.image.pullPolicy }}
        ports:
        - name: http
          containerPort: {{ .Values.service.port }}
          protocol: TCP
        env:
        {{- range .Values.env }}
        - name: {{ .name }}
          {{- if .value }}
          value: {{ .value | quote }}
          {{- else if .valueFrom }}
          valueFrom:
            {{- toYaml .valueFrom | nindent 12 }}
          {{- end }}
        {{- end }}
        - name: CHESS_AI_STORAGE_PATH
          value: "/mnt/azure/ai-models"
        - name: SPRING_PROFILES_ACTIVE
          value: {{ .Values.global.environment | default "prod" }}
        securityContext:
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: true
          capabilities:
            drop:
            - ALL
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: http
          initialDelaySeconds: 60
          periodSeconds: 30
          timeoutSeconds: 5
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: http
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        volumeMounts:
        {{- range .Values.volumeMounts }}
        - name: {{ .name }}
          mountPath: {{ .mountPath }}
          {{- if .readOnly }}
          readOnly: {{ .readOnly }}
          {{- end }}
        {{- end }}
        - name: tmp
          mountPath: /tmp
        - name: var-tmp
          mountPath: /var/tmp
        resources:
          {{- toYaml .Values.resources | nindent 10 }}
      volumes:
      {{- range .Values.volumes }}
      - name: {{ .name }}
        {{- toYaml . | nindent 8 }}
      {{- end }}
      - name: tmp
        emptyDir: {}
      - name: var-tmp
        emptyDir: {}
      {{- with .Values.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.affinity }}
      affinity:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with .Values.tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
```

### `templates/serviceaccount.yaml`

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: {{ include "chess-app.serviceAccountName" . }}
  labels:
    {{- include "chess-app.labels" . | nindent 4 }}
  annotations:
    {{- if .Values.serviceAccount.annotations }}
    {{- toYaml .Values.serviceAccount.annotations | nindent 4 }}
    {{- end }}
    azure.workload.identity/use: "true"
automountServiceAccountToken: true
```

### `templates/secret.yaml`

```yaml
{{- if .Values.global.keyVault.enabled }}
apiVersion: v1
kind: Secret
metadata:
  name: {{ include "chess-app.fullname" . }}-azure-credentials
  labels:
    {{- include "chess-app.labels" . | nindent 4 }}
type: Opaque
data:
  client-id: {{ .Values.global.keyVault.clientId | b64enc }}
  tenant-id: {{ .Values.global.keyVault.tenantId | b64enc }}
{{- end }}
```

### `templates/configmap.yaml`

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ include "chess-app.fullname" . }}-config
  labels:
    {{- include "chess-app.labels" . | nindent 4 }}
data:
  application.yml: |
    spring:
      profiles:
        active: {{ .Values.global.environment | default "prod" }}
      
      # Azure-specific configuration
      cloud:
        azure:
          storage:
            account-name: {{ .Values.global.storage.accountName }}
            container-name: {{ .Values.global.storage.containerName }}
      
      # Database configuration (if using Azure Database)
      datasource:
        url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:chessdb}
        username: ${DB_USERNAME:chessuser}
        password: ${DB_PASSWORD:chesspass}
        hikari:
          maximum-pool-size: 10
          minimum-idle: 5
          connection-timeout: 30000
          idle-timeout: 600000
          max-lifetime: 1800000
      
      # JPA configuration
      jpa:
        hibernate:
          ddl-auto: validate
        show-sql: false
        properties:
          hibernate:
            dialect: org.hibernate.dialect.PostgreSQLDialect
            format_sql: true
      
      # Actuator configuration
      management:
        endpoints:
          web:
            exposure:
              include: health,info,metrics,prometheus
        endpoint:
          health:
            show-details: when-authorized
      
      # Logging configuration
      logging:
        level:
          com.chess: INFO
          org.springframework: WARN
          org.hibernate: WARN
        pattern:
          console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
          file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
        file:
          name: /var/log/chess-app/application.log
      
      # Security configuration
      security:
        oauth2:
          client:
            registration:
              azure:
                client-id: ${AZURE_CLIENT_ID}
                client-secret: ${AZURE_CLIENT_SECRET}
                scope: openid,profile,email
                authorization-grant-type: authorization_code
                redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            provider:
              azure:
                authorization-uri: https://login.microsoftonline.com/${AZURE_TENANT_ID}/oauth2/v2.0/authorize
                token-uri: https://login.microsoftonline.com/${AZURE_TENANT_ID}/oauth2/v2.0/token
                user-info-uri: https://graph.microsoft.com/oidc/userinfo
                user-name-attribute: sub
```

### `templates/pvc.yaml`

```yaml
{{- if .Values.persistence.enabled }}
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: {{ include "chess-app.fullname" . }}-pvc
  labels:
    {{- include "chess-app.labels" . | nindent 4 }}
spec:
  accessModes:
    - {{ .Values.persistence.accessMode | default "ReadWriteOnce" }}
  resources:
    requests:
      storage: {{ .Values.persistence.size | default "10Gi" }}
  {{- if .Values.persistence.storageClass }}
  storageClassName: {{ .Values.persistence.storageClass }}
  {{- end }}
{{- end }}
```

### `templates/networkpolicy.yaml`

```yaml
{{- if .Values.networkPolicy.enabled }}
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: {{ include "chess-app.fullname" . }}-netpol
  labels:
    {{- include "chess-app.labels" . | nindent 4 }}
spec:
  podSelector:
    matchLabels:
      {{- include "chess-app.selectorLabels" . | nindent 6 }}
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: istio-system
    - namespaceSelector:
        matchLabels:
          name: istio-ingress
    ports:
    - protocol: TCP
      port: {{ .Values.service.port }}
  egress:
  - to: []
    ports:
    - protocol: TCP
      port: 443
    - protocol: TCP
      port: 80
    - protocol: TCP
      port: 5432
    - protocol: TCP
      port: 6379
{{- end }}
```

## Azure-Specific Helm Values

### Development Environment (`values-dev.yaml`)

```yaml
# Development-specific overrides
global:
  environment: "dev"
  image:
    tag: "dev-latest"
  
  resources:
    limits:
      cpu: 1000m
      memory: 2Gi
    requests:
      cpu: 500m
      memory: 1Gi
  
  autoscaling:
    enabled: false
    minReplicas: 1
    maxReplicas: 3

replicaCount: 1

# Development-specific environment variables
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "dev"
  - name: LOGGING_LEVEL_ROOT
    value: "DEBUG"

# Disable production features
networkPolicy:
  enabled: false

persistence:
  enabled: false

# Development monitoring
monitoring:
  enabled: true
  prometheus:
    enabled: true
  grafana:
    enabled: true
```

### Production Environment (`values-prod.yaml`)

```yaml
# Production-specific overrides
global:
  environment: "prod"
  image:
    tag: "v1.0.0"
  
  resources:
    limits:
      cpu: 4000m
      memory: 8Gi
    requests:
      cpu: 2000m
      memory: 4Gi
  
  autoscaling:
    enabled: true
    minReplicas: 3
    maxReplicas: 20
    targetCPUUtilizationPercentage: 70
    targetMemoryUtilizationPercentage: 80

replicaCount: 3

# Production-specific environment variables
env:
  - name: SPRING_PROFILES_ACTIVE
    value: "prod"
  - name: LOGGING_LEVEL_ROOT
    value: "INFO"

# Enable production features
networkPolicy:
  enabled: true

persistence:
  enabled: true
  size: 100Gi
  storageClass: "managed-premium"

# Production monitoring
monitoring:
  enabled: true
  prometheus:
    enabled: true
    retention: "30d"
  grafana:
    enabled: true
    adminPassword: "secure-password"

# Production security
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  fsGroup: 2000
  seccompProfile:
    type: RuntimeDefault
```

## Deployment Commands

### Install Chart

```bash
# Add Azure-specific values
helm install chess-app ./infra/helm/chess-app \
  --namespace chess-app \
  --create-namespace \
  --values ./infra/helm/chess-app/values-azure.yaml \
  --values ./infra/helm/chess-app/values-prod.yaml \
  --set global.image.tag=v1.0.0 \
  --set global.storage.accountName=chessappstorage \
  --set global.keyVault.vaultName=chess-app-kv
```

### Upgrade Chart

```bash
# Upgrade with new image
helm upgrade chess-app ./infra/helm/chess-app \
  --namespace chess-app \
  --values ./infra/helm/chess-app/values-azure.yaml \
  --values ./infra/helm/chess-app/values-prod.yaml \
  --set global.image.tag=v1.1.0
```

### Rollback Chart

```bash
# Rollback to previous version
helm rollback chess-app 1 --namespace chess-app
```

## Azure Integration Features

### 1. Workload Identity

The charts support Azure Workload Identity for secure authentication without storing secrets:

```yaml
serviceAccount:
  annotations:
    azure.workload.identity/client-id: "your-managed-identity-client-id"
    azure.workload.identity/use: "true"
```

### 2. Azure Storage Integration

Seamless integration with Azure Blob Storage using CSI driver:

```yaml
volumes:
  - name: ai-models-storage
    csi:
      driver: blob.csi.azure.com
      volumeAttributes:
        containerName: "ai-models"
        storageAccount: "chessappstorage"
```

### 3. Azure Key Vault Integration

Secure secret management using Azure Key Vault:

```yaml
keyVault:
  enabled: true
  vaultName: "chess-app-kv"
  tenantId: "your-tenant-id"
  clientId: "your-client-id"
```

### 4. Azure Monitor Integration

Comprehensive monitoring with Azure Monitor and Application Insights:

```yaml
monitoring:
  enabled: true
  workspaceId: "your-workspace-id"
  workspaceKey: "your-workspace-key"
```

## Best Practices

### 1. Security

- Use Azure Workload Identity for authentication
- Enable network policies for micro-segmentation
- Implement proper RBAC and least privilege access
- Use Azure Key Vault for secret management

### 2. Performance

- Configure appropriate resource limits and requests
- Use Azure Premium Storage for high-performance workloads
- Implement horizontal pod autoscaling
- Optimize container images for Azure

### 3. Reliability

- Use multiple availability zones
- Implement proper health checks and probes
- Configure graceful shutdown and startup
- Use Azure managed services where possible

### 4. Monitoring

- Enable comprehensive logging and metrics
- Use Azure Monitor for centralized monitoring
- Implement distributed tracing with Jaeger
- Set up proper alerting and notification

## Troubleshooting

### Common Issues

1. **Image Pull Failures**: Check ACR authentication and permissions
2. **Storage Mount Issues**: Verify CSI driver installation and permissions
3. **Authentication Failures**: Check Workload Identity configuration
4. **Network Connectivity**: Verify Network Policies and Service Mesh configuration

### Debugging Commands

```bash
# Check pod status
kubectl get pods -n chess-app

# Check logs
kubectl logs -f deployment/chess-app -n chess-app

# Check events
kubectl get events -n chess-app --sort-by='.lastTimestamp'

# Check service account
kubectl describe serviceaccount chess-app -n chess-app

# Check secrets
kubectl get secrets -n chess-app
```

## Conclusion

This Azure Helm charts configuration provides a production-ready deployment solution that leverages Azure-native services and features. The charts maintain compatibility with the existing application while providing enhanced security, monitoring, and performance capabilities specific to the Azure platform.
