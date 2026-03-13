# ══════════════════════════════════════════════════════════════════════════════
# Tutti — Terraform Main Configuration
# ══════════════════════════════════════════════════════════════════════════════
#
# 💡 포트폴리오 설계 포인트:
#   이 Terraform 코드는 "비용 제로화"를 목표로 설계되었습니다.
#   GKE Standard Zonal 클러스터 + Spot VM 전용 노드풀 + Artifact Registry를 통해
#   GCP 3개월 크레딧 내에서 실질 비용 $0에 가까운 인프라를 구현합니다.
#
# 아키텍처 요약:
#   • GKE Standard Zonal (관리비 무료) — Regional의 $74.40/월 절감
#   • Spot VM e2-medium (일반 대비 ~70% 할인) — 워커 노드 비용 최소화
#   • 기본 노드풀 즉시 삭제 → Spot 전용 커스텀 노드풀로 교체
#   • Artifact Registry (gcr.io 대비 안정적, 무료 저장소 제공)
#   • 외부 LoadBalancer 미사용 — Cloudflare Tunnel로 트래픽 라우팅 (비용 $0)
#
# ══════════════════════════════════════════════════════════════════════════════

# ────────────────────────────────────────────────────────────
# Terraform Settings
# ────────────────────────────────────────────────────────────
terraform {
  required_version = ">= 1.5.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }

  # 💡 포트폴리오 POINT: 팀 협업 시 GCS Backend로 전환 가능
  # backend "gcs" {
  #   bucket = "tutti-terraform-state"
  #   prefix = "gke"
  # }
}

# ────────────────────────────────────────────────────────────
# Provider Configuration
# ────────────────────────────────────────────────────────────
provider "google" {
  project = var.project_id
  region  = var.region
}

# ────────────────────────────────────────────────────────────
# GKE Cluster — Standard Zonal (관리비 무료)
# ────────────────────────────────────────────────────────────
#
# 💰 비용 최적화 포인트:
#   • Zonal 클러스터는 GKE Standard에서 관리비(Control Plane)가 무료
#     → Regional 클러스터($74.40/월)와 달리 $0/월
#   • 전시 프로젝트이므로 Multi-zone HA가 불필요
#   • release_channel = "REGULAR" → 안정적인 K8s 버전 자동 업그레이드
#
# ⚠️  Zonal의 단점: Control Plane 단일 장애점(SPOF) → 전시 목적에는 무방
#
resource "google_container_cluster" "primary" {
  name     = var.cluster_name
  location = var.zone  # 💰 Zonal → 관리비 무료 (Regional과의 핵심 차이)

  # ⚠️ Terraform에서 클러스터를 삭제할 수 있도록 보호 해제
  # 프로덕션에서는 true로 설정하여 실수로 인한 삭제를 방지할 것
  deletion_protection = false

  # ────────────────────────────────────────
  # 💰 핵심: 기본 노드풀 즉시 삭제
  # ────────────────────────────────────────
  # GKE는 클러스터 생성 시 기본 노드풀(On-demand)을 자동 생성합니다.
  # 이를 즉시 삭제하고 Spot VM 전용 커스텀 노드풀로 교체하여
  # On-demand 비용 발생을 원천 차단합니다.
  #
  remove_default_node_pool = true
  initial_node_count       = 1  # 기본 풀 생성 후 즉시 삭제됨

  # ── Release Channel ──
  # K8s 버전을 GKE가 자동으로 관리 → 수동 업그레이드 부담 제거
  release_channel {
    channel = "REGULAR"
  }

  # ── 네트워크 정책 ──
  # network_policy는 별도의 Calico CNI 설치가 필요하므로 생략
  # Standard GKE 클러스터에서는 Dataplane V2 사용 시 자동 지원

  # ── Workload Identity ──
  # 💡 포트폴리오 POINT: Pod가 GCP 서비스에 안전하게 접근하기 위한 모범 사례
  # Service Account Key 파일 없이 IAM 역할을 Pod에 바인딩
  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }

  # ── 추가 설정 ──
  # HTTP LB 비활성화 — Cloudflare Tunnel 사용으로 GCE Ingress 불필요
  addons_config {
    http_load_balancing {
      disabled = true  # 💰 GCE LB ($18+/월) 제거
    }
    horizontal_pod_autoscaling {
      disabled = false  # HPA 활성화
    }
  }

  # ── Logging & Monitoring (비용 최적화) ──
  # GKE 기본 Cloud Logging/Monitoring은 무료 할당량 내에서 운영
  logging_config {
    enable_components = ["SYSTEM_COMPONENTS"]  # 💰 WORKLOADS 제거 — Cloud Logging 무료 할당량(150MB/월) 초과 방지
  }
  monitoring_config {
    enable_components = ["SYSTEM_COMPONENTS"]
    managed_prometheus {
      enabled = false  # 💰 Managed Prometheus는 과금 대상
    }
  }
}

