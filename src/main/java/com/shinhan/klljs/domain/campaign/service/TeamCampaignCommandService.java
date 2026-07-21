package com.shinhan.klljs.domain.campaign.service;

import com.shinhan.klljs.domain.campaign.dto.CampaignRenameResponse;
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
 * 팀 "캠페인 페이지"의 캠페인명 수정·삭제 API를 처리한다
 * (docs/campaign-page-api-spec.md 6-1절, 6절).
 *
 * 삭제는 하드 삭제라 상태 전이는 없지만, 같은 캠페인에 대한 동시 삭제 요청은 직렬화해야 한다 -
 * 잠금 없이 findById로 읽으면 두 요청이 같은 행을 동시에 읽어 둘 다 삭제를 시도할 수 있고,
 * 나중에 커밋하는 쪽은 이미 지워진 행에 DELETE를 실행해 Hibernate가 stale-state 예외
 * (0 rows affected)를 던진다 - findByIdForUpdate로 뒤 트랜잭션을 앞 트랜잭션의 커밋
 * 이후로 미뤄서, 뒤 트랜잭션이 다시 조회했을 때 정상적으로 CAMPAIGN_NOT_FOUND(404)를 받게 한다.
 * 수정도 같은 이유로 findByIdForUpdate를 재사용해 동시 수정/삭제 요청을 직렬화한다.
 * vision_summary_5s.campaign_id는 DB의 ON DELETE SET NULL이 알아서 처리한다.
 */
@Service
@RequiredArgsConstructor
public class TeamCampaignCommandService {

    private static final int MAX_CAMPAIGN_NAME_LENGTH = 30;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final CampaignRepository campaignRepository;

    /** OWNER/ADMIN만 캠페인명을 바꿀 수 있다 - 삭제와 동일한 권한 기준. */
    @Transactional
    public CampaignRenameResponse renameCampaign(Long userId, Long teamId, Long campaignId, String campaignName) {
        teamRepository.findById(teamId)
                .orElseThrow(() -> new GeneralException(TeamErrorCode.TEAM_NOT_FOUND));

        TeamMember requester = teamMemberRepository.findByUserIdAndTeamIdAndStatus(
                        userId, teamId, TeamMemberStatus.ACTIVE
                )
                .orElseThrow(() -> new GeneralException(TeamErrorCode.TEAM_ACCESS_DENIED));
        if (requester.getRole() == TeamMemberRole.MEMBER) {
            throw new GeneralException(TeamErrorCode.CAMPAIGN_MANAGEMENT_FORBIDDEN);
        }

        if (campaignName == null || campaignName.isBlank()) {
            throw new GeneralException(CampaignErrorCode.INVALID_CAMPAIGN_REQUEST);
        }
        String trimmedName = campaignName.trim();
        if (trimmedName.length() > MAX_CAMPAIGN_NAME_LENGTH) {
            throw new GeneralException(CampaignErrorCode.INVALID_CAMPAIGN_REQUEST);
        }

        Campaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new GeneralException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
        if (!campaign.getTeam().getId().equals(teamId)) {
            throw new GeneralException(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
        }

        campaign.rename(trimmedName);
        return CampaignRenameResponse.of(campaign);
    }

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

        Campaign campaign = campaignRepository.findByIdForUpdate(campaignId)
                .orElseThrow(() -> new GeneralException(CampaignErrorCode.CAMPAIGN_NOT_FOUND));
        if (!campaign.getTeam().getId().equals(teamId)) {
            throw new GeneralException(CampaignErrorCode.CAMPAIGN_NOT_FOUND);
        }

        campaignRepository.delete(campaign);
    }
}
