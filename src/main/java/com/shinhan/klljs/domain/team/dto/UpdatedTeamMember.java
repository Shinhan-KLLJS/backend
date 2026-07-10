package com.shinhan.klljs.domain.team.dto;

import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;

public record UpdatedTeamMember(
        Long userId,
        TeamMemberRole role
) {
    public static UpdatedTeamMember from(TeamMember member) {
        return new UpdatedTeamMember(member.getUser().getId(), member.getRole());
    }
}