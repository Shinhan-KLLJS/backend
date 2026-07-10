package com.shinhan.klljs.domain.user.dto;

import com.shinhan.klljs.domain.user.entity.User;
import com.shinhan.klljs.domain.user.entity.UserStatus;

import java.util.List;

/**
 * hasTeam/teamId는 현재 "사용자는 팀 하나에만 속한다"는 단순화된 가정 하의 필드다 -
 * 여러 ACTIVE 팀에 속한 사용자라도(TeamMemberRepository 주석 참고, 데이터 모델 자체는 다대다를 허용한다)
 * findTeamIdsByUserIdAndStatus()가 돌려주는 목록의 첫 번째 값만 대표로 사용한다.
 * 다중 팀 소속을 프론트에 온전히 노출해야 하는 시점이 오면, 이 응답을 배열 형태로 다시 넓혀야 한다.
 */
public record UserMeResponse(
        Long id,
        String displayName,
        String email,
        String profileImageUrl,
        UserStatus status,
        boolean hasTeam,
        Long teamId
) {
    public static UserMeResponse from(User user, List<Long> teamIds) {
        boolean hasTeam = !teamIds.isEmpty();
        Long teamId = hasTeam ? teamIds.get(0) : null;

        return new UserMeResponse(
                user.getId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getStatus(),
                hasTeam,
                teamId
        );
    }
}
