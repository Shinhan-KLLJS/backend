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
 * 홈 대시보드의 "캠페인 목록 조회" / "캠페인 상세정보 조회" API를 처리하는 서비스.
 *
 * 캠페인 status는 DB에 저장된 값을 그대로 신뢰하고 응답한다 (오늘 날짜와 비교해서 재계산하지 않는다).
 * REGISTERED -> BEFORE_EXECUTION/IN_EXECUTION/AFTER_EXECUTION로 전환되는 로직(스케줄러인지,
 * 등록 완료 시점에 바로 결정되는지 등)은 아직 이 코드베이스 어디에도 없다 - 캠페인 등록 플로우를
 * 만들 때 그 전환 로직도 같이 설계해야 한다. 지금은 "이미 올바른 status로 저장돼 있다"고 가정한다.
 */
@Service
@RequiredArgsConstructor
public class DashboardCampaignQueryService {

    private final CampaignRepository campaignRepository;
    private final TeamMemberRepository teamMemberRepository;

    /**
     * 캠페인 목록 조회. 흐름:
     * 1. 이 사용자가 ACTIVE로 속한 팀 ID들을 구한다 (한 명이 여러 팀에 속할 수 있음)
     * 2. 그 팀들이 가진 캠페인을 전부 가져온다 (keyword/status 필터 적용 전 전체 목록)
     * 3. 필터 적용 전 전체 목록을 기준으로 "기본 선택 캠페인"을 하나 정한다
     *    (필터링된 결과만 보고 기본 선택을 정하면, 필터 때문에 원래 기본 선택 캠페인이
     *    빠져있는데도 다른 캠페인이 엉뚱하게 기본 선택으로 잡히는 문제가 생길 수 있어서다)
     * 4. keyword(캠페인명 포함 검색, 대소문자 무시) / status 필터를 적용하고 최신순으로 정렬해서 응답
     */
    @Transactional(readOnly = true)
    public DashboardCampaignListResponse getCampaigns(Long userId, String keyword, CampaignStatus status) {
        List<Long> teamIds = teamMemberRepository.findTeamIdsByUserIdAndStatus(userId, TeamMemberStatus.ACTIVE);
        if (teamIds.isEmpty()) {
            // 어느 팀에도 속해있지 않으면 볼 수 있는 캠페인도 없다 (빈 목록, 에러 아님)
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

    /**
     * 캠페인 상세정보 조회. 접근 권한 체크 -> 선택 기간 계산 -> 응답 조립 순서로 진행한다.
     */
    @Transactional(readOnly = true)
    public DashboardCampaignDetailResponse getCampaignDetail(
            Long userId, Long campaignId, LocalDate selectedStartDate, LocalDate selectedEndDate
    ) {
        Campaign campaign = getAccessibleCampaign(userId, campaignId);
        CampaignPeriodContext periodContext = CampaignPeriodResolver.resolve(campaign, selectedStartDate, selectedEndDate);
        return DashboardCampaignDetailResponse.from(campaign, periodContext);
    }

    /**
     * campaign_id 하나를 다루는 모든 대시보드 API가 공통으로 쓰는 접근 권한 체크(스펙 0절 "접근 권한").
     * public으로 열어둔 이유: 앞으로 만들 송출정보/실시간그래프/깔대기 등 나머지 API들도
     * 전부 이 메서드로 "이 캠페인, 이 사용자가 봐도 되나?"부터 확인하고 시작해야 하기 때문이다.
     *
     * @throws GeneralException 캠페인이 없으면 CAMPAIGN_NOT_FOUND(404),
     *         있어도 이 사용자의 팀 소속이 아니면(다른 팀 캠페인이거나, LEFT/REMOVED 상태면) CAMPAIGN_ACCESS_DENIED(403)
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
     * 스펙 2절 "기본 선택 규칙" 우선순위 그대로 구현.
     * 우선순위가 높은 상태부터 순서대로 확인하고, 그 상태의 캠페인이 하나도 없으면 다음 순위로 넘어간다(.or 체이닝).
     * 각 우선순위 안에서 여러 건이 있을 때 무엇을 고를지:
     * - IN_EXECUTION: 스펙에 명시 안 됨 -> 가장 최근에 생성된 캠페인으로 임의 선택
     * - BEFORE_EXECUTION: "오늘 이후 가장 먼저 시작하는 캠페인" (스펙에 명시) -> executionStartDate가 가장 이른 것
     * - REGISTERED: "가장 최근 등록완료된 캠페인" (스펙에 명시) -> createdAt이 가장 늦은(최신) 것
     * - AFTER_EXECUTION: "가장 최근 종료된 캠페인" (스펙에 명시) -> executionEndDate가 가장 늦은 것
     * - 위 4개 상태가 하나도 없으면(전부 REGISTRATION_FAILED인 경우 등): 가장 최근 생성된 캠페인
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
