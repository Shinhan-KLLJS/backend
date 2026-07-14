package com.shinhan.klljs.domain.campaign.controller;

import com.shinhan.klljs.domain.campaign.dto.TeamCampaignListResponse;
import com.shinhan.klljs.domain.campaign.dto.TeamCampaignSort;
import com.shinhan.klljs.domain.campaign.dto.TeamCampaignStatusFilter;
import com.shinhan.klljs.domain.campaign.service.TeamCampaignQueryService;
import com.shinhan.klljs.global.apiPayload.ApiResponse;
import com.shinhan.klljs.global.apiPayload.code.GeneralSuccessCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 팀 "캠페인 페이지" API (docs/campaign-page-api-spec.md). Swagger 설명은
 * TeamCampaignControllerDocs에 몰아뒀다.
 */
@RestController
@RequiredArgsConstructor
public class TeamCampaignController implements TeamCampaignControllerDocs {

    private final TeamCampaignQueryService teamCampaignQueryService;

    @Override
    @GetMapping("/api/v1/teams/{teamId}/campaigns")
    public ApiResponse<TeamCampaignListResponse> getCampaigns(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long teamId,
            @RequestParam(required = false) TeamCampaignStatusFilter status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "NAME") TeamCampaignSort sort
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        TeamCampaignListResponse response =
                teamCampaignQueryService.getCampaigns(userId, teamId, status, keyword, sort);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, response);
    }
}
