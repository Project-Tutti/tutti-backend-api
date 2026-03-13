package com.tutti.server.domain.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProjectRenameRequest {

    @NotBlank(message = "프로젝트 이름은 필수입니다.")
    @Size(min = 1, max = 100, message = "프로젝트 이름은 1~100자 이내여야 합니다.")
    private String name;
}
