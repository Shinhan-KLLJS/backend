package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.dto.TeamCampaignListResponse;
import com.shinhan.klljs.domain.campaign.dto.TeamCampaignSort;
import com.shinhan.klljs.domain.campaign.dto.TeamCampaignStatusFilter;
import com.shinhan.klljs.domain.campaign.dto.TeamCampaignSummary;
import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.entity.CampaignStatus;
import com.shinhan.klljs.domain.campaign.repository.CampaignRepository;
import com.shinhan.klljs.domain.campaign.util.CampaignPlayCountEstimator;
import com.shinhan.klljs.domain.team.entity.Team;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.exception.TeamErrorCode;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import com.shinhan.klljs.global.util.KstDateTimes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 팀 "캠페인 페이지"의 목록 조회 API를 처리한다 (docs/campaign-page-api-spec.md 4절).
 *
 * keyword/status 필터링과 정렬은 DB가 아니라 이 계층에서 처리한다 - 팀당 캠페인 개수가
 * 많지 않을 걸로 예상되어(DashboardCampaignQueryService와 동일한 전제), 필터 조합별로 쿼리를
 * 여러 개 만드는 것보다 일단 다 가져와서 메모리에서 거르는 편이 코드가 단순하다.
 */
@Service
@RequiredArgsConstructor
public class TeamCampaignQueryService {

    private final CampaignRepository campaignRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TeamCampaignListResponse getCampaigns(
            Long userId, Long teamId, TeamCampaignStatusFilter status, String keyword, TeamCampaignSort sort
    ) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new GeneralException(TeamErrorCode.TEAM_NOT_FOUND));

        boolean isMember = teamMemberRepository.existsByUserIdAndTeamIdAndStatus(
                userId, teamId, TeamMemberStatus.ACTIVE);
        if (!isMember) {
            throw new GeneralException(TeamErrorCode.TEAM_ACCESS_DENIED);
        }

        List<Campaign> campaigns = campaignRepository.findByTeamIdWithMediaUnit(teamId);

        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim().toLowerCase();
        Set<CampaignStatus> statusFilter = toCampaignStatuses(status);

        LocalDateTime nowKst = KstDateTimes.toKst(LocalDateTime.now(clock));
        LocalDate today = nowKst.toLocalDate();

        List<TeamCampaignSummary> result = campaigns.stream()
                .filter(c -> normalizedKeyword == null || c.getCampaignName().toLowerCase().contains(normalizedKeyword))
                .filter(c -> statusFilter == null || statusFilter.contains(c.getStatus()))
                .sorted(comparatorFor(sort))
                .map(c -> TeamCampaignSummary.from(c, estimateTodayPlayCount(c, today, nowKst)))
                .toList();

        return TeamCampaignListResponse.from(team.getTeamName(), result);
    }

    /** 스펙 2-3절 매핑: BEFORE_EXECUTION은 REGISTERED도 함께 포함한다. null(전체)이면 필터링하지 않는다. */
    private Set<CampaignStatus> toCampaignStatuses(TeamCampaignStatusFilter status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case BEFORE_EXECUTION -> EnumSet.of(CampaignStatus.REGISTERED, CampaignStatus.BEFORE_EXECUTION);
            case IN_EXECUTION -> EnumSet.of(CampaignStatus.IN_EXECUTION);
            case AFTER_EXECUTION -> EnumSet.of(CampaignStatus.AFTER_EXECUTION);
        };
    }

    private Comparator<Campaign> comparatorFor(TeamCampaignSort sort) {
        TeamCampaignSort effectiveSort = sort == null ? TeamCampaignSort.NAME : sort;
        return switch (effectiveSort) {
            case NAME -> Comparator.comparing(Campaign::getCampaignName);
            case EXECUTION_RECENT -> Comparator.comparing(Campaign::getExecutionStartDate).reversed();
            case EXECUTION_OLDEST -> Comparator.comparing(Campaign::getExecutionStartDate);
        };
    }

    private int estimateTodayPlayCount(Campaign campaign, LocalDate today, LocalDateTime nowKst) {
        return CampaignPlayCountEstimator.estimateTodayPlayCount(
                campaign.getExecutionStartDate(),
                campaign.getExecutionEndDate(),
                campaign.getDailyTargetPlayCount(),
                today,
                nowKst
        );
    }
}
