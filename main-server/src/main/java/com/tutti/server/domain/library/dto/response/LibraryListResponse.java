package com.tutti.server.domain.library.dto.response;

import com.tutti.server.domain.project.entity.Project;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class LibraryListResponse {

    private List<LibraryProject> projects;
    private long totalElements;
    private int totalPages;
    private int currentPage;
    private boolean hasNext;

    @Getter
    @Builder
    @AllArgsConstructor
    public static class LibraryProject {
        private Long projectId;
        private String name;
        private LocalDateTime createdAt;

        public static LibraryProject from(Project project) {
            return LibraryProject.builder()
                    .projectId(project.getId())
                    .name(project.getName())
                    .createdAt(project.getCreatedAt())
                    .build();
        }
    }

    public static LibraryListResponse from(Page<Project> page) {
        List<LibraryProject> projects = page.getContent().stream()
                .map(LibraryProject::from)
                .toList();

        return LibraryListResponse.builder()
                .projects(projects)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber())
                .hasNext(page.hasNext())
                .build();
    }
}
