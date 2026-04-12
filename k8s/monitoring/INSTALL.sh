# ══════════════════════════════════════════════════════════════
# 모니터링 스택 배포 가이드
# ══════════════════════════════════════════════════════════════
#
# GKE 클러스터에 모니터링 스택을 설치하는 순서입니다.
# 모든 명령은 kubectl이 GKE 클러스터에 연결된 상태에서 실행하세요.
#
# ══════════════════════════════════════════════════════════════

# ── 1. 네임스페이스 생성 ──
kubectl apply -f namespace.yaml

# ── 2. 시크릿 확인 ──
# (참고: CI/CD가 구동될 때 GitHub Secrets를 통해 자동으로 생성됩니다.)
kubectl get secret supabase-monitor-secret -n monitoring

# ── 3. Helm 차트 설치 ──
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

# kube-prometheus-stack (Prometheus + Grafana + Alertmanager)
helm install prometheus prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  -f kube-prometheus-stack-values.yaml

# Loki (로그 수집)
helm install loki grafana/loki \
  --namespace monitoring \
  -f loki-values.yaml

# Promtail (GKE Pod → Loki 로그 전송)
helm install promtail grafana/promtail \
  --namespace monitoring \
  -f promtail-values.yaml

# ── 4. ServiceMonitor & Exporter 배포 ──
kubectl apply -f main-server-servicemonitor.yaml
kubectl apply -f cloudflared-servicemonitor.yaml
kubectl apply -f postgres-exporter.yaml

# ── 5. 상태 확인 ──
kubectl get pods -n monitoring
kubectl get servicemonitor -n monitoring

# ── 6. Grafana 접근 (포트 포워딩으로 테스트) ──
# kubectl port-forward svc/prometheus-grafana -n monitoring 3000:80
# → http://localhost:3000 (admin / tutti-grafana-change-me)
