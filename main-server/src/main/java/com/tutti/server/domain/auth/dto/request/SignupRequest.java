package com.tutti.server.domain.auth.dto.request;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignupRequest {

    @NotBlank(message = "이메일은 필수입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    @Size(max = 255)
    private String email;

    @NotBlank(message = "이름은 필수입니다.")
    @Size(min = 2, max = 50, message = "이름은 2~50자 이내여야 합니다.")
    @Pattern(regexp = "^[a-zA-Z가-힣0-9\\s]+$", message = "이름에 특수문자를 사용할 수 없습니다.")
    private String name;

    @NotBlank(message = "비밀번호는 필수입니다.")
    @Size(min = 8, max = 20, message = "비밀번호는 8~20자 이내여야 합니다.")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]+$", message = "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다.")
    private String password;
}
