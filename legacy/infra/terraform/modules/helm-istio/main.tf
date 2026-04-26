# Istio Installation via Helm
resource "kubernetes_namespace" "istio_system" {
  metadata {
    name = var.istio_namespace
    labels = {
      "istio-injection" = "disabled"
    }
  }
}

resource "helm_release" "istio_base" {
  name       = "istio-base"
  repository = "https://istio-release.storage.googleapis.com/charts"
  chart      = "base"
  namespace  = kubernetes_namespace.istio_system.metadata[0].name
  version    = var.istio_version
  
  timeout = 600
  
  depends_on = [kubernetes_namespace.istio_system]
}

resource "helm_release" "istiod" {
  name       = "istiod"
  repository = "https://istio-release.storage.googleapis.com/charts"
  chart      = "istiod"
  namespace  = kubernetes_namespace.istio_system.metadata[0].name
  version    = var.istio_version
  
  timeout = 600
  
  values = [
    yamlencode({
      pilot = {
        env = {
          EXTERNAL_ISTIOD = false
        }
      }
      global = {
        meshID = "mesh1"
        multiCluster = {
          clusterName = "${var.environment}-cluster"
        }
        network = "network1"
      }
    })
  ]
  
  depends_on = [helm_release.istio_base]
}

resource "kubernetes_namespace" "istio_ingress" {
  metadata {
    name = "istio-ingress"
    labels = {
      "istio-injection" = "enabled"
    }
  }
}

resource "helm_release" "istio_gateway" {
  name       = "istio-gateway"
  repository = "https://istio-release.storage.googleapis.com/charts"
  chart      = "gateway"
  namespace  = kubernetes_namespace.istio_ingress.metadata[0].name
  version    = var.istio_version
  
  timeout = 600
  
  values = [
    yamlencode({
      service = {
        type = "ClusterIP"
        ports = [
          {
            port = 80
            name = "http"
          },
          {
            port = 443
            name = "https"
          }
        ]
      }
    })
  ]
  
  depends_on = [helm_release.istiod, kubernetes_namespace.istio_ingress]
}