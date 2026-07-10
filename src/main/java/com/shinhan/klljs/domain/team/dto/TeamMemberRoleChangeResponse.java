package com.shinhan.klljs.domain.team.dto;

import java.util.List;

public record TeamMemberRoleChangeResponse(
        List<UpdatedTeamMember> updatedMembers
) {
    public static TeamMemberRoleChangeResponse of(UpdatedTeamMember... members) {
        return new TeamMemberRoleChangeResponse(List.of(members));
    }
}