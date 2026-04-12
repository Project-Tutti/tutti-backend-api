import pytest
import requests

def test_get_my_profile(base_url, session, auth_headers, test_user):
    res = session.get(f"{base_url}/users/me", headers=auth_headers)
    data = res.json()
    assert res.status_code == 200
    assert data["isSuccess"] is True
    assert data["result"]["email"] == test_user["email"]
    assert data["result"]["name"] == test_user["name"]


    
def test_unauthorized_access(base_url, session):
    # 헤더 없이 마이페이지 접근 시도
    res = session.get(f"{base_url}/users/me")
    assert res.status_code in [401, 403]
