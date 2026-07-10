package com.shinhan.klljs.domain.team.dto;

import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.global.util.KstDateTimes;

import java.time.OffsetDateTime;

public record TeamMemberSummary(
        Long userId,
        String displayName,
        String email,
        TeamMemberRole role,
        boolean isMe,
        OffsetDateTime joinedAt
) {
    public static TeamMemberSummary from(TeamMember member, Long requesterId) {
        return new TeamMemberSummary(
                member.getUser().getId(),
                member.getUser().getDisplayName(),
                member.getUser().getEmail(),
                member.getRole(),
                member.getUser().getId().equals(requesterId),
                KstDateTimes.toKstOffset(member.getJoinedAt())
        );
    }
}