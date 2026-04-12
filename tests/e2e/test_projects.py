import pytest
import time
import json

@pytest.mark.ai_e2e
def test_project_happy_path(base_url, session, auth_headers, dummy_midi_file):
    """
    프로젝트 생성 -> 41번 비올라 편곡 요청 -> 폴링 -> 완료 -> 다운로드 링크 확인
    """
    
    # 1. 미디 업로드 및 프로젝트 생성
    project_payload = {
        "name": "E2E Test Project",
        "versionName": "v1",
        "instrumentId": 41, # 비올라
        "genre": "CLASSICAL",
        "temperature": 1.0,
        "tracks": [
            {
                "trackIndex": 0,
                "name": "Dummy Track",
                "midiProgram": 0,
                "isDrum": False,
                "sourceInstrumentId": 0
            }
        ],
        "mappings": [
            {
                "trackIndex": 0,
                "targetInstrumentId": 40 # 카테고리만 허용됨
            }
        ]
    }
    
    # 실제 요청 (multipart form-data 와 JSON 본문 복합)
    with open(dummy_midi_file, "rb") as f:
        multipart_data = [
            ("file", ("dummy.mid", f, "audio/midi")),
            ("request", (None, json.dumps(project_payload), "application/json"))
        ]
        res = session.post(f"{base_url}/projects", files=multipart_data, headers=auth_headers)

    assert res.status_code in [200, 201], f"Project Create Failed: {res.text}"
    result = res.json().get("result", {})
    project_id = result.get("projectId")
    version_id = result.get("versionId")
    assert project_id is not None
    assert version_id is not None
    
    # 2. 폴링 (Polling)
    max_retries = 150 # 최대 150 * 2 = 300초(5분) 대기
    status = "processing"
    
    for _ in range(max_retries):
        poll_res = session.get(f"{base_url}/projects/{project_id}", headers=auth_headers)
        assert poll_res.status_code == 200
        poll_data = poll_res.json()
        
        # 최신 버전 확인
        versions = poll_data["result"].get("versions", [])
        assert len(versions) > 0
        latest_v = versions[0]
        status = latest_v["status"]
        
        if status in ["complete", "failed"]:
            break
            
        time.sleep(2)
        
    assert status == "complete", f"AI 편곡 처리 실패: status={status}"
    
    # 3. 다운로드 링크 확인
    dl_res = session.get(f"{base_url}/projects/{project_id}/{version_id}/download?type=midi", headers=auth_headers)
    assert dl_res.status_code == 200, f"다운로드 에러: {dl_res.text}"
    dl_data = dl_res.json()
    assert dl_data["isSuccess"] is True
    # Signed URL이 반환되는지 확인
    assert "signedUrl" in dl_data.get("result", {})


@pytest.mark.ai_e2e
def test_project_invalid_mapping_category(base_url, session, auth_headers, dummy_midi_file):
    """
    트랙 매핑에 카테고리가 아닌 일반 악기 번호 (예: 41)를 맵핑값으로 보냈을 때 예외(Validation) 처리 확인
    """
    project_payload = {
        "name": "E2E Invalid Mapping",
        "versionName": "v1",
        "instrumentId": 41, 
        "genre": "CLASSICAL",
        "temperature": 1.0,
        "tracks": [
            {
                "trackIndex": 0,
                "name": "Dummy Track",
                "midiProgram": 0,
                "isDrum": False,
                "sourceInstrumentId": 0
            }
        ],
        "mappings": [
            {
                "trackIndex": 0,
                "targetInstrumentId": 41 # 개별 악기 할당 불가 (카테고리만 허용됨)
            }
        ]
    }
    
    with open(dummy_midi_file, "rb") as f:
        multipart_data = [
            ("file", ("dummy.mid", f, "audio/midi")),
            ("request", (None, json.dumps(project_payload), "application/json"))
        ]
        res = session.post(f"{base_url}/projects", files=multipart_data, headers=auth_headers)

    # 비즈니스 로직 예외(BAD_REQUEST 계열)가 떨어져야 정상 동작
    assert res.status_code in [400, 500] 
    data = res.json()
    assert data["isSuccess"] is False
    # INVALID_MAPPING_INSTRUMENT 와 같은 전용 에러 코드가 있는지 검증 추가 가능
