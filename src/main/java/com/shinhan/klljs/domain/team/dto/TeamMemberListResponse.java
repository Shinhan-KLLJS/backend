package com.shinhan.klljs.domain.team.dto;

import java.util.List;

public record TeamMemberListResponse(
        Long teamId,
        String teamName,
        List<TeamMemberSummary> members
) {
}