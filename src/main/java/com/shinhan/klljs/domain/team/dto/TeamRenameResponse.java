package com.shinhan.klljs.domain.team.dto;

import com.shinhan.klljs.domain.team.entity.Team;

public record TeamRenameResponse(
        Long teamId,
        String teamName
) {

    public static TeamRenameResponse of(Team team) {
        return new TeamRenameResponse(team.getId(), team.getTeamName());
    }
}
