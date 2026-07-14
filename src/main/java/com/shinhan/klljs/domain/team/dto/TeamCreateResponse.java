package com.shinhan.klljs.domain.team.dto;

import com.shinhan.klljs.domain.team.entity.Team;

/**
 * 팀 생성 응답.
 *
 * 초대 코드는 여기 없다. 팀 생성 직후 프론트가 이어서 초대 코드 발급 API
 * ({@code POST /api/v1/teams/{teamId}/invite-code}, 백엔드 A)를 호출한다.
 *
 * 검증 상태 필드도 없다 - 진위확인·자동 판정이 MVP에서 빠지면서 승인/반려 개념 자체가 없어졌다.
 * 이 응답이 왔다는 것 자체가 팀·OWNER·사업자등록 정보가 모두 만들어졌다는 뜻이다.
 */
public record TeamCreateResponse(
        Long teamId,
        String teamName
) {

    public static TeamCreateResponse of(Team team) {
        return new TeamCreateResponse(team.getId(), team.getTeamName());
    }
}
