# ══════════════════════════════════════════════════════════════
# Tutti — Terraform Variables
# ══════════════════════════════════════════════════════════════

variable "project_id" {
  description = "GCP 프로젝트 ID"
  type        = string
}

variable "region" {
  description = "GCP 리전 (Artifact Registry 등)"
  type        = string
  default     = "us-central1"
}

variable "zone" {
  description = "GKE 클러스터가 배포될 Zonal 영역"
  type        = string
  default     = "us-central1-a"
}

variable "cluster_name" {
  description = "GKE 클러스터 이름"
  type        = string
  default     = "tutti-cluster"
}

variable "node_machine_type" {
  description = "워커 노드 머신 타입 (Spot VM)"
  type        = string
  default     = "e2-medium"  # 2 vCPU, 4GB RAM — Spring Boot 서비스에 적합
}

variable "min_node_count" {
  description = "노드풀 최소 노드 수"
  type        = number
  default     = 1
}

variable "max_node_count" {
  description = "노드풀 최대 노드 수"
  type        = number
  default     = 3
}

variable "disk_size_gb" {
  description = "노드 디스크 크기 (GB)"
  type        = number
  default     = 30  # 비용 최소화: 최소 권장 크기
}
