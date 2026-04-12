import pytest
import uuid
import requests
from conftest import BASE_URL

def test_login_success(base_url, session, test_user):
    payload = {
        "email": test_user["email"],
        "password": test_user["password"]
    }
    res = session.post(f"{base_url}/auth/login", json=payload)
    data = res.json()
    assert res.status_code == 200
    assert data["isSuccess"] is True
    assert "accessToken" in data["result"]
    assert "refreshToken" in data["result"]

def test_login_failure(base_url, session, test_user):
    payload = {
        "email": test_user["email"],
        "password": "WrongPassword123!"
    }
    res = session.post(f"{base_url}/auth/login", json=payload)
    data = res.json()
    assert data["isSuccess"] is False
    # Check for errorCode location based on GlobalExceptionHandler
    error_code = data.get("result", {}).get("errorCode") or data.get("errorCode")
    assert error_code in ["UNAUTHORIZED", "INVALID_CREDENTIALS", "ACCESS_DENIED", "USER_NOT_FOUND"], f"Unexpected data: {data}"

def test_signup_duplicate_email(base_url, session, test_user):
    # test_user에 사용된 이메일로 다시 가입 시도
    payload = {
        "email": test_user["email"],
        "password": "NewPassword123!",
        "name": "Another Name"
    }
    res = session.post(f"{base_url}/auth/signup", json=payload)
    data = res.json()
    assert data["isSuccess"] is False
    # 중복 이메일 예외 코드가 반환되어야 함
    error_code = data.get("result", {}).get("errorCode") or data.get("errorCode")
    assert error_code == "EMAIL_ALREADY_EXISTS", f"Unexpected data: {data}"
