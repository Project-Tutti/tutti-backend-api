# Tutti Backend E2E Testing Pipeline

이 디렉토리는 `tutti-backend` API 서버의 안정성을 검증하기 위한 통합 E2E(End-to-End) 테스트 스크립트를 포함하고 있습니다.
`pytest`와 `requests`를 활용하여 실제 환경(또는 로컬 서버)과 파이프라인 전체를 검증합니다.

## 디렉토리 구조

```text
tests/e2e/
├── conftest.py             # 테스트 글로벌 설정 및 Fixture (테스트 유저 생성, 더미 미디 파일 등)
├── README.md               # 현재 문서
├── requirements.txt        # 파이썬 의존성 패키지 목록
├── test_auth.py            # 회원가입, 로그인 및 인증 오류 케이스 검증
├── test_instruments.py     # 악기 카테고리 로드 및 무결성 검증
├── test_library.py         # 내 보관함 및 프로젝트 리스트 조회 검증
├── test_projects.py        # 프로젝트 생성 → AI 처리 → 파일 다운로드 통합 프로세스 검증
├── test_users.py           # 단일 유저 프로필 조회 및 업데이트 검증
└── utils/
    └── midi_gen.py         # 테스트 업로드용 더미 MIDI 파일 동적 생성기 (pretty_midi 사용)
```

## 시작하기 (How to run)

### 1. 테스트 환경 세팅

E2E 테스트 실행을 위해 Python 3.10 이상의 환경이 권장됩니다.
로컬 환경 혹은 테스트용 가상환경(venv)을 활성화한 후 패키지를 설치합니다.

```bash
# 디렉토리 이동
cd tests/e2e

# 의존성 설치
pip install -r requirements.txt
```

### 2. 환경 변수 설정 (대상 서버 정의)

테스트 스크립트는 `API_BASE_URL` 환경 변수를 바라봅니다. 지정되지 않은 경우, 기본적으로 라이브 서버(`https://api.tutti.asia/api`)를 타겟으로 합니다.

**로컬 Spring Boot 서버를 타겟으로 테스트하려면:**
```bash
export API_BASE_URL="http://localhost:8080/api"
```

### 3. 테스트 실행

설치된 `pytest`를 활용하여 전체 테스트 스위트를 실행합니다. 테스트가 시작될 때 `conftest.py`에 의해 무작위 이메일/닉네임을 가진 임시 테스트 계정이 생성되며, 모든 검증 종료 후 서버에서 자동 삭제(Teardown)됩니다.

```bash
# 전체 테스트 실행 (상세 출력)
pytest -v

# 특정 테스트 파일만 실행 (예: 프로젝트 파이프라인)
pytest test_projects.py -v

# 테스트가 실패했을 때 즉시 중단 (Fail fast)
pytest -x -v
```

## 시스템 연동 가이드

### 더미 MIDI 파일 (`utils/midi_gen.py`)
이 테스트 스위트는 `result.mid` 등 실제 모델 크기의 큰 바이너리를 스토리지에 계속 던지지 않도록, Python 스크립트 실행 타이밍에 **1초 길이의 가벼운 더미 MIDI 파일**을 동적으로 생성하여 사용합니다.

### GitHub Actions 통합 (CI)
레포지토리 최상단의 `.github/workflows/ci-e2e.yml` 워크플로우에 본 파이프라인이 연동되어 있습니다. `main` 또는 `develop` 브랜치에 코드가 푸시되거나 Pull Request 가 생성될 때, 가상의 Ubuntu 환경에서 본 테스트들이 자동으로 구동되어 무결성을 검증합니다.

## 주의 사항
* 현재 `API_BASE_URL`이 Production(`api.tutti.asia`)을 향한 상태에서 새 기능(예: 41번 악기 생성을 지원하는 새로운 API 구조)을 테스트할 경우 서버와 스크립트 기능의 눈높이가 맞지 않아 패스하지 못할 수 있습니다.
* Live/Prod 서버를 테스트하는 것은 권장되지 않으나 현재 지원하고 있으며 임시 테스트 계정 데이터만 넣었다가 즉시 삭제하므로 프로덕션 데이터는 건드리지 않습니다.
* 추후 프론트엔드 연동과 함께 `docker-compose` 환경 내에서 격리된 DB를 상대로 동작하게끔 고도화할 수 있습니다.
