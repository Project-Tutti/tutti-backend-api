# Tutti Main Server — Architecture & Infrastructure Design

> **Version**: 1.1  
> **Last Updated**: 2026-02-18  
> **Authors**: Platform Engineering Team

---

## 📋 Table of Contents

1. [프로젝트 & 인프라 디렉토리 구조](#1-프로젝트--인프라-디렉토리-구조)
2. [아키텍처 및 통신 설계 포인트](#2-아키텍처-및-통신-설계-포인트)
3. [핵심 보일러플레이트 코드 (뼈대)](#3-핵심-보일러플레이트-코드-뼈대)
4. [액션 플랜 (To-Do List)](#4-액션-플랜-to-do-list)

---

## 1. 프로젝트 & 인프라 디렉토리 구조

> **Note**: AI 서버(FastAPI)는 별도 레포지토리에서 관리됩니다. 이 레포지토리는 Spring Boot 메인 서버와 관련 인프라에 집중합니다.

### 1.1 전체 트리

```
tutti-backend/
├── .github/
│   └── workflows/
│       ├── ci-main.yml              # Spring Boot CI (test, build, push)
│       └── cd-deploy.yml            # GKE 배포 워크플로우
│
├── main-server/                     # ── Spring Boot 메인 서버 ──
│   ├── build.gradle
│   ├── settings.gradle
│   ├── Dockerfile
│   ├── gradle/
│   │   └── wrapper/
│   └── src/
│       ├── main/
│       │   ├── java/com/tutti/server/
│       │   │   ├── TuttiApplication.java
│       │   │   │
│       │   │   ├── global/                    # 🌐 전역 설정 & 공통 모듈
│       │   │   │   ├── config/
│       │   │   │   │   ├── SecurityConfig.java
│       │   │   │   │   ├── WebConfig.java
│       │   │   │   │   ├── JpaConfig.java
│       │   │   │   │   └── RestClientConfig.java
│       │   │   │   ├── common/
│       │   │   │   │   ├── ApiResponse.java        # 공통 응답 래퍼
│       │   │   │   │   ├── PageResponse.java        # 페이지네이션 응답
│       │   │   │   │   └── BaseTimeEntity.java      # JPA Auditing
│       │   │   │   ├── error/
│       │   │   │   │   ├── ErrorCode.java           # 에러 코드 Enum
│       │   │   │   │   ├── BusinessException.java   # 커스텀 예외
│       │   │   │   │   └── GlobalExceptionHandler.java
│       │   │   │   ├── auth/
│       │   │   │   │   ├── jwt/
│       │   │   │   │   │   ├── JwtTokenProvider.java
│       │   │   │   │   │   ├── JwtAuthenticationFilter.java
│       │   │   │   │   │   └── JwtProperties.java
│       │   │   │   │   └── oauth/
│       │   │   │   │       └── GoogleOAuthClient.java
│       │   │   │   └── util/
│       │   │   │       └── SecurityUtil.java
│       │   │   │
│       │   │   ├── domain/                    # 📦 도메인별 패키지 (DDD-Lite)
│       │   │   │   ├── auth/
│       │   │   │   │   ├── controller/
│       │   │   │   │   │   └── AuthController.java
│       │   │   │   │   ├── dto/
│       │   │   │   │   │   ├── request/
│       │   │   │   │   │   │   ├── SignupRequest.java
│       │   │   │   │   │   │   ├── LoginRequest.java
│       │   │   │   │   │   │   ├── SocialLoginRequest.java
│       │   │   │   │   │   │   └── TokenRefreshRequest.java
│       │   │   │   │   │   └── response/
│       │   │   │   │   │       ├── AuthResponse.java
│       │   │   │   │   │       └── TokenResponse.java
│       │   │   │   │   └── service/
│       │   │   │   │       └── AuthService.java
│       │   │   │   │
│       │   │   │   ├── user/
│       │   │   │   │   ├── controller/
│       │   │   │   │   │   └── UserController.java
│       │   │   │   │   ├── dto/
│       │   │   │   │   │   └── response/
│       │   │   │   │   │       └── UserProfileResponse.java
│       │   │   │   │   ├── entity/
│       │   │   │   │   │   └── User.java
│       │   │   │   │   ├── repository/
│       │   │   │   │   │   └── UserRepository.java
│       │   │   │   │   └── service/
│       │   │   │   │       └── UserService.java
│       │   │   │   │
│       │   │   │   ├── project/
│       │   │   │   │   ├── controller/
│       │   │   │   │   │   └── ProjectController.java
│       │   │   │   │   ├── dto/
│       │   │   │   │   │   ├── request/
│       │   │   │   │   │   │   ├── ProjectCreateRequest.java
│       │   │   │   │   │   │   ├── ProjectRenameRequest.java
│       │   │   │   │   │   │   ├── RegenerateRequest.java
│       │   │   │   │   │   │   └── VersionRenameRequest.java
│       │   │   │   │   │   └── response/
│       │   │   │   │   │       ├── ProjectDetailResponse.java
│       │   │   │   │   │       ├── ProjectCreateResponse.java
│       │   │   │   │   │       ├── TrackInfoResponse.java
│       │   │   │   │   │       └── VersionResponse.java
│       │   │   │   │   ├── entity/
│       │   │   │   │   │   ├── Project.java
│       │   │   │   │   │   ├── ProjectVersion.java
│       │   │   │   │   │   ├── ProjectTrack.java
│       │   │   │   │   │   └── VersionMapping.java
│       │   │   │   │   ├── repository/
│       │   │   │   │   │   ├── ProjectRepository.java
│       │   │   │   │   │   └── ProjectVersionRepository.java
│       │   │   │   │   └── service/
│       │   │   │   │       ├── ProjectService.java
│       │   │   │   │       └── ArrangementService.java  # AI 서버 연동
│       │   │   │   │
│       │   │   │   └── library/
│       │   │   │       ├── controller/
│       │   │   │       │   └── LibraryController.java
│       │   │   │       ├── dto/
│       │   │   │       │   └── response/
│       │   │   │       │       └── LibraryListResponse.java
│       │   │   │       └── service/
│       │   │   │           └── LibraryService.java
│       │   │   │
│       │   │   └── infra/                     # 🔌 외부 인프라 연동
│       │   │       ├── ai/
│       │   │       │   ├── AiClient.java            # AI 서버 REST 클라이언트
│       │   │       │   ├── dto/
│       │   │       │   │   ├── AiArrangeRequest.java
│       │   │       │   │   └── AiArrangeResponse.java
│       │   │       │   └── AiClientProperties.java
│       │   │       └── storage/
│       │   │           └── FileStorageService.java   # GCS/로컬 파일 저장
│       │   │
│       │   └── resources/
│       │       └── application.yml             # 환경별 프로필 포함 (local/dev/prod)
│       │
│       └── test/
│           └── java/com/tutti/server/
│               ├── domain/
│               │   ├── auth/
│               │   └── project/
│               └── global/
│
├── k8s/                             # ── Kubernetes 매니페스트 ──
│   ├── base/
│   │   ├── kustomization.yaml
│   │   ├── namespace.yaml
│   │   ├── main-server/
│   │   │   ├── deployment.yaml
│   │   │   ├── service.yaml
│   │   │   └── hpa.yaml
│   │   └── ingress.yaml
│   └── secrets/
│       └── secrets-template.yaml
│
├── docs/                            # ── 문서 ──
│   ├── architecture/
│   │   └── ARCHITECTURE.md          # (이 문서)
│   └── onboarding/
│       └── ONBOARDING.md            # 개발자 온보딩 매뉴얼
│
├── local_docs/                      # Notion 내보내기 / API 명세 원본
│
├── docker-compose.yml               # 로컬 개발용 (PostgreSQL + Main Server)
├── .gitignore
├── LICENSE
└── README.md
```

### 1.2 핵심 디렉토리 역할 설명

| 디렉토리                  | 역할                                                                                           |
| ------------------------- | ---------------------------------------------------------------------------------------------- |
| `.github/workflows/`      | GitHub Actions CI/CD 파이프라인. Main Server 전용 CI + GKE 배포 CD                             |
| `main-server/`            | Spring Boot 메인 API 서버. 인증, 프로젝트 CRUD, SSE, 파일 관리 전담                            |
| `main-server/.../global/` | 공통 설정(Security, JPA), 에러 핸들링, JWT 인증 필터 등 횡단 관심사                            |
| `main-server/.../domain/` | 도메인별 패키지 (auth, user, project, library). 각 도메인은 controller→service→repository 구조 |
| `main-server/.../infra/`  | 외부 시스템 연동 계층. AI 서버 HTTP 호출, 파일 스토리지 등                                     |
| `k8s/`                    | Kustomize 기반 K8s 매니페스트. Main Server Deployment, Service, HPA, Ingress                   |
| `docs/`                   | 아키텍처 설계 문서 및 개발자 온보딩 가이드                                                     |

### 1.3 AI 서버와의 관계

AI 서버(FastAPI)는 **별도 레포지토리**에서 독립적으로 개발/배포됩니다. 메인 서버는 `infra/ai/AiClient.java`를 통해 HTTP REST로 AI 서버와 통신합니다.

- AI 서버 URL은 환경변수 `AI_SERVER_URL`로 설정
- 로컬: `http://localhost:8000`
- GKE: `http://ai-server.tutti.svc.cluster.local:8000` (K8s 내부 DNS)

---

## 2. 아키텍처 및 통신 설계 포인트

### 2.1 시스템 아키텍처 개요

```
┌─────────────────────────────────────────────────────────────────┐
│                         GKE Cluster                             │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                    Ingress (GCE L7)                     │    │
│  │              api.tutti.asia → main-server               │    │
│  └──────────────────────┬──────────────────────────────────┘    │
│                         │                                       │
│  ┌──────────────────────▼──────────────────────────────────┐    │
│  │              main-server (Spring Boot)                   │    │
│  │   ┌─────────────────────────────────────────────────┐   │    │
│  │   │  Auth │ Project │ Library │ SSE Controller      │   │    │
│  │   └──────────┬──────────────────────────────────────┘   │    │
│  │              │                                           │    │
│  │   ┌──────────▼──────────────────────────────────────┐   │    │
│  │   │  ArrangementService → AiClient (HTTP/Callback)  │   │    │
│  │   └──────────┬──────────────────────────────────────┘   │    │
│  └──────────────┼──────────────────────────────────────────┘    │
│                 │ ClusterIP (내부 통신)                          │
│                 ▼                                               │
│       ai-server (별도 레포/배포)                                 │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │                  Supabase (PostgreSQL)                   │    │
│  │           External Managed Database (Direct Connect)    │    │
│  └─────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Spring Boot ↔ FastAPI 비동기 통신 설계

AI 편곡 추론은 수 초~수십 초가 소요될 수 있으므로, **비동기 콜백 패턴**을 채택합니다.

```
┌────────────┐      ① POST /api/projects          ┌─────────────┐
│   Client    │ ──────────────────────────────────▶ │ Main Server │
│  (Frontend) │ ◀────────────────────────────────── │(Spring Boot)│
│             │      202 Accepted {status:pending}  │             │
│             │                                     │             │
│             │      ⑤ SSE: progress stream         │             │
│             │ ◀═══════════════════════════════════ │             │
└────────────┘                                     └──────┬──────┘
                                                          │
                    ② POST /api/v1/arrange                │
                       (callbackUrl 포함)                  │
                                                          ▼
                                                   ┌─────────────┐
                                                   │  AI Server  │
                                                   │  (FastAPI)  │
                                                   │  [별도 레포]  │
                                                   │             │
                                                   │ ③ 추론 실행  │
                                                   │   (비동기)   │
                                                   │             │
                                                   │ ④ POST 콜백  │
                                                   │   → Main    │
                                                   └─────────────┘
```

#### 통신 흐름 상세

| 단계 | 행위자        | 동작                                    | 설명                                                                  |
| ---- | ------------- | --------------------------------------- | --------------------------------------------------------------------- |
| ①    | Client → Main | `POST /api/projects`                    | MIDI 파일 업로드 + 편곡 요청                                          |
| ②    | Main → AI     | `POST /api/v1/arrange`                  | MIDI 바이너리 + 매핑 정보 + `callbackUrl` 전달. Main은 즉시 응답 반환 |
| ③    | AI Server     | 내부 처리                               | BackgroundTask로 추론 실행                                            |
| ④    | AI → Main     | `POST /internal/callback/arrange`       | 완료/실패 결과를 Main 서버로 콜백                                     |
| ⑤    | Main → Client | `SSE /api/projects/{id}/{verId}/status` | 클라이언트는 SSE로 실시간 진행률 수신                                 |

#### 핵심 설계 결정

1. **동기 호출 회피**: Main → AI 호출 시 HTTP 타임아웃 문제를 원천 차단
2. **콜백 URL 패턴**: AI 서버가 처리 완료 후 Main 서버의 내부 엔드포인트로 결과 전송
3. **진행률 중계**: AI 서버가 DB에 진행률을 기록하면, Main 서버가 SSE로 클라이언트에 중계
4. **멱등성**: 콜백은 재시도 가능하도록 멱등하게 설계 (versionId 기준)

### 2.3 GKE 리소스 관리

```yaml
# GKE 노드 풀 전략 (Main Server)
Node Pool:
  ┌─────────────────────────────────────────────────────┐
  │  default-pool (CPU)                                 │
  │  ├── Machine Type: e2-standard-4 (4 vCPU, 16GB)    │
  │  ├── Autoscaling: 2-10 nodes                        │
  │  ├── Workloads: main-server, ingress                │
  │  └── Taint: none                                    │
  └─────────────────────────────────────────────────────┘
```

> **GPU 노드 풀**: AI 서버용 GPU 노드 풀은 AI 서버 레포지토리의 인프라 설정에서 관리합니다.

---

## 3. 핵심 보일러플레이트 코드 (뼈대)

> 각 파일의 실제 코드는 `main-server/` 디렉토리에 생성되어 있습니다.

### 3.1 Spring Boot 핵심 파일

- **`build.gradle`**: Spring Boot 3.4 + Java 21, Spring Security, Spring Data JPA (PostgreSQL), WebClient, JWT (jjwt), Validation
- **`application.yml`**: Supabase PostgreSQL 연결, JWT 설정, AI 서버 URL, 파일 업로드 제한, `local`/`dev`/`prod` 프로필 분리
- **`SecurityConfig`**: JWT 필터 체인, 공개/보호 엔드포인트 분리, 내부 콜백 엔드포인트 허용
- **`ApiResponse`**: 명세서의 `{isSuccess, message, result}` 통합 응답 래퍼
- **`ErrorCode`**: API 명세서의 모든 에러 코드를 Enum으로 정의 (22개)

### 3.2 인프라 파일

- **`Dockerfile`**: Multi-stage 빌드, JRE Alpine 슬림 이미지, non-root 사용자
- **`deployment.yaml`**: 리소스 제한, Startup/Liveness/Readiness Probe, Secret 연동
- **`GitHub Actions`**: PR 검증 → Docker 빌드/푸시 → GKE 롤링 배포

---

## 4. 액션 플랜 (To-Do List)

### Phase 1: 로컬 개발 환경 구축 (Week 1)

| #   | Task                                             | Priority    | Owner   |
| --- | ------------------------------------------------ | ----------- | ------- |
| 1.1 | Spring Boot 프로젝트 Gradle Wrapper 초기화       | 🔴 Critical | Backend |
| 1.2 | Supabase 프로젝트 생성 및 DB 스키마 마이그레이션 | 🔴 Critical | Backend |
| 1.3 | `docker-compose.yml`로 로컬 PostgreSQL 실행 확인 | 🔴 Critical | Backend |
| 1.4 | `application-local.yml`로 로컬 DB 연결 테스트    | 🟡 High     | Backend |

### Phase 2: 핵심 골격 구현 (Week 2)

| #   | Task                                                                    | Priority    | Owner   |
| --- | ----------------------------------------------------------------------- | ----------- | ------- |
| 2.1 | 공통 응답 래퍼 (`ApiResponse`, `ErrorCode`, `GlobalExceptionHandler`)   | 🔴 Critical | Backend |
| 2.2 | JWT 인증 파이프라인 (토큰 발급/검증/필터)                               | 🔴 Critical | Backend |
| 2.3 | Auth 도메인 API 구현 (회원가입, 로그인, 소셜로그인, 토큰갱신, 로그아웃) | 🔴 Critical | Backend |
| 2.4 | User 도메인 API 구현 (프로필 조회, 회원 탈퇴)                           | 🟡 High     | Backend |

### Phase 3: 비즈니스 로직 골격 (Week 3)

| #   | Task                                                                  | Priority    | Owner   |
| --- | --------------------------------------------------------------------- | ----------- | ------- |
| 3.1 | Project 도메인 Entity/Repository 구현                                 | 🔴 Critical | Backend |
| 3.2 | 프로젝트 생성 API (MIDI 업로드 + AI 서버 연동)                        | 🔴 Critical | Backend |
| 3.3 | SSE 진행률 조회 엔드포인트 구현                                       | 🔴 Critical | Backend |
| 3.4 | AI 서버 콜백 수신 내부 엔드포인트 구현 (`/internal/callback/arrange`) | 🔴 Critical | Backend |
| 3.5 | 프로젝트 조회/수정/삭제 API 구현                                      | 🟡 High     | Backend |
| 3.6 | 보관함 목록 조회 API (페이지네이션 + 검색 + 정렬)                     | 🟡 High     | Backend |
| 3.7 | 악보 데이터 조회 / 파일 다운로드 API                                  | 🟢 Medium   | Backend |

### Phase 4: Dockerizing & CI 파이프라인 (Week 4)

| #   | Task                                               | Priority    | Owner |
| --- | -------------------------------------------------- | ----------- | ----- |
| 4.1 | Spring Boot Dockerfile 검증 & 최적화               | 🔴 Critical | Infra |
| 4.2 | GCP Artifact Registry 리포지토리 생성              | 🔴 Critical | Infra |
| 4.3 | GitHub Actions CI 워크플로우 (test → build → push) | 🔴 Critical | Infra |

### Phase 5: K8s 매니페스트 & GKE 배포 (Week 5)

| #   | Task                                                    | Priority    | Owner |
| --- | ------------------------------------------------------- | ----------- | ----- |
| 5.1 | GKE 클러스터 생성 (CPU 노드 풀)                         | 🔴 Critical | Infra |
| 5.2 | K8s Namespace, Secret 설정                              | 🔴 Critical | Infra |
| 5.3 | Main Server Deployment + Service + HPA                  | 🔴 Critical | Infra |
| 5.4 | Ingress 설정 (도메인, TLS)                              | 🔴 Critical | Infra |
| 5.5 | GitHub Actions CD 워크플로우 (GKE 배포)                 | 🔴 Critical | Infra |
| 5.6 | AI 서버 팀과 통신 인터페이스 합의 (API 스펙, 콜백 규약) | 🟡 High     | All   |

### Phase 6: 안정화 & 모니터링 (Week 6+)

| #   | Task                                          | Priority    | Owner |
| --- | --------------------------------------------- | ----------- | ----- |
| 6.1 | AI 서버 연동 통합 테스트 (E2E)                | 🔴 Critical | All   |
| 6.2 | 로깅 체계 구축 (Cloud Logging)                | 🟡 High     | Infra |
| 6.3 | 모니터링 대시보드 (Cloud Monitoring)          | 🟡 High     | Infra |
| 6.4 | 보안 점검 (OWASP, 시크릿 관리, 네트워크 정책) | 🟡 High     | Infra |
