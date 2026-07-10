package com.shinhan.klljs.domain.user.dto;

import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository.TeamMembershipSummary;
import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;

import java.util.List;

public record UserMeResponse(
        Long id,
        String displayName,
        String email,
        String profileImageUrl,
        UserStatus status,
        List<TeamMembership> teams
) {
    public static UserMeResponse from(User user, List<TeamMembershipSummary> teamMemberships) {
        List<TeamMembership> teams = teamMemberships.stream()
                .map(t -> new TeamMembership(t.teamId(), t.teamName(), t.role()))
                .toList();

        return new UserMeResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getStatus(),
                teams
        );
    }

    /**
     * 사용자가 ACTIVE로 속한 팀 하나. teams가 빈 배열이면 소속된 팀이 없다는 뜻이다 -
     * 프론트는 이 배열의 비어있음 여부로 "팀 생성/합류" 온보딩 화면을 보여줄지 판단하면 된다.
     * 팀 소속 정보는 JWT에는 넣지 않고 항상 이 API로 최신 상태를 조회한다
     * (액세스 토큰이 15분간 유효해서, 토큰에 넣으면 그 사이의 팀 생성/합류/탈퇴가
     * 최대 15분간 반영 안 된 채로 보일 수 있다 - JwtTokenService 클래스 주석 참고).
     */
    public record TeamMembership(Long teamId, String teamName, TeamMemberRole role) {
    }
}
