package com.tutti.server.domain.project.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "project_tracks", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "project_id", "track_index" })
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ProjectTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "track_index", nullable = false)
    private Integer trackIndex;

    @Column(name = "source_instrument_id", nullable = false)
    private Integer sourceInstrumentId;
}
