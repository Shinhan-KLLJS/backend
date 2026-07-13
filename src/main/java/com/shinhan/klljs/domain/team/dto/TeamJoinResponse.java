package com.shinhan.klljs.domain.team.dto;

import com.shinhan.klljs.domain.team.entity.TeamMemberRole;

public record TeamJoinResponse(
        Long teamId,
        String teamName,
        TeamMemberRole role
) {
}