import os
import pretty_midi
import tempfile

def create_dummy_midi(file_path: str, duration_sec: int = 1):
    """
    1초짜리 단순 C4 노트를 포함하는 더미 MIDI 파일을 지정된 경로에 생성합니다.
    테스트 속도를 높이고 Storage를 절약하기 위한 목적입니다.
    """
    pm = pretty_midi.PrettyMIDI()
    inst = pretty_midi.Instrument(program=0, name="Dummy_Piano")
    
    # 0초부터 duration_sec 만큼 지속되는 C4(60) 노트 생성
    note = pretty_midi.Note(velocity=100, pitch=60, start=0, end=duration_sec)
    inst.notes.append(note)
    
    pm.instruments.append(inst)
    pm.write(file_path)
    
    return file_path
