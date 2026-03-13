package com.tutti.server.domain.project.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VersionRenameRequest {

    @NotBlank(message = "버전 이름은 필수입니다.")
    @Size(min = 1, max = 50, message = "버전 이름은 1~50자 이내여야 합니다.")
    private String name;
}
