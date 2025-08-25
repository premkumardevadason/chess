# Monitoring Stack via Helm
resource "kubernetes_namespace" "monitoring" {
  metadata {
    name = "monitoring"
    labels = {
      "istio-injection" = "enabled"
    }
  }
}

resource "helm_release" "kube_prometheus_stack" {
  name       = "kube-prometheus-stack"
  repository = "https://prometheus-community.github.io/helm-charts"
  chart      = "kube-prometheus-stack"
  namespace  = kubernetes_namespace.monitoring.metadata[0].name
  version    = "55.0.0"
  
  timeout = 600
  
  values = [
    yamlencode({
      prometheus = {
        prometheusSpec = {
          retention = var.prometheus_retention_period
          storageSpec = {
            volumeClaimTemplate = {
              spec = {
                storageClassName = "gp3"
                accessModes      = ["ReadWriteOnce"]
                resources = {
                  requests = {
                    storage = var.prometheus_storage_size
                  }
                }
              }
            }
          }
        }
      }
      grafana = {
        enabled = true
        adminPassword = "admin"
        service = {
          type = "ClusterIP"
        }
      }
      alertmanager = {
        enabled = true
      }
    })
  ]
  
  depends_on = [kubernetes_namespace.monitoring]
}

resource "helm_release" "jaeger" {
  name       = "jaeger"
  repository = "https://jaegertracing.github.io/helm-charts"
  chart      = "jaeger"
  namespace  = kubernetes_namespace.monitoring.metadata[0].name
  version    = "0.71.0"
  
  timeout = 600
  
  values = [
    yamlencode({
      storage = {
        type = "memory"
      }
      agent = {
        enabled = true
      }
      collector = {
        enabled = true
      }
      query = {
        enabled = true
        service = {
          type = "ClusterIP"
        }
      }
    })
  ]
  
  depends_on = [helm_release.kube_prometheus_stack]
}