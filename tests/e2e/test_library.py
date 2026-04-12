import pytest

def test_fetch_library(base_url, session, auth_headers):
    # 라이브러리 (내 작업물) 정상 조회 테스트
    res = session.get(f"{base_url}/library", headers=auth_headers)
    assert res.status_code == 200
    data = res.json()
    assert data["isSuccess"] is True
    # Projects 목록 확인
    assert "projects" in data.get("result", {})
