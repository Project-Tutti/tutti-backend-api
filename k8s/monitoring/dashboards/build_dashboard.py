import json
import os

def create_dashboard():
    dashboard = {
        "annotations": {"list": []},
        "editable": True,
        "graphTooltip": 1,
        "links": [],
        "panels": [],
        "refresh": "10s",
        "schemaVersion": 38,
        "style": "dark",
        "tags": ["tutti", "kubernetes"],
        "templating": {"list": []},
        "time": {"from": "now-3h", "to": "now"},
        "timepicker": {},
        "timezone": "Asia/Seoul",
        "title": "Tutti — 서비스 Overview",
        "uid": "tutti-overview",
        "version": 5
    }

    panel_id = 1
    def add_row(title, y_pos):
        nonlocal panel_id
        dashboard["panels"].append({
            "collapsed": False,
            "gridPos": {"h": 1, "w": 24, "x": 0, "y": y_pos},
            "id": panel_id,
            "panels": [],
            "title": title,
            "type": "row"
        })
        panel_id += 1

    # Row 1: SINGLE Status Overview Box
    add_row("🌟 클러스터 통합 가동 상태 리스트 (Status Overview)", 0)
    
    dashboard["panels"].append({
        "title": "전체 서비스 구동 상태 모음 (Main / AI / Converter / DB / Redis / CF Tunnel)",
        "type": "table",
        "id": panel_id,
        "gridPos": {"h": 12, "w": 24, "x": 0, "y": 1},
        "datasource": {"type": "prometheus", "uid": "prometheus"},
        "targets": [
            {
                "expr": 'kube_pod_status_ready{condition="true", pod=~".*main-server.*"}',
                "format": "time_series",
                "instant": True,
                "legendFormat": "{{pod}}",
                "refId": "A"
            },
            {
                "expr": 'up{job="node_scrape"}',
                "format": "time_series",
                "instant": True,
                "legendFormat": "ai-server (외부 온프레미스)",
                "refId": "B"
            },
            {
                "expr": 'kube_pod_status_ready{condition="true", pod=~".*converter.*"}',
                "format": "time_series",
                "instant": True,
                "legendFormat": "{{pod}}",
                "refId": "C"
            },
            {
                "expr": 'pg_up',
                "format": "time_series",
                "instant": True,
                "legendFormat": "supabase-db (외부/내부)",
                "refId": "E"
            },
            {
                "expr": 'up{job="cloudflared-metrics"}',
                "format": "time_series",
                "instant": True,
                "legendFormat": "cf-tunnel",
                "refId": "F"
            }
        ],
        "transformations": [
            {
                "id": "reduce",
                "options": {
                    "includeTimeField": False,
                    "mode": "seriesToRows",
                    "reducers": ["lastNotNull"]
                }
            }
        ],
        "fieldConfig": {
            "defaults": {
                "mappings": [
                    {"options": {"0": {"color": "red", "text": "🔴 DOWN (에러)"}, "1": {"color": "green", "text": "🟢 UP (정상)"}}, "type": "value"}
                ],
                "custom": {
                    "align": "left"
                }
            },
            "overrides": [
                {
                    "matcher": {"id": "byType", "options": "number"},
                    "properties": [{"id": "displayName", "value": "STATUS"}, {"id": "custom.align", "value": "center"}]
                },
                {
                    "matcher": {"id": "byType", "options": "string"},
                    "properties": [{"id": "displayName", "value": "서비스 인스턴스 (INSTANCE)"}]
                }
            ]
        },
        "options": {"showHeader": True}
    })
    panel_id += 1

    # Row 2: Infrastructure
    add_row("⚙️ 시스템 리소스 (Infrastructure)", 10)
    
    dashboard["panels"].extend([
        {
            "title": "CPU 사용량 및 Limit (Cores)",
            "type": "timeseries",
            "id": panel_id,
            "gridPos": {"h": 8, "w": 8, "x": 0, "y": 11},
            "datasource": {"type": "prometheus", "uid": "prometheus"},
            "fieldConfig": {"defaults": {"custom": {"drawStyle": "line", "fillOpacity": 15, "gradientMode": "scheme"}, "unit": "cores"}},
            "options": {"tooltip": {"mode": "multi", "sort": "none"}},
            "targets": [
                {"expr": "sum(rate(container_cpu_usage_seconds_total{namespace=\"tutti\", container=\"main-server\"}[1m]))", "legendFormat": "Main Server CPU 사용", "refId": "A"},
                {"expr": "sum(kube_pod_container_resource_limits{namespace=\"tutti\", container=\"main-server\", resource=\"cpu\"})", "legendFormat": "Main Server CPU 한도(Limit)", "refId": "B"}
            ]
        },
        {
            "title": "메모리 사용량 및 Limit (Bytes)",
            "type": "timeseries",
            "id": panel_id + 1,
            "gridPos": {"h": 8, "w": 8, "x": 8, "y": 11},
            "datasource": {"type": "prometheus", "uid": "prometheus"},
            "fieldConfig": {"defaults": {"custom": {"drawStyle": "line", "fillOpacity": 15, "gradientMode": "scheme"}, "unit": "bytes"}},
            "options": {"tooltip": {"mode": "multi", "sort": "none"}},
            "targets": [
                {"expr": "sum(container_memory_working_set_bytes{namespace=\"tutti\", container=\"main-server\"})", "legendFormat": "Main RAM 사용", "refId": "A"},
                {"expr": "sum(kube_pod_container_resource_limits{namespace=\"tutti\", container=\"main-server\", resource=\"memory\"})", "legendFormat": "Main RAM 한도(Limit)", "refId": "B"}
            ]
        },
        {
            "title": "네트워크 트래픽 (Bytes/s)",
            "type": "timeseries",
            "id": panel_id + 2,
            "gridPos": {"h": 8, "w": 8, "x": 16, "y": 11},
            "datasource": {"type": "prometheus", "uid": "prometheus"},
            "fieldConfig": {"defaults": {"custom": {"drawStyle": "line", "fillOpacity": 15, "gradientMode": "scheme"}, "unit": "Bps"}},
            "options": {"tooltip": {"mode": "multi", "sort": "none"}},
            "targets": [
                {"expr": "sum(rate(container_network_receive_bytes_total{namespace=\"tutti\"}[1m]))", "legendFormat": "Receive", "refId": "A"},
                {"expr": "sum(rate(container_network_transmit_bytes_total{namespace=\"tutti\"}[1m]))", "legendFormat": "Transmit", "refId": "B"}
            ]
        }
    ])
    panel_id += 3

    # Row 3: Application Metrics
    add_row("🚦 애플리케이션 지표 (Application)", 20)

    dashboard["panels"].extend([
        {
            "title": "초당 HTTP 요청 수",
            "type": "timeseries",
            "id": panel_id,
            "gridPos": {"h": 8, "w": 12, "x": 0, "y": 21},
            "datasource": {"type": "prometheus", "uid": "prometheus"},
            "fieldConfig": {"defaults": {"custom": {"drawStyle": "bars", "fillOpacity": 80}, "unit": "reqps"}},
            "options": {"tooltip": {"mode": "multi", "sort": "none"}},
            "targets": [{"expr": "sum(rate(http_server_requests_seconds_count{namespace=\"tutti\"}[1m])) by (method)", "legendFormat": "{{method}}", "refId": "A"}]
        },
        {
            "title": "HTTP 95퍼센타일 응답 지연율 (Seconds)",
            "type": "timeseries",
            "id": panel_id + 1,
            "gridPos": {"h": 8, "w": 12, "x": 12, "y": 21},
            "datasource": {"type": "prometheus", "uid": "prometheus"},
            "fieldConfig": {"defaults": {"custom": {"drawStyle": "line", "fillOpacity": 10, "gradientMode": "scheme"}, "unit": "s"}},
            "options": {"tooltip": {"mode": "multi", "sort": "none"}},
            "targets": [{"expr": "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{namespace=\"tutti\"}[5m])) by (le))", "legendFormat": "p95 latency", "refId": "A"}]
        },
        {
            "title": "데이터베이스 커넥션 풀",
            "type": "timeseries",
            "id": panel_id + 2,
            "gridPos": {"h": 8, "w": 12, "x": 0, "y": 29},
            "datasource": {"type": "prometheus", "uid": "prometheus"},
            "fieldConfig": {"defaults": {"custom": {"drawStyle": "line", "fillOpacity": 20, "gradientMode": "scheme"}}},
            "options": {"tooltip": {"mode": "multi", "sort": "none"}},
            "targets": [
                {"expr": "sum(hikaricp_connections_active{namespace=\"tutti\"})", "legendFormat": "Active", "refId": "A"},
                {"expr": "sum(hikaricp_connections_idle{namespace=\"tutti\"})", "legendFormat": "Idle", "refId": "B"},
                {"expr": "sum(hikaricp_connections_max{namespace=\"tutti\"})", "legendFormat": "Max Limit", "refId": "C"}
            ]
        },
        {
            "title": "JVM 힙 메모리",
            "type": "timeseries",
            "id": panel_id + 3,
            "gridPos": {"h": 8, "w": 12, "x": 12, "y": 29},
            "datasource": {"type": "prometheus", "uid": "prometheus"},
            "fieldConfig": {"defaults": {"custom": {"drawStyle": "line", "fillOpacity": 20, "gradientMode": "scheme"}, "unit": "bytes"}},
            "options": {"tooltip": {"mode": "multi", "sort": "none"}},
            "targets": [
                {"expr": "sum(jvm_memory_used_bytes{namespace=\"tutti\", area=\"heap\"})", "legendFormat": "Used", "refId": "A"},
                {"expr": "sum(jvm_memory_max_bytes{namespace=\"tutti\", area=\"heap\"})", "legendFormat": "Max Limit", "refId": "B"}
            ]
        }
    ])
    panel_id += 4

    # ── Template Variables: 모듈 선택 ──
    # container 라벨: Loki API /label/container/values 에서 확인된 정확한 이름 사용
    #   main-server, tutti-backend-ai-ai-worker-1, converter-service, cloudflared
    dashboard["templating"]["list"] = [
        {
            "name": "log_module",
            "label": "📋 로그 모듈",
            "type": "custom",
            "query": ", ".join([
                '📝 Main Server (App) : {container="main-server"} != "HTTP ("',
                '🔍 Main Server (API) : {container="main-server"} |= "HTTP ("',
                '🧠 AI Server : {container="tutti-backend-ai-ai-worker-1"} != "복구할 Pending 메시지 없음"',
                '🔄 Converter : {container="converter-service"} != "GET /health"',
                '🌐 Cloudflared : {container="cloudflared"}',
            ]),
            "current": {
                "text": "📝 Main Server (App)",
                "value": '{container="main-server"} != "HTTP ("'
            },
            "options": [
                {"text": "📝 Main Server (App)",  "value": '{container="main-server"} != "HTTP ("', "selected": True},
                {"text": "🔍 Main Server (API)",  "value": '{container="main-server"} |= "HTTP ("', "selected": False},
                {"text": "🧠 AI Server",          "value": '{container="tutti-backend-ai-ai-worker-1"} != "복구할 Pending 메시지 없음"', "selected": False},
                {"text": "🔄 Converter",           "value": '{container="converter-service"} != "GET /health"', "selected": False},
                {"text": "🌐 Cloudflared",         "value": '{container="cloudflared"}', "selected": False},
            ],
            "hide": 0,
            "includeAll": False,
            "multi": False,
        }
    ]


    # Row 4: Live Logs — 포커스 뷰
    add_row("📋 실시간 로그 (↑ 상단 드롭다운에서 모듈 선택 / 패널 내 Ctrl+F로 검색)", 40)

    loki_ds = {"type": "loki", "uid": "loki"}
    log_opts = {"showTime": True, "showLabels": True, "wrapLogMessage": True,
                "enableLogDetails": True, "sortOrder": "Descending", "prettifyLogMessage": True}

    # 포커스 패널 — $log_module이 쿼리 자체 (|= 없이 깨끗한 쿼리)
    dashboard["panels"].append({
        "title": "🔎 실시간 로그 포커스 뷰",
        "type": "logs",
        "id": panel_id,
        "gridPos": {"h": 25, "w": 24, "x": 0, "y": 41},
        "datasource": loki_ds,
        "options": log_opts,
        "targets": [{
            "expr": '$log_module',
            "refId": "A"
        }],
    })
    panel_id += 1

    # 출력 경로: 스크립트 위치 기준 상대 경로
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_path = os.path.join(script_dir, 'tutti-overview-cm.yaml')

    indented_json = "\n    ".join(json.dumps(dashboard, indent=4, ensure_ascii=False).splitlines())
    yaml_template = f"""apiVersion: v1
kind: ConfigMap
metadata:
  name: tutti-overview-dashboard
  namespace: monitoring
  labels:
    grafana_dashboard: "1"
data:
  tutti-overview.json: |-
    {indented_json}
"""
    
    with open(output_path, 'w') as f:
        f.write(yaml_template)
    
    print(f"✅ Dashboard ConfigMap 생성 완료: {output_path}")

if __name__ == "__main__":
    create_dashboard()
