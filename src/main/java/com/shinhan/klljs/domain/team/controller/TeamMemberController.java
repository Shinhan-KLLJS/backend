package com.shinhan.klljs.domain.team.controller;

import com.shinhan.klljs.domain.team.dto.TeamInviteCodeResponse;
import com.shinhan.klljs.domain.team.dto.TeamJoinRequest;
import com.shinhan.klljs.domain.team.dto.TeamJoinResponse;
import com.shinhan.klljs.domain.team.dto.TeamMemberListResponse;
import com.shinhan.klljs.domain.team.dto.TeamMemberRoleChangeRequest;
import com.shinhan.klljs.domain.team.dto.TeamMemberRoleChangeResponse;
import com.shinhan.klljs.domain.team.service.TeamInviteCodeService;
import com.shinhan.klljs.domain.team.service.TeamJoinService;
import com.shinhan.klljs.domain.team.service.TeamMemberManagementService;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import com.shinhan.klljs.global.apiPayload.code.GeneralSuccessCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TeamMemberController implements TeamMemberControllerDocs {

    private final TeamMemberManagementService teamMemberManagementService;
    private final TeamInviteCodeService teamInviteCodeService;
    private final TeamJoinService teamJoinService;

    @Override
    @PostMapping("/api/v1/teams/join")
    public ApiResponse<TeamJoinResponse> joinTeam(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TeamJoinRequest request
    ) {
        TeamJoinResponse response = teamJoinService.join(userId(jwt), request.inviteCode());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }

    @Override
    @GetMapping("/api/v1/teams/{teamId}/members")
    public ApiResponse<TeamMemberListResponse> getMembers(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long teamId,
            @RequestParam(required = false) String keyword
    ) {
        TeamMemberListResponse response = teamMemberManagementService.getMembers(
                userId(jwt), teamId, keyword);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }

    @Override
    @PostMapping("/api/v1/teams/{teamId}/invite-code")
    public ApiResponse<TeamInviteCodeResponse> issueInviteCode(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long teamId
    ) {
        TeamInviteCodeResponse response = teamInviteCodeService.issue(userId(jwt), teamId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }

    @Override
    @PatchMapping("/api/v1/teams/{teamId}/members/{userId}/role")
    public ApiResponse<TeamMemberRoleChangeResponse> changeRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long teamId,
            @PathVariable Long userId,
            @Valid @RequestBody TeamMemberRoleChangeRequest request
    ) {
        TeamMemberRoleChangeResponse response = teamMemberManagementService.changeRole(
                userId(jwt), teamId, userId, request.role());
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }

    @Override
    @DeleteMapping("/api/v1/teams/{teamId}/members/{userId}")
    public ApiResponse<Void> removeMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long teamId,
            @PathVariable Long userId
    ) {
        teamMemberManagementService.removeMember(userId(jwt), teamId, userId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK);
    }

    @Override
    @DeleteMapping("/api/v1/teams/{teamId}/members/me")
    public ApiResponse<Void> leaveTeam(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long teamId
    ) {
        teamMemberManagementService.leaveTeam(userId(jwt), teamId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK);
    }

    private Long userId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}