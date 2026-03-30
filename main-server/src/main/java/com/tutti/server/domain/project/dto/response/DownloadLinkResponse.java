package com.tutti.server.domain.project.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 파일 다운로드 응답 DTO — Signed URL 기반 다운로드 링크를 반환합니다.
 *
 * <p>
 * 기존 바이너리 스트리밍 방식에서 Supabase Signed URL 방식으로 변경되면서
 * 클라이언트가 직접 Supabase Storage에서 다운로드할 수 있는 임시 링크를 제공합니다.
 * </p>
 */
@Getter
@Builder
@AllArgsConstructor
public class DownloadLinkResponse {

    /** 시간 제한이 있는 Supabase Storage 서명 URL */
    private final String downloadLink;
}
