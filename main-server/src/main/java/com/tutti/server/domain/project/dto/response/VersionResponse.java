package com.tutti.server.domain.project.dto.response;

import com.tutti.server.domain.project.dto.request.MappingItem;
import com.tutti.server.domain.project.entity.ProjectVersion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class VersionResponse {

    private Long versionId;
    private String name;
    private String status;
    private List<MappingItem> mappings;
    private Integer instrumentId;
    private Integer minNote;
    private Integer maxNote;
    private LocalDateTime createdAt;

    public static VersionResponse from(ProjectVersion version) {
        List<MappingItem> mappingItems = version.getMappings().stream()
                .map(m -> new MappingItem(m.getTrackIndex(), m.getTargetInstrumentId()))
                .toList();

        return VersionResponse.builder()
                .versionId(version.getId())
                .name(version.getName())
                .status(version.getStatus().name().toLowerCase())
                .mappings(mappingItems)
                .instrumentId(version.getInstrumentId())
                .minNote(version.getMinNote())
                .maxNote(version.getMaxNote())
                .createdAt(version.getCreatedAt())
                .build();
    }
}
