import os
import time
import uuid
import pytest
import requests
import tempfile
from utils.midi_gen import create_dummy_midi

BASE_URL = os.getenv("API_BASE_URL", "https://api.tutti.asia/api")

@pytest.fixture(scope="session")
def base_url():
    """API 기본 URL 반환 (세션 스코프)"""
    return BASE_URL

@pytest.fixture(scope="session")
def session():
    """모든 요청에 사용할 글로벌 requests Session 객체"""
    return requests.Session()

@pytest.fixture(scope="session")
def test_user(base_url, session):
    """
    세션 전체에서 사용할 테스트 유저를 회원가입 및 로그인하여 생성합니다.
    테스트 종료 시(teardown) 해당 계정을 삭제합니다.
    """
    unique_id = str(uuid.uuid4())[:8]
    email = f"e2e-test-{unique_id}@tutti.asia"
    password = "TestPassword123!"
    name = f"E2E {unique_id}"

    # 1. 회원가입
    signup_payload = {
        "email": email,
        "password": password,
        "name": name
    }
    signup_res = session.post(f"{base_url}/auth/signup", json=signup_payload).json()
    assert signup_res.get("isSuccess") is True, f"회원가입 실패: {signup_res}"

    # 2. 로그인
    login_payload = {
        "email": email,
        "password": password
    }
    login_res = session.post(f"{base_url}/auth/login", json=login_payload).json()
    assert login_res.get("isSuccess") is True, f"로그인 실패: {login_res}"
    
    token = login_res["result"]["accessToken"]
    
    yield {
        "email": email,
        "password": password,
        "name": name,
        "token": token
    }

    # 3. Teardown (계정 삭제)
    delete_res = session.delete(
        f"{base_url}/users/me",
        headers={"Authorization": f"Bearer {token}"}
    )
    # 삭제가 성공했는지 체크하되, teardown 실패로 다른 테스트가 방해받지 않도록 print만.
    if delete_res.status_code != 200:
        print(f"⚠️ Teardown: 계정 삭제 실패: {delete_res.text}")

@pytest.fixture
def auth_headers(test_user):
    """인증 헤더를 반환하는 픽스처"""
    return {
        "Authorization": f"Bearer {test_user['token']}"
    }

@pytest.fixture
def dummy_midi_file():
    """
    임시 경로에 1초짜리 실제 구조를 갖춘 MIDI 파일을 생성하여 yield 하고
    테스트가 끝나면 자동으로 삭제합니다.
    """
    fd, path = tempfile.mkstemp(suffix=".mid")
    os.close(fd)
    
    create_dummy_midi(path, duration_sec=1)
    
    yield path
    
    if os.path.exists(path):
        os.remove(path)
