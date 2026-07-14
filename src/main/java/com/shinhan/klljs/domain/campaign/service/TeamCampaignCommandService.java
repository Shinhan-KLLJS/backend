package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.entity.Campaign;
import com.shinhan.klljs.domain.campaign.exception.CampaignErrorCode;
import com.shinhan.klljs.domain.campaign.repository.CampaignRepository;
import com.shinhan.klljs.domain.team.entity.TeamMember;
import com.shinhan.klljs.domain.team.entity.TeamMemberRole;
import com.shinhan.klljs.domain.team.entity.TeamMemberStatus;
import com.shinhan.klljs.domain.team.exception.TeamErrorCode;
import com.shinhan.klljs.domain.team.repository.TeamMemberRepository;
import com.shinhan.klljs.domain.team.repository.TeamRepository;
import com.shinhan.klljs.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 팀 "캠페인 페이지"의 삭제 API를 처리한다 (docs/campaign-page-api-spec.md 6절).
 *
 * 하드 삭제라 락이나 상태 전이가 필요 없다 - campaigns row를 지우면 끝이고,
 * vision_summary_5s.campaign_id는 DB의 ON DELETE SET NULL이 알아서 처리한다.
 * 같은 캠페인을 동시에 두 번 삭제 요청해도, 두 번째 요청은 findById에서 못 찾아 그냥 404가 된다.
 */
@Service
@RequiredArgsConstructor
public class TeamCampaignCommandService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CampaignRepository campaignRepository;

    @Transactional
    public void deleteCampaign(Long userId, Long teamId, Long campaignId) {
        teamRepository.findById(teamId)
                .orElseThrow(() -> new GeneralException(TeamErrorCode.TEAM_NOT_FOUND));

        TeamMember requester = teamMemberRepository.findByUserIdAndTeamIdAndStatus(
                        userId, teamId, TeamMemberStatus.ACTIVE
                )
                .orElseThrow(() -> new GeneralException(TeamErrorCode.TEAM_ACCESS_DENIED));
        if (requester.getRole() == TeamMemberRole.MEMBER) {
            throw new GeneralException(TeamErrorCode.CAMPAIGN_MANAGEMENT_FORBIDDEN);
        }

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new GeneralException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
        if (!campaign.getTeam().getId().equals(teamId)) {
            throw new GeneralException(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
        }

        campaignRepository.delete(campaign);
    }
}
