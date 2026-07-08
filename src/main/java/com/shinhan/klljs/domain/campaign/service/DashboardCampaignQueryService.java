package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.dto.DashboardCampaignDetailResponse;
import com.shinhan.klljs.domain.campaign.dto.DashboardCampaignListResponse;
import com.shinhan.klljs.domain.campaign.dto.DashboardCampaignSummary;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.exception.CampaignErrorCode;
import com.shinhan.klljs.domain.campaign.repository.CampaignRepository;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver;
import com.shinhan.klljs.domain.campaign.util.CampaignPeriodResolver.CampaignPeriodContext;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 캠페인 status는 등록 처리 결과로 저장된 값을 그대로 신뢰한다 (날짜로부터 재계산하지 않는다).
 * REGISTERED -> BEFORE_EXECUTION/IN_EXECUTION/AFTER_EXECUTION 전환 방식은 아직 정해지지 않았고,
 * 이 부분은 캠페인 등록 플로우를 만들 때 별도로 다뤄야 한다.
 */
@Service
@RequiredArgsConstructor
public class DashboardCampaignQueryService {

    private final CampaignRepository campaignRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Transactional(readOnly = true)
    public DashboardCampaignListResponse getCampaigns(Long userId, String keyword, CampaignStatus status) {
        List<Long> teamIds = teamMemberRepository.findTeamIdsByUserIdAndStatus(userId, TeamMemberStatus.ACTIVE);
        if (teamIds.isEmpty()) {
            return DashboardCampaignListResponse.from(List.of());
        }

        List<Campaign> campaigns = campaignRepository.findByTeamIdIn(teamIds);
        Long defaultCampaignId = resolveDefaultCampaign(campaigns).map(Campaign::getId).orElse(null);

        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim().toLowerCase();

        List<DashboardCampaignSummary> result = campaigns.stream()
                .filter(c -> normalizedKeyword == null || c.getCampaignName().toLowerCase().contains(normalizedKeyword))
                .filter(c -> status == null || c.getStatus() == status)
                .sorted(Comparator.comparing(Campaign::getCreatedAt).reversed())
                .map(c -> DashboardCampaignSummary.from(c, c.getId().equals(defaultCampaignId)))
                .toList();

        return DashboardCampaignListResponse.from(result);
    }

    @Transactional(readOnly = true)
    public DashboardCampaignDetailResponse getCampaignDetail(
            Long userId, Long campaignId, LocalDate selectedStartDate, LocalDate selectedEndDate
    ) {
        Campaign campaign = getAccessibleCampaign(userId, campaignId);
        CampaignPeriodContext periodContext = CampaignPeriodResolver.resolve(campaign, selectedStartDate, selectedEndDate);
        return DashboardCampaignDetailResponse.from(campaign, periodContext);
    }

    /**
     * campaign_id를 받는 모든 대시보드 API가 공통으로 쓰는 접근 권한 체크(0절 "접근 권한").
     * 캠페인이 없으면 404, 요청한 사용자가 그 캠페인의 team에 ACTIVE로 속해있지 않으면 403.
     */
    public Campaign getAccessibleCampaign(Long userId, Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new GeneralException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));

        boolean hasAccess = teamMemberRepository.existsByUserIdAndTeamIdAndStatus(
                userId, campaign.getTeam().getId(), TeamMemberStatus.ACTIVE);
        if (!hasAccess) {
            throw new GeneralException(CampaignErrorCode.CAMPAIGN_ACCESS_DENIED);
        }

        return campaign;
    }

    /**
     * 2절 "기본 선택 규칙" 우선순위. 각 우선순위 안에 여러 건이 있을 때 고르는 기준:
     * - IN_EXECUTION: 가장 최근에 생성된 캠페인 (명세에 명시 안 됨, 동률 시 임의 기준)
     * - BEFORE_EXECUTION: 오늘 이후 가장 먼저 시작하는 캠페인 (명세에 명시)
     * - REGISTERED: 가장 최근 등록된 캠페인 (명세에 명시)
     * - AFTER_EXECUTION: 가장 최근 종료된 캠페인 (명세에 명시)
     * - 그 외(전부 REGISTRATION_FAILED 등): 가장 최근 생성된 캠페인
     */
    private Optional<Campaign> resolveDefaultCampaign(List<Campaign> campaigns) {
        return byStatus(campaigns, CampaignStatus.IN_EXECUTION).max(Comparator.comparing(Campaign::getCreatedAt))
                .or(() -> byStatus(campaigns, CampaignStatus.BEFORE_EXECUTION).min(Comparator.comparing(Campaign::getExecutionStartDate)))
                .or(() -> byStatus(campaigns, CampaignStatus.REGISTERED).max(Comparator.comparing(Campaign::getCreatedAt)))
                .or(() -> byStatus(campaigns, CampaignStatus.AFTER_EXECUTION).max(Comparator.comparing(Campaign::getExecutionEndDate)))
                .or(() -> campaigns.stream().max(Comparator.comparing(Campaign::getCreatedAt)));
    }

    private Stream<Campaign> byStatus(List<Campaign> campaigns, CampaignStatus status) {
        return campaigns.stream().filter(c -> c.getStatus() == status);
    }
}
