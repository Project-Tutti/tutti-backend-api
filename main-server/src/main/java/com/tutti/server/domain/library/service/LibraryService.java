package com.tutti.server.domain.library.service;

import com.tutti.server.domain.library.dto.response.LibraryListResponse;
import com.tutti.server.domain.project.entity.Project;
import com.tutti.server.domain.project.repository.ProjectRepository;
import com.tutti.server.global.error.BusinessException;
import com.tutti.server.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LibraryService {

    private static final Set<String> SORTABLE_FIELDS = Set.of("createdAt", "name", "updatedAt");
    private static final Set<String> SORT_DIRECTIONS = Set.of("asc", "desc");

    private final ProjectRepository projectRepository;

    // ── 4.1 보관함 목록 조회 ──

    public LibraryListResponse getLibrary(UUID userId, int page, int size,
            String keyword, String sortParam) {
        Sort sort = parseSort(sortParam);

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        Pageable pageable = PageRequest.of(safePage, safeSize, sort);

        Page<Project> projectPage;
        if (keyword != null && !keyword.isBlank()) {
            // Soft Delete 필터링 적용
            projectPage = projectRepository.findByUserIdAndDeletedAtIsNullAndNameContainingIgnoreCase(
                    userId, keyword.trim(), pageable);
        } else {
            projectPage = projectRepository.findByUserIdAndDeletedAtIsNull(userId, pageable);
        }

        return LibraryListResponse.from(projectPage);
    }

    private Sort parseSort(String sortParam) {
        if (sortParam == null || sortParam.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        String[] parts = sortParam.split(",");
        String field = parts[0].trim();
        String direction = parts.length > 1 ? parts[1].trim().toLowerCase() : "desc";

        if (!SORTABLE_FIELDS.contains(field)) {
            throw new BusinessException(ErrorCode.INVALID_SORT_PARAMETER);
        }
        if (!SORT_DIRECTIONS.contains(direction)) {
            throw new BusinessException(ErrorCode.INVALID_SORT_PARAMETER);
        }

        return Sort.by(Sort.Direction.fromString(direction), field);
    }
}
