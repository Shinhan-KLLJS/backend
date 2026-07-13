package com.shinhan.klljs.domain.team.dto;

import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import jakarta.validation.constraints.NotNull;

public record TeamMemberRoleChangeRequest(
        @NotNull TeamMemberRole role
) {
}