import pytest

def test_get_instrument_categories(base_url, session):
    res = session.get(f"{base_url}/instruments/categories")
    assert res.status_code == 200
    data = res.json()
    assert data["isSuccess"] is True
    categories = data.get("result", [])
    assert len(categories) > 0
    # generatable 속성이 있는지 확인
    for cat in categories:
        assert "generatable" in cat
        assert "representativeProgram" in cat

def test_get_instruments(base_url, session):
    res = session.get(f"{base_url}/instruments")
    assert res.status_code == 200
    data = res.json()
    assert data["isSuccess"] is True
    instruments = data.get("result", {}).get("instruments", [])
    assert len(instruments) > 0
    # 악기 데이터 구조 확인
    for inst in instruments:
        assert "midiProgram" in inst
        assert "categoryId" in inst
