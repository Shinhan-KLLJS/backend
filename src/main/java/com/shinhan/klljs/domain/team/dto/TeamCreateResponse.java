package com.shinhan.klljs.domain.team.dto;

import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.VerificationStatus;

/**
 * 팀 생성 응답.
 *
 * 초대 코드는 여기 없다. 팀 생성 직후 프론트가 이어서 초대 코드 발급 API
 * ({@code POST /api/v1/teams/{teamId}/invite-code}, 백엔드 A)를 호출한다.
 *
 * @param verificationStatus 항상 {@code APPROVED}다. 검증을 통과한 경우에만 팀이 만들어지고,
 *                           그 외에는 400으로 막혀 이 응답 자체에 도달하지 않는다
 */
public record TeamCreateResponse(
        Long teamId,
        String teamName,
        VerificationStatus verificationStatus
) {

    public static TeamCreateResponse of(Team team) {
        return new TeamCreateResponse(team.getId(), team.getTeamName(), VerificationStatus.APPROVED);
    }
}
