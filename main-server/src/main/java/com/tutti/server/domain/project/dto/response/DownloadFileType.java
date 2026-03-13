package com.tutti.server.domain.project.dto.response;

/**
 * 다운로드 파일 형식 Enum
 */
public enum DownloadFileType {

    MIDI("audio/midi", ".mid"),
    XML("application/xml", ".xml"),
    PDF("application/pdf", ".pdf");

    private final String contentType;
    private final String extension;

    DownloadFileType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return extension;
    }

    public static DownloadFileType fromString(String type) {
        try {
            return DownloadFileType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
