package com.tutti.server.domain.project.dto.response;

import com.tutti.server.domain.project.entity.ProjectTrack;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class TrackInfoResponse {

    private List<TrackItem> tracks;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class TrackItem {
        private Integer trackIndex;
        private Integer sourceInstrumentId;

        public static TrackItem from(ProjectTrack track) {
            return TrackItem.builder()
                    .trackIndex(track.getTrackIndex())
                    .sourceInstrumentId(track.getSourceInstrumentId())
                    .build();
        }
    }
}
