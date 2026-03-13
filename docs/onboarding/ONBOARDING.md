# Tutti Main Server — 개발자 온보딩 매뉴얼

> 🎯 이 문서는 새로운 팀원이 로컬 환경을 설정하고, 프로젝트 구조를 이해하며, 배포 파이프라인의 전체 흐름을 파악할 수 있도록 안내합니다.
>
> **Note**: AI 서버(FastAPI)는 별도 레포지토리에서 관리됩니다.

---

## 📋 목차

1. [사전 요구사항](#1-사전-요구사항)
2. [로컬 환경 설정](#2-로컬-환경-설정)
3. [서버 실행](#3-서버-실행)
4. [Docker Compose로 실행](#4-docker-compose로-실행)
5. [배포 파이프라인 이해](#5-배포-파이프라인-이해)
6. [AI 서버 연동 테스트](#6-ai-서버-연동-테스트)
7. [트러블슈팅 가이드](#7-트러블슈팅-가이드)

---

## 1. 사전 요구사항

### 필수 설치 프로그램

| 도구               | 버전                    | 설치 확인                  |
| ------------------ | ----------------------- | -------------------------- |
| **Java JDK**       | 21+                     | `java --version`           |
| **Gradle**         | 8.x (또는 Wrapper 사용) | `./gradlew --version`      |
| **Docker**         | 24+                     | `docker --version`         |
| **Docker Compose** | 2.x+                    | `docker compose version`   |
| **Git**            | 2.x+                    | `git --version`            |
| **kubectl**        | 최신                    | `kubectl version --client` |
| **gcloud CLI**     | 최신                    | `gcloud --version`         |

### macOS 빠른 설치

```bash
# Homebrew로 일괄 설치
brew install openjdk@21 docker kubectl google-cloud-sdk

# Java 심볼릭 링크
sudo ln -sfn $(brew --prefix openjdk@21)/libexec/openjdk.jdk \
  /Library/Java/JavaVirtualMachines/openjdk-21.jdk
```

---

## 2. 로컬 환경 설정

### 2.1 리포지토리 클론

```bash
git clone https://github.com/Project-Tutti/tutti-backend.git
cd tutti-backend
```

### 2.2 로컬 PostgreSQL 실행

**Option A: Docker로 PostgreSQL만 실행** (권장)

```bash
docker run -d \
  --name tutti-postgres \
  -e POSTGRES_DB=tutti_dev \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:16-alpine
```

**Option B: docker-compose로 PostgreSQL 실행**

```bash
docker compose up -d postgres
```

**Option C: Supabase 직접 연결**

- Supabase 프로젝트의 Connection String을 환경변수로 설정

### 2.3 환경변수 설정

기본값이 `application.yml`에 설정되어 있어 로컬에서는 대부분 옵션입니다.

```bash
# 필요시 환경변수 오버라이드
export DB_USERNAME=postgres
export DB_PASSWORD=postgres
export JWT_SECRET=dev-secret-key-for-local-development-only-32chars
export AI_SERVER_URL=http://localhost:8000   # AI 서버가 로컬에서 실행 중인 경우
```

---

## 3. 서버 실행

### 3.1 Spring Boot Main Server

```bash
cd main-server

# 의존성 다운로드 & 빌드
./gradlew build -x test

# 로컬 프로필로 실행
./gradlew bootRun

# 또는 직접 JAR 실행
java -jar build/libs/tutti-main-server.jar --spring.profiles.active=local
```

✅ 정상 실행 확인:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

### 3.2 AI 서버 연동 (선택사항)

AI 서버는 별도 레포지토리에서 관리됩니다. 로컬에서 AI 서버가 함께 필요한 경우:

1. AI 서버 레포를 별도로 클론하여 실행
2. `AI_SERVER_URL` 환경변수를 AI 서버 주소로 설정
3. AI 서버 없이도 메인 서버의 인증/프로젝트 CRUD 기능은 독립적으로 테스트 가능

---

## 4. Docker Compose로 실행

PostgreSQL + Main Server를 한 번에 실행합니다.

```bash
# 프로젝트 루트에서
docker compose up --build

# 백그라운드 실행
docker compose up -d --build

# 로그 확인
docker compose logs -f main-server

# 종료
docker compose down

# 데이터 볼륨까지 삭제
docker compose down -v
```

### 서비스 접근

| 서비스             | URL                                   | 설명            |
| ------------------ | ------------------------------------- | --------------- |
| Main Server        | http://localhost:8080                 | Spring Boot API |
| Main Server Health | http://localhost:8080/actuator/health | 헬스 체크       |
| PostgreSQL         | localhost:5432                        | DB 직접 접속    |

---

## 5. 배포 파이프라인 이해

### 5.1 전체 흐름

```
┌──────────┐     ┌──────────┐     ┌────────────────┐     ┌─────────┐
│  코드     │     │  GitHub  │     │  Artifact      │     │  GKE    │
│  Push     │────▶│  Actions │────▶│  Registry      │────▶│  Cluster│
│           │     │  (CI/CD) │     │  (Docker 이미지)│     │         │
└──────────┘     └──────────┘     └────────────────┘     └─────────┘
```

### 5.2 CI 파이프라인 (PR → merge)

1. **PR 생성**: `dev` 또는 `main` 브랜치로 PR
2. **자동 실행**: `main-server/` 경로 변경 시 `ci-main.yml` 트리거
3. **테스트**: `./gradlew test` 실행
4. **빌드 & 푸시**: (main 브랜치 merge 시만) Docker 이미지 빌드 → Artifact Registry 푸시

### 5.3 CD 파이프라인 (main merge → 배포)

1. CI 워크플로우 성공 후 `cd-deploy.yml` 자동 트리거
2. GKE 클러스터 인증 (Workload Identity Federation)
3. `kubectl set image`로 롤링 업데이트
4. `kubectl rollout status`로 배포 성공 확인

### 5.4 수동 배포

GitHub Actions 탭에서 `CD - Deploy to GKE` 워크플로우를 수동 실행할 수 있습니다.

---

## 6. AI 서버 연동 테스트

### 6.1 기본 API 테스트 (AI 서버 없이 가능)

```bash
# 1. 회원가입
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "name": "TestUser",
    "password": "Password123!"
  }'

# 2. 로그인 (토큰 획득)
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Password123!"
  }' | python3 -c "import sys,json; print(json.load(sys.stdin)['result']['accessToken'])")

echo "Token: $TOKEN"

# 3. 프로필 조회
curl http://localhost:8080/api/users/me \
  -H "Authorization: Bearer $TOKEN"

# 4. 보관함 목록 조회
curl "http://localhost:8080/api/library?page=0&size=10" \
  -H "Authorization: Bearer $TOKEN"
```

### 6.2 AI 서버 연동 테스트 (AI 서버 실행 필요)

```bash
# 프로젝트 생성 (MIDI 파일 업로드 → AI 서버로 편곡 요청)
curl -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@sample.mid" \
  -F 'request={"name":"테스트 프로젝트","versionName":"Ver 1"};type=application/json'

# SSE 진행률 스트림 수신
curl -N http://localhost:8080/api/projects/1/1/status \
  -H "Authorization: Bearer $TOKEN" \
  -H "Accept: text/event-stream"
```

---

## 7. 트러블슈팅 가이드

### 7.1 자주 발생하는 문제

#### ❌ Main Server 시작 실패: DB 연결 오류

```
Connection refused to localhost:5432
```

**해결:**

```bash
# PostgreSQL이 실행 중인지 확인
docker ps | grep postgres

# 실행 중이 아니라면
docker start tutti-postgres

# 또는 docker-compose로
docker compose up -d postgres
```

#### ❌ Gradle 빌드 실패

```
Could not resolve dependencies
```

**해결:**

```bash
# Gradle 캐시 정리 후 재빌드
cd main-server
./gradlew clean build --refresh-dependencies -x test
```

#### ❌ JWT 토큰 오류

```
401 Unauthorized - INVALID_TOKEN
```

**해결:**

- 토큰 만료 확인 (Access Token은 1시간 유효)
- `jwt.secret` 값이 환경변수와 일치하는지 확인
- 로그인 재시도하여 새 토큰 발급

#### ❌ AI 서버 통신 실패

```
Connection refused to http://localhost:8000
```

**해결:**

1. AI 서버가 별도 레포에서 실행 중인지 확인
2. `AI_SERVER_URL` 환경변수가 올바르게 설정되었는지 확인
3. AI 서버 없이도 인증/프로젝트 CRUD는 테스트 가능

### 7.2 유용한 디버깅 명령어

```bash
# ── 로컬 디버깅 ──
# Spring Boot 디버그 모드
cd main-server
./gradlew bootRun --debug-jvm   # Remote debug: localhost:5005

# DB 직접 접속
psql -h localhost -p 5432 -U postgres -d tutti_dev

# ── Docker 디버깅 ──
docker compose logs -f --tail 100 main-server
docker compose exec main-server sh

# ── K8s 디버깅 ──
kubectl get pods -n tutti
kubectl logs -f deployment/main-server -n tutti
kubectl describe pod <pod-name> -n tutti
kubectl exec -it <pod-name> -n tutti -- sh
```

---

## 📝 프로젝트 구조 요약

```
tutti-backend/
├── main-server/     👈 Spring Boot (Java 21) — API, 인증, CRUD
├── k8s/             👈 Kubernetes 매니페스트
├── .github/         👈 CI/CD 파이프라인
├── docs/            👈 아키텍처 & 온보딩 문서
└── docker-compose.yml  👈 로컬 개발 실행 (PostgreSQL + Main Server)
```

궁금한 점이나 문제가 있다면 Slack `#tutti-backend` 채널에 질문해 주세요! 🙌
