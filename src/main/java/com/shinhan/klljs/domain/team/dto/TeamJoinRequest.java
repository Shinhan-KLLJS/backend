package com.shinhan.klljs.domain.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamJoinRequest(
        @NotBlank(message = "초대 코드는 필수입니다.")
        @Size(max = 100, message = "초대 코드는 100자 이하여야 합니다.")
        String inviteCode
) {
}