# Chess App Helm Deployment
resource "kubernetes_namespace" "chess_app" {
  metadata {
    name = var.app_namespace
    labels = {
      "istio-injection" = "enabled"
    }
  }
}

resource "helm_release" "chess_app" {
  name      = var.app_release_name
  chart     = "${path.root}/../helm/chess-app"
  namespace = kubernetes_namespace.chess_app.metadata[0].name
  
  timeout          = var.helm_timeout
  cleanup_on_fail  = true
  atomic           = true
  
  values = [
    file("${path.root}/../helm/values/${var.environment}.yaml")
  ]
  
  dynamic "set" {
    for_each = var.helm_set_values
    content {
      name  = set.key
      value = set.value
    }
  }
  
  dynamic "set_sensitive" {
    for_each = var.helm_set_sensitive_values
    content {
      name  = set_sensitive.key
      value = set_sensitive.value
    }
  }
  
  set {
    name  = "global.s3.bucket"
    value = var.s3_bucket_name
  }
  
  set {
    name  = "global.serviceAccount.roleArn"
    value = var.service_account_role_arn
  }
  
  depends_on = [kubernetes_namespace.chess_app]
}