package com.shinhan.klljs.domain.team.controller;

import com.shinhan.klljs.domain.team.dto.TeamInviteCodeResponse;
import com.shinhan.klljs.domain.team.dto.TeamJoinRequest;
import com.shinhan.klljs.domain.team.dto.TeamJoinResponse;
import com.shinhan.klljs.domain.team.dto.TeamMemberListResponse;
import com.shinhan.klljs.domain.team.dto.TeamMemberRoleChangeRequest;
import com.shinhan.klljs.domain.team.dto.TeamMemberRoleChangeResponse;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.oauth2.jwt.Jwt;

@Tag(name = "팀원 관리", description = "팀원 목록, 초대 코드, 역할, 삭제 및 나가기 API")
public interface TeamMemberControllerDocs {

    @Operation(summary = "팀 합류", description = "유효한 7자리 초대 코드를 입력해 해당 팀에 MEMBER로 합류합니다. LEFT/REMOVED 이력이 있으면 MEMBER로 재합류합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "합류 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "유효하지 않거나 만료/폐기된 코드 (TEAM_400_001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 없음 (USER_404_001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "비활성 팀 또는 이미 소속된 팀 (TEAM_409_001/003)")
    })
    ApiResponse<TeamJoinResponse> joinTeam(
            @Parameter(hidden = true) Jwt jwt,
            TeamJoinRequest request
    );

    @Operation(summary = "팀원 목록 조회", description = "ACTIVE 팀원을 조회하고 이름/이메일로 검색한다. 팀명과 본인 여부를 함께 반환한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "keyword가 100자를 초과함"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "팀 미소속 (TEAM_403_001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "팀 없음 (TEAM_404_001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "비활성 팀 (TEAM_409_001)")
    })
    ApiResponse<TeamMemberListResponse> getMembers(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "팀 ID", example = "1") Long teamId,
            @Parameter(description = "이름 또는 이메일 검색어, 최대 100자", example = "김") String keyword
    );

    @Operation(summary = "초대 코드 발급", description = "OWNER/ADMIN이 호출한다. 기존 활성 코드를 폐기하고 1년 유효한 새 코드를 반환한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "발급 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "미소속 또는 역할 부족 (TEAM_403_001/002)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "팀 없음 (TEAM_404_001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "비활성 팀 (TEAM_409_001)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "코드 충돌 재시도 소진 (TEAM_500_001)")
    })
    ApiResponse<TeamInviteCodeResponse> issueInviteCode(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "팀 ID", example = "1") Long teamId
    );

    @Operation(summary = "팀원 역할 변경", description = "OWNER는 모든 역할을, ADMIN은 ADMIN/MEMBER 역할을 부여할 수 있다. OWNER 이전은 원자적으로 처리한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "자기 자신 또는 잘못된 role"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "미소속 또는 역할 부족"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "팀 또는 대상 멤버 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "비활성 팀")
    })
    ApiResponse<TeamMemberRoleChangeResponse> changeRole(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "팀 ID", example = "1") Long teamId,
            @Parameter(description = "대상 사용자 ID", example = "2") Long userId,
            TeamMemberRoleChangeRequest request
    );

    @Operation(summary = "팀원 삭제", description = "OWNER가 다른 ACTIVE 팀원을 REMOVED 상태로 변경한다.")
    ApiResponse<Void> removeMember(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "팀 ID", example = "1") Long teamId,
            @Parameter(description = "대상 사용자 ID", example = "2") Long userId
    );

    @Operation(summary = "팀 나가기", description = "ADMIN/MEMBER가 팀을 나간다. OWNER는 먼저 역할을 이전해야 한다.")
    ApiResponse<Void> leaveTeam(
            @Parameter(hidden = true) Jwt jwt,
            @Parameter(description = "팀 ID", example = "1") Long teamId
    );
}