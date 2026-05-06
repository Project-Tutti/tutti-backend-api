-- V4: project_versions 테이블에 MP3 결과 파일 경로 컬럼 추가
-- 기존 COMPLETE 버전들은 NULL이 되며, 프론트엔드에서 MP3 다운로드 시 "파일 없음" 응답으로 정상 처리됩니다.
ALTER TABLE project_versions
ADD COLUMN result_mp3_path VARCHAR(512);
