package com.shinhan.klljs.domain.team.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TeamRenameRequest(
        @NotBlank(message = "팀명을 입력해 주세요.")
        @Size(max = 200, message = "팀명은 200자를 넘을 수 없습니다.")
        String teamName
) {
}