# ────────────────────────────────────────────────────────────
# Node Pool — Spot VM 전용 (비용 70% 절감)
# ────────────────────────────────────────────────────────────
#
# 💰 비용 최적화 포인트:
#   • Spot VM = GCP의 Preemptible VM 차세대 버전 (최대 91% 할인)
#   • e2-medium (2 vCPU, 4GB RAM) 기준:
#     - On-demand: ~$24.46/월
#     - Spot:      ~$7.34/월 (약 70% 절감)
#   • Autoscaling으로 유휴 시간에 노드 수 자동 축소
#
# ⚠️  Spot VM 주의사항:
#   • GCP가 리소스 필요 시 24시간 내 노드를 선점(회수)할 수 있음
#   • GKE가 자동으로 새 Spot 노드를 프로비저닝하여 Pod를 재스케줄링
#   • terminationGracePeriodSeconds로 graceful shutdown 보장
#   • 전시 프로젝트에서는 짧은 다운타임이 허용 가능하므로 최적의 선택
#
resource "google_container_node_pool" "spot_pool" {
  name     = "spot-pool"
  location = var.zone
  cluster  = google_container_cluster.primary.name

  # ── Autoscaling 설정 ──
  # 💰 유휴 시간에 노드를 최소 1개까지 축소하여 비용 절감
  autoscaling {
    min_node_count = var.min_node_count  # 기본: 1
    max_node_count = var.max_node_count  # 기본: 3 (전시 트래픽 피크 대응)
  }

  # ── 노드 관리 설정 ──
  management {
    auto_repair  = true   # 비정상 노드 자동 복구
    auto_upgrade = true   # K8s 버전 자동 업그레이드
  }

  # ── 노드 스펙 ──
  node_config {
    machine_type = var.node_machine_type  # e2-medium (2 vCPU, 4GB)

    # ═══════════════════════════════════════
    # 💰 핵심: Spot VM 활성화
    # ═══════════════════════════════════════
    spot = true  # Spot VM으로 ~70% 비용 절감!

    disk_size_gb = var.disk_size_gb  # 30GB (최소 권장)
    disk_type    = "pd-standard"     # 💰 pd-ssd 대비 ~60% 절감 — 전시용에는 충분

    # ── OAuth Scopes ──
    # 노드가 GCP API에 접근하기 위한 최소 권한
    oauth_scopes = [
      "https://www.googleapis.com/auth/cloud-platform",
    ]

    # ── Workload Identity (노드 레벨) ──
    # Pod-level Identity를 위해 필요
    workload_metadata_config {
      mode = "GKE_METADATA"
    }

    # ── Labels ──
    # 💡 포트폴리오 POINT: 리소스 태깅으로 비용 추적 가능
    labels = {
      environment = "production"
      team        = "tutti"
      managed-by  = "terraform"
      node-type   = "spot"  # Spot 노드임을 명시
    }

    # ── Taints (선택사항) ──
    # Spot 노드에 taint를 걸어 중요 워크로드만 toleration으로 배포 가능
    # 현재는 모든 워크로드가 Spot에서 실행되므로 비활성화
    # taint {
    #   key    = "cloud.google.com/gke-spot"
    #   value  = "true"
    #   effect = "NO_SCHEDULE"
    # }
  }

  # ── 업그레이드 전략 ──
  # Surge upgrade: 업그레이드 시 추가 노드 1개 생성 → 기존 노드 drain
  upgrade_settings {
    max_surge       = 1
    max_unavailable = 0  # 무중단 업그레이드
  }
}

# ────────────────────────────────────────────────────────────
# Artifact Registry — 컨테이너 이미지 저장소
# ────────────────────────────────────────────────────────────
#
# 💡 포트폴리오 POINT: gcr.io 대신 Artifact Registry 사용 이유
#   • gcr.io는 Legacy이며 Artifact Registry가 GA 후속 서비스
#   • Artifact Registry는 0.5GB/월 무료 저장소 제공
#   • 같은 리전(us-central1) 내 GKE에서 이미지 pull 시 네트워크 비용 $0
#   • IAM 기반 세밀한 접근 제어 가능
#
resource "google_artifact_registry_repository" "docker" {
  location      = var.region
  repository_id = "tutti"
  description   = "Tutti 컨테이너 이미지 저장소"
  format        = "DOCKER"

  # 💰 비용 최적화: 오래된 이미지 자동 정리 정책
  cleanup_policy_dry_run = false

  cleanup_policies {
    id     = "delete-old-images"
    action = "DELETE"
    condition {
      older_than = "2592000s"  # 30일 이상된 이미지 삭제
      tag_state  = "ANY"       # 💰 태그 유무와 관계없이 모든 오래된 이미지 정리
    }
  }

  cleanup_policies {
    id     = "keep-minimum-versions"
    action = "KEEP"
    most_recent_versions {
      keep_count = 10  # 최신 10개 버전은 항상 유지 (30일 규칙보다 우선)
    }
  }
}
