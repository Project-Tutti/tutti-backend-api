# ══════════════════════════════════════════════════════════════
# Tutti — Terraform Outputs
# ══════════════════════════════════════════════════════════════

output "cluster_name" {
  description = "GKE 클러스터 이름"
  value       = google_container_cluster.primary.name
}

output "cluster_endpoint" {
  description = "GKE 클러스터 API 엔드포인트"
  value       = google_container_cluster.primary.endpoint
  sensitive   = true
}

output "cluster_zone" {
  description = "GKE 클러스터 배포 영역"
  value       = google_container_cluster.primary.location
}

output "artifact_registry_url" {
  description = "Artifact Registry Docker 이미지 URL prefix"
  value       = "${var.region}-docker.pkg.dev/${var.project_id}/${google_artifact_registry_repository.docker.repository_id}"
}

output "kubeconfig_command" {
  description = "kubeconfig 설정 명령어"
  value       = "gcloud container clusters get-credentials ${google_container_cluster.primary.name} --zone ${var.zone} --project ${var.project_id}"
}
