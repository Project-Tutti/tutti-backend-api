package com.tutti.server.domain.project.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "version_mappings", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "version_id", "track_index" })
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class VersionMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_id", nullable = false)
    private ProjectVersion version;

    @Column(name = "track_index", nullable = false)
    private Integer trackIndex;

    @Column(name = "target_instrument_id", nullable = false)
    private Integer targetInstrumentId;
}
