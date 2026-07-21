package com.shinhan.klljs.domain.team.controller;

import com.shinhan.klljs.domain.team.dto.TeamCreateRequest;
import com.shinhan.klljs.domain.team.dto.TeamCreateResponse;
import com.shinhan.klljs.domain.team.dto.TeamRenameRequest;
import com.shinhan.klljs.domain.team.dto.TeamRenameResponse;
import com.shinhan.klljs.domain.team.service.TeamCreateService;
import com.shinhan.klljs.domain.team.service.TeamMemberManagementService;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import com.shinhan.klljs.global.apiPayload.code.GeneralSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팀 생성·수정 API (docs/team-creation-api-spec.md 5절).
 *
 * 팀 생성은 아직 팀이 없는 사용자가 호출하므로 "인증된 사용자인가"만 확인한다 - 팀 소속 검사가 없다.
 */
@RestController
@RequiredArgsConstructor
public class TeamController implements TeamControllerDocs {

    private final TeamCreateService teamCreateService;
    private final TeamMemberManagementService teamMemberManagementService;

    @Override
    @PostMapping("/api/v1/teams")
    public ApiResponse<TeamCreateResponse> createTeam(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TeamCreateRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, teamCreateService.create(userId, request));
    }

    @Override
    @PatchMapping("/api/v1/teams/{teamId}")
    public ApiResponse<TeamRenameResponse> renameTeam(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long teamId,
            @Valid @RequestBody TeamRenameRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        TeamRenameResponse response = teamMemberManagementService.renameTeam(userId, teamId, request.teamName());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}
